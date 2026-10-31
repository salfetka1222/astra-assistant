package com.astra.assistant.ui

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val RELEASE_API = "https://api.github.com/repos/salfetka1222/astra-assistant/releases/latest"

@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val installedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Нажми «Проверить», чтобы узнать о новой версии.") }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Обновления Astra", style = MaterialTheme.typography.headlineSmall)
        Text("Установлена версия ${packageInfo.versionName ?: "неизвестна"} (код $installedCode)")
        Text(
            "Пока доступна только проверка версии. Установка из приложения будет включена после настройки постоянной подписи APK.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    status = withContext(Dispatchers.IO) { checkLatestRelease(installedCode) }
                    busy = false
                }
            }
        ) { Text(if (busy) "Проверяем…" else "Проверить обновления") }
        Text(status, modifier = Modifier.fillMaxWidth())
    }
}

private fun checkLatestRelease(installedCode: Long): String {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Astra-Assistant-Updater")
        }
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            return "Стабильный релиз Astra пока недоступен. Попробуй проверить обновления позже."
        }
        if (responseCode != HttpURLConnection.HTTP_OK) {
            return "Не удалось получить сведения о релизе (HTTP $responseCode)."
        }
        val data = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        val tag = data.optString("tag_name")
        val code = Regex("^astra-v(\\d+)$").matchEntire(tag)?.groupValues?.get(1)?.toLongOrNull()
        if (code == null) {
            "Пока нет совместимого релиза Astra. Текущий релиз GitHub не содержит проверяемого кода версии."
        } else if (code > installedCode) {
            "Найдена версия с кодом $code. Установка временно недоступна до настройки безопасной подписи APK."
        } else {
            "Установленная версия актуальна по данным опубликованного релиза (код $code)."
        }
    } catch (_: Exception) {
        "Не удалось проверить обновления. Проверь подключение к интернету и повтори попытку."
    } finally {
        connection?.disconnect()
    }
}
