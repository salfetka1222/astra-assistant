package com.astra.assistant.ui

import android.app.Application
import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astra.assistant.core.AssistantEngine
import com.astra.assistant.core.AstraTaskEngine
import com.astra.assistant.core.AstraNativeActionExecutor
import com.astra.assistant.core.ActionRisk
import com.astra.assistant.core.AstraActionRegistry
import com.astra.assistant.data.AppDatabase
import com.astra.assistant.data.MessageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).messageDao()
    private val engine = AssistantEngine()

    // Count concurrent replies so the animation stays visible until the last finishes.
    private val _pendingReplies = MutableStateFlow(0)
    val pendingReplies = _pendingReplies.asStateFlow()

    val messages = dao.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun send(text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        // Inspect raw input before trim: embedded or trailing newlines must not become actions.
        val nativePlan = AstraTaskEngine.fromUserText(text)

        // Set before launching the coroutine: the indicator appears immediately on Send.
        _pendingReplies.update { it + 1 }
        viewModelScope.launch {
            try {
                dao.insert(MessageEntity(role = "user", content = cleanText))
                val response = if (nativePlan != null) {
                    val action = nativePlan.steps.singleOrNull()
                        ?.let { AstraActionRegistry.find(it.actionId) }
                    if (action?.risk == ActionRisk.READ_ONLY) {
                        withContext(Dispatchers.IO) {
                            AstraNativeActionExecutor.execute(getApplication<Application>(), nativePlan, confirmed = false).message
                        }
                    } else {
                        "Команда распознана: ${action?.title ?: "действие"}. Подтверди или отмени её в диалоге."
                    }
                } else when (cleanText.trim().lowercase().replace(Regex("\\s+"), " ")) {
                    "покажи файлы", "список файлов", "файлы termux", "что в workspace" -> bridgeRequest("list_files")
                    "анализ workspace", "анализ python файлов" -> bridgeRequest("workspace_summary")
                    "проверь termux", "статус termux", "проверь подключение", "проверить мост" -> bridgeRequest(null)
                    else -> modelReplyOrDemo(cleanText)
                }
                // Persist the answer before hiding the typing indicator.
                dao.insert(MessageEntity(role = "assistant", content = response))
            } finally {
                // Also executes on network failure or coroutine cancellation.
                _pendingReplies.update { (it - 1).coerceAtLeast(0) }
            }
        }
    }

    private suspend fun modelReplyOrDemo(message: String): String = withContext(Dispatchers.IO) {
        if (message.length > 3000) {
            return@withContext "Для диалога с ИИ сократи сообщение до 3000 символов."
        }
        val prefs = getApplication<Application>()
            .getSharedPreferences("astra_bridge_preferences", Context.MODE_PRIVATE)
        val token = prefs.getString("bridge_token", "").orEmpty().trim()
        if (token.length < 32) {
            return@withContext engine.replyTo(message) +
                "\n\nДля ответов ChatGPT подключи Termux Bridge и войди в аккаунт в разделе «Инструменты»."
        }
        try {
            val body = JSONObject().put("message", message)
            val (status, jsonText) = bridgeCall("/v1/chat", token, body, readTimeoutMs = 55_000)
            when (status) {
                200 -> {
                    val json = JSONObject(jsonText)
                    if (json.optBoolean("ok")) {
                        json.optString("reply").takeIf { it.isNotBlank() }
                            ?: "ИИ вернул пустой ответ."
                    } else "ИИ не смог сформировать ответ."
                }
                401 -> "Termux Bridge: неверный токен. Проверь подключение в разделе «Инструменты»."
                503 -> "Войди в аккаунт ChatGPT через «Инструменты → Мост Termux → Продолжить с ChatGPT»."
                403 -> "ChatGPT подключён, но использование плана не разрешено. Повтори вход с согласием."
                502 -> "ChatGPT сейчас недоступен. Проверь подключение и лимиты в разделе «Инструменты»."
                400, 413 -> "Termux Bridge отклонил сообщение. Проверь длину текста."
                else -> "Не удалось получить ответ ИИ (HTTP $status)."
            }
        } catch (_: Exception) {
            "Нет подключения к ИИ через Termux Bridge. Проверь статус моста в «Инструментах»."
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

    private fun bridgeCall(path: String, token: String, body: JSONObject?, readTimeoutMs: Int = 4000): Pair<Int, String> {
        val connection = URL("http://127.0.0.1:8765$path").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.connectTimeout = 2500
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val response = if (status in 200..299) connection.inputStream else connection.errorStream
            // Bound bytes read from the local bridge, not just the displayed text.
            val data = response?.use { it.readNBytes(12_000) } ?: byteArrayOf()
            Pair(status, data.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }

    fun reportNativeAction(message: String) {
        viewModelScope.launch { dao.insert(MessageEntity(role = "assistant", content = message)) }
    }

    fun clearHistory() {
        viewModelScope.launch { dao.clear() }
    }
}
