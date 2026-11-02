package com.astra.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.astra.assistant.update.AstraRelease
import com.astra.assistant.update.ReadyUpdate
import com.astra.assistant.update.UpdateClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val client = remember(context) { UpdateClient(context) }
    val scope = rememberCoroutineScope()
    val info = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    var busy by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<AstraRelease?>(null) }
    var ready by remember { mutableStateOf<ReadyUpdate?>(null) }
    var status by remember { mutableStateOf("Проверь, доступна ли новая версия Astra.") }

    fun task(action: suspend () -> Unit) {
        busy = true
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = e.message ?: "Не удалось выполнить действие. Повтори попытку." }
            finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text("← Назад") }
        Text("Обновления Astra", style = MaterialTheme.typography.headlineSmall)
        Text("Версия ${info.versionName} (код ${PackageInfoCompat.getLongVersionCode(info)})")
        Text("Astra проверит файл перед установкой. Установку подтверждаешь ты в окне Android.")
        Button(enabled = !busy, onClick = {
            task {
                ready?.file?.delete()
                ready = null
                release = null
                status = "Проверяем обновления…"
                release = withContext(Dispatchers.IO) { client.latest() }
                status = if (release == null) "Новых обновлений в настроенном канале пока нет."
                    else "Найдено обновление (код ${release!!.code}). Нажми «Скачать обновление», чтобы проверить APK."
            }
        }) { Text("Проверить обновления") }
        val available = release
        if (available != null && ready == null) {
            Button(enabled = !busy, onClick = {
                task {
                    status = "Скачиваем и проверяем APK… Оставь этот экран открытым."
                    ready = withContext(Dispatchers.IO) { client.download(available) }
                    status = "APK проверен: контрольная сумма, версия и подпись совпадают. Нажми «Установить»."
                }
            }) { Text("Скачать обновление") }
        }
        val downloaded = ready
        if (downloaded != null) {
            Button(enabled = !busy, onClick = {
                task {
                    if (!context.packageManager.canRequestPackageInstalls()) {
                        status = "Разреши установку из Astra в настройках Android, вернись и снова нажми «Установить»."
                        client.requestInstallPermission()
                    } else {
                        status = "Повторно проверяем файл…"
                        withContext(Dispatchers.IO) { client.verify(downloaded) }
                        client.install(downloaded)
                        status = "Подтверди установку в окне Android. Если отменил — можно повторить."
                    }
                }
            }) { Text("Установить") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(status)
    }
}
