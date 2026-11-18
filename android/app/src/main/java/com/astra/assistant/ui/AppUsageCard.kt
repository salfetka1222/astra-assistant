package com.astra.assistant.ui

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.astra.assistant.core.AppCatalogSnapshot
import com.astra.assistant.core.AppVisit
import com.astra.assistant.core.DeviceAppUsage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
internal fun AppUsageCard(catalog: AppCatalogSnapshot?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf<Job?>(null) }
    var visits by remember { mutableStateOf<List<AppVisit>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun clear() {
        request?.cancel()
        request = null
        visits = null
        message = null
    }

    LifecycleResumeEffect(Unit) {
        access = DeviceAppUsage.hasAccess(context)
        onPauseOrDispose { clear() }
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Использование приложений", style = MaterialTheme.typography.titleLarge)
            Text("За последние 24 часа: до 10 приложений из каталога по числу переходов на экран. " +
                "Повторные переходы внутри одного приложения считаются одним посещением. " +
                "Сводка формируется по кнопке и стирается при выходе из раздела или сворачивании Astra.")
            Text("Доступ к статистике Android: " + if (access) "включён" else "выключен")
            TextButton(onClick = {
                clear()
                try {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (_: android.content.ActivityNotFoundException) {
                    message = "В настройках Android найди специальный доступ → Доступ к статистике использования."
                } catch (_: SecurityException) {
                    message = "Android запретил открыть настройки доступа."
                }
            }) { Text(if (access) "Управлять доступом" else "Настроить доступ в Android") }

            if (catalog?.apps.isNullOrEmpty()) Text("Сначала создай каталог кнопкой «Изучить приложения».")
            OutlinedButton(enabled = access && !catalog?.apps.isNullOrEmpty() && request == null, onClick = {
                val apps = catalog?.apps ?: return@OutlinedButton
                visits = null
                message = null
                request = scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { DeviceAppUsage.read(context.applicationContext, apps) }
                        access = DeviceAppUsage.hasAccess(context)
                        if (result == null || !access) {
                            message = "Статистика недоступна. Проверь системный доступ и повтори запрос."
                        } else {
                            visits = result
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: RuntimeException) {
                        access = DeviceAppUsage.hasAccess(context)
                        message = "Android не вернул статистику. Попробуй ещё раз."
                    } finally {
                        // A cancelled request may finish after another one has started.
                        if (request == coroutineContext[Job]) request = null
                    }
                }
            }) { Text(if (request == null) "Показать сводку" else "Подсчёт…") }
            message?.let { Text(it) }
            visits?.let { rows ->
                if (rows.isEmpty()) Text("За этот период Android не сообщил о посещениях приложений из каталога.")
                val timeFormat = DateFormat.getTimeFormat(context)
                val dayFormat = DateFormat.getDateFormat(context)
                rows.forEach { row ->
                    val last = Date(row.lastSeen)
                    Text("${row.app.label} · посещений: ${row.count}\nПоследняя активность: ${dayFormat.format(last)} ${timeFormat.format(last)}")
                }
                TextButton(onClick = { clear() }) { Text("Очистить сводку") }
            }
        }
    }
}
