package com.astra.assistant.ui

import android.content.Context
import android.content.ComponentName
import android.provider.Settings
import android.content.Intent
import android.media.AudioManager
import android.app.ActivityManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import com.astra.assistant.AstraAccessibilityService
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private fun isAstraAccessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getInt(
        context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0
    ) == 1
    if (!enabled) return false
    val configured = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    val target = ComponentName(context, AstraAccessibilityService::class.java)
    return configured.split(':').any { ComponentName.unflattenFromString(it) == target }
}

private const val BRIDGE_PREFS = "astra_bridge_preferences"
private const val BRIDGE_TOKEN_KEY = "bridge_token"
private const val BRIDGE_BASE_URL = "http://127.0.0.1:8765"

@Composable
fun BridgeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(BRIDGE_PREFS, Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(prefs.getString(BRIDGE_TOKEN_KEY, "") ?: "") }
    var filePath by remember { mutableStateOf("") }
    var memoryQuery by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("Мост пока не проверен.") }
    var busy by remember { mutableStateOf(false) }
    var actionHistory by remember { mutableStateOf(listOf<String>()) }
    fun recordAction(label: String, success: Boolean) {
        actionHistory = (listOf("${if (success) "✓" else "✕"} $label") + actionHistory).take(12)
        result = if (success) "Команда «$label» отправлена Android." else "Не удалось выполнить «$label»."
    }

    val scope = rememberCoroutineScope()
    var accessibilityEnabled by remember { mutableStateOf(isAstraAccessibilityEnabled(context)) }
    val accessibilityPrefs = remember(context) {
        context.getSharedPreferences(AstraAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var lastPackage by remember { mutableStateOf("") }
    var lastEventAt by remember { mutableStateOf(0L) }
    fun refreshAccessibility() {
        accessibilityEnabled = isAstraAccessibilityEnabled(context)
        lastPackage = accessibilityPrefs.getString(
            AstraAccessibilityService.ACCESSIBILITY_CURRENT_PACKAGE, ""
        ).orEmpty()
        lastEventAt = accessibilityPrefs.getLong(
            AstraAccessibilityService.ACCESSIBILITY_LAST_EVENT_AT, 0L
        )
    }
    LaunchedEffect(Unit) { refreshAccessibility() }


    fun runRequest(path: String, body: JSONObject? = null) {
        if (token.trim().length < 32) {
            result = "Вставь токен из Termux (не менее 32 символов)."
            return
        }
        busy = true
        prefs.edit().putString(BRIDGE_TOKEN_KEY, token.trim()).apply()
        scope.launch {
            result = withContext(Dispatchers.IO) {
                try {
                    val connection = (URL(BRIDGE_BASE_URL + path).openConnection() as HttpURLConnection).apply {
                        requestMethod = if (body == null) "GET" else "POST"
                        connectTimeout = 2500
                        readTimeout = 4000
                        setRequestProperty("Authorization", "Bearer " + token.trim())
                        setRequestProperty("Accept", "application/json")
                        if (body != null) {
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        }
                    }
                    try {
                        if (body != null) {
                            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                        }
                        val status = connection.responseCode
                        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                        if (status == 200 && path == "/v1/health") {
                            val health = JSONObject(text)
                            val uptime = health.optLong("uptime_seconds", 0)
                            val tools = health.optJSONArray("tools")
                            val available = (0 until (tools?.length() ?: 0)).joinToString(", ") { tools?.optString(it).orEmpty() }
                            "Astra Bridge: ${health.optString("status", "неизвестно")}\n" +
                                "Соединение: ${health.optString("transport", "неизвестно")}\n" +
                                "Время работы: ${uptime} сек.\n" +
                                "Разрешённые инструменты: $available"
                        } else {
                            "HTTP $status\n$text"
                        }
                    } finally {
                        connection.disconnect()
                    }
                } catch (error: Exception) {
                    Log.w("AstraBridge", "Bridge request failed: ${error.javaClass.simpleName}")
                    "Нет соединения. Проверь, что в Termux запущен python bridge.py, порт 8765 доступен и токен правильный.\nПричина: ${error.javaClass.simpleName}"
                }
            }
            busy = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text("Мост Termux", style = MaterialTheme.typography.headlineSmall)
        }
        Text("Управление Android", style = MaterialTheme.typography.titleMedium)
        Text("Действия выполняются только по нажатию кнопки. Astra не читает текст других приложений.")
        val actions = listOf(
            "back" to "Назад", "home" to "Домой", "recents" to "Недавние",
            "notifications" to "Уведомления", "quick_settings" to "Быстрые настройки",
            "scroll_up" to "Прокрутить вверх", "scroll_down" to "Прокрутить вниз"
        )
        actions.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (action, label) ->
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = accessibilityEnabled,
                        onClick = {
                            AstraAccessibilityService.performManualAction(action) { success ->
                                recordAction(label, success)
                            }
                        }
                    ) { Text(label) }
                }
            }
        }
        Text("Быстрые инструменты", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                recordAction("Громкость +", true)
            }) { Text("Громкость +") }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                recordAction("Громкость −", true)
            }) { Text("Громкость −") }
        }
        Text("Диагностика устройства", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = {
            try {
                val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val batteryText = if (percent in 0..100) "$percent%" else "неизвестно"
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memory = ActivityManager.MemoryInfo()
                manager.getMemoryInfo(memory)
                val mib = 1024L * 1024L
                val storage = StatFs(Environment.getDataDirectory().absolutePath)
                result = "Батарея: $batteryText\n" +
                    "Свободная ОЗУ: ${memory.availMem / mib} МиБ\n" +
                    "Свободное хранилище: ${storage.availableBytes / mib} МиБ"
                actionHistory = (listOf("✓ Диагностика устройства") + actionHistory).take(12)
            } catch (error: Exception) {
                Log.w("AstraBridge", "Device diagnostics failed: ${error.javaClass.simpleName}")
                recordAction("Диагностика устройства", false)
            }
        }) { Text("Батарея, ОЗУ и память") }
        Text("Быстрый запуск разрешённых приложений", style = MaterialTheme.typography.titleMedium)
        val launchOptions = listOf(
            "Настройки Android" to Intent(Settings.ACTION_SETTINGS),
            "Настройки Wi-Fi" to Intent(Settings.ACTION_WIFI_SETTINGS),
            "Экран и яркость" to Intent(Settings.ACTION_DISPLAY_SETTINGS),
            "Настройки звука" to Intent(Settings.ACTION_SOUND_SETTINGS),
            "Приложения" to Intent(Settings.ACTION_APPLICATION_SETTINGS),
            "Браузер" to Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))
        )
        launchOptions.forEach { (label, intent) ->
            OutlinedButton(onClick = {
                val success = try {
                    context.startActivity(intent)
                    true
                } catch (_: Exception) { false }
                recordAction("Открыть $label", success)
            }) { Text("Открыть: $label") }
        }
        if (actionHistory.isNotEmpty()) {
            Text("История действий (только в текущем сеансе)", style = MaterialTheme.typography.titleMedium)
            actionHistory.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { actionHistory = emptyList() }) { Text("Очистить историю") }
        }
        Text("Специальные возможности Android", style = MaterialTheme.typography.titleMedium)
        Text(if (accessibilityEnabled) "Astra Accessibility: включена" else "Astra Accessibility: выключена")
        Text("Служба поддерживает ручную навигацию и прокрутку. Чтение содержимого чужих приложений и автоматический ввод отключены.")
        if (accessibilityEnabled && lastPackage.isNotBlank()) {
            Text("Последнее активное приложение: $lastPackage")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refreshAccessibility() }) { Text("Обновить статус") }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) { Text("Настройки") }
        }
        Text(
            "Соединение только с Termux на этом телефоне через 127.0.0.1:8765. Токен хранится в приватных настройках приложения. Не отправляй его другим людям.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Токен из Termux") },
            visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text("Вставь токен после запуска bridge.py") },
            singleLine = true
        )
        Button(
            onClick = { runRequest("/v1/health") },
            enabled = !busy
        ) { Text("Статус Astra Bridge") }
        Button(
            onClick = {
                runRequest(
                    "/v1/tool",
                    JSONObject().put("tool", "list_files").put("arguments", JSONObject().put("path", "."))
                )
            },
            enabled = !busy
        ) { Text("Показать workspace") }

        Text("Чтение файла workspace", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = filePath,
            onValueChange = { filePath = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Относительный путь") },
            placeholder = { Text("например, notes.txt") },
            singleLine = true
        )
        Button(
            onClick = {
                val requestedPath = filePath.trim()
                if (requestedPath.isEmpty()) {
                    result = "Укажи путь к файлу внутри workspace."
                } else {
                    runRequest(
                        "/v1/tool",
                        JSONObject().put("tool", "read_file")
                            .put("arguments", JSONObject().put("path", requestedPath))
                    )
                }
            },
            enabled = !busy
        ) { Text("Прочитать файл") }

        Text("Поиск локальной памяти", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = memoryQuery,
            onValueChange = { memoryQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поисковая фраза") },
            placeholder = { Text("Оставь пустым, чтобы показать последние записи") },
            singleLine = true
        )
        Button(
            onClick = {
                runRequest(
                    "/v1/tool",
                    JSONObject().put("tool", "memory_search")
                        .put("arguments", JSONObject().put("query", memoryQuery.trim()))
                )
            },
            enabled = !busy
        ) { Text("Найти в памяти") }

        Spacer(Modifier.height(4.dp))
        Text("Ответ моста", style = MaterialTheme.typography.titleMedium)
        Text(result, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
