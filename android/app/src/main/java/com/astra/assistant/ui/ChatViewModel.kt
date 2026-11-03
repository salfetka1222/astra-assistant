package com.astra.assistant.ui

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astra.assistant.core.AssistantEngine
import com.astra.assistant.data.AppDatabase
import com.astra.assistant.data.MessageEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).messageDao()
    private val engine = AssistantEngine()

    val messages = dao.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun send(text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        viewModelScope.launch {
            dao.insert(MessageEntity(role = "user", content = cleanText))
            val response = if (cleanText.trim().lowercase().replace(Regex("\\s+"), " ") in setOf(
                "проверь батарею", "заряд батареи", "диагностика", "состояние телефона",
                "покажи состояние телефона", "проверь телефон"
            )) deviceStatus() else when (cleanText.trim().lowercase().replace(Regex("\\s+"), " ")) {
                "покажи файлы", "список файлов", "файлы termux", "что в workspace" -> bridgeRequest("list_files")
                "анализ workspace", "анализ python файлов" -> bridgeRequest("workspace_summary")
                "проверь termux", "статус termux", "проверь подключение", "проверить мост" -> bridgeRequest(null)
                else -> engine.replyTo(cleanText)
            }
            dao.insert(MessageEntity(role = "assistant", content = response))
        }
    }

    private suspend fun bridgeRequest(tool: String?): String = withContext(Dispatchers.IO) {
        val prefs = getApplication<Application>().getSharedPreferences("astra_bridge_preferences", Context.MODE_PRIVATE)
        val token = prefs.getString("bridge_token", "").orEmpty().trim()
        if (token.length < 32) return@withContext "Сначала подключи Termux Bridge и сохрани токен в разделе «Инструменты»."
        try {
            val health = bridgeCall("/v1/health", token, null)
            if (health.first != 200) return@withContext "Termux Bridge не отвечает на проверку: HTTP ${health.first}. Проверь токен и запущенный мост."
            val status = JSONObject(health.second)
            if (!status.optBoolean("ok") || status.optString("status") != "online")
                return@withContext "Termux Bridge сейчас недоступен."
            if (tool == null) return@withContext "Termux Bridge подключён. Время работы: ${status.optLong("uptime_seconds")} сек."
            val tools = status.optJSONArray("tools")
            if ((0 until (tools?.length() ?: 0)).none { tools?.optString(it) == tool })
                return@withContext "Инструмент $tool отключён на сервере Termux."
            val body = JSONObject().put("tool", tool).put("arguments", JSONObject().put("path", "."))
            val result = bridgeCall("/v1/tool", token, body)
            if (result.first != 200) return@withContext "Termux отказал в запросе: HTTP ${result.first}."
            val json = JSONObject(result.second)
            if (!json.optBoolean("ok")) return@withContext "Termux не смог выполнить запрос."
            "Termux ($tool):\n${json.opt("result")?.toString()?.take(3500) ?: "Нет данных"}"
        } catch (_: Exception) {
            "Нет подключения к Termux Bridge. Проверь, что мост запущен на 127.0.0.1:8765."
        }
    }

    private fun bridgeCall(path: String, token: String, body: JSONObject?): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:8765$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.connectTimeout = 2500
            connection.readTimeout = 4000
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val response = if (status in 200..299) connection.inputStream else connection.errorStream
            Pair(status, response?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(12000) } ?: "")
        } finally { connection.disconnect() }
    }

    private suspend fun deviceStatus(): String = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val charge = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            val storage = StatFs(Environment.getDataDirectory().absolutePath)
            val mib = 1024L * 1024L
            "Состояние телефона\nБатарея: ${if (charge in 0..100) "$charge%" else "недоступно"}\n" +
                "Свободная ОЗУ: ${info.availMem / mib} МиБ\n" +
                "Свободное место: ${storage.availableBytes / mib} МиБ"
        } catch (_: Exception) {
            "Не удалось получить показатели устройства. Открой раздел «Инструменты» для диагностики."
        }
    }

    fun clearHistory() {
        viewModelScope.launch { dao.clear() }
    }
}
