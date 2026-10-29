package com.astra.assistant.ui

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

private const val BRIDGE_PREFS = "astra_bridge_preferences"
private const val BRIDGE_TOKEN_KEY = "bridge_token"
private const val BRIDGE_BASE_URL = "http://127.0.0.1:8765"

@Composable
fun BridgeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(BRIDGE_PREFS, Context.MODE_PRIVATE) }
    var token by remember { mutableStateOf(prefs.getString(BRIDGE_TOKEN_KEY, "") ?: "") }
    var result by remember { mutableStateOf("Мост пока не проверен.") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
                        "HTTP $status\n$text"
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text("Мост Termux", style = MaterialTheme.typography.headlineSmall)
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
            placeholder = { Text("Вставь токен после запуска bridge.py") },
            singleLine = true
        )
        Button(onClick = { runRequest("/v1/health") }, enabled = !busy) {
            Text(if (busy) "Проверяем…" else "Проверить соединение")
        }
        Button(
            onClick = {
                runRequest(
                    "/v1/tool",
                    JSONObject().put("tool", "device_status").put("arguments", JSONObject())
                )
            },
            enabled = !busy
        ) { Text("Получить статус устройства") }
        Button(
            onClick = {
                runRequest(
                    "/v1/tool",
                    JSONObject().put("tool", "list_files").put("arguments", JSONObject().put("path", "."))
                )
            },
            enabled = !busy
        ) { Text("Показать workspace") }
        Spacer(Modifier.height(4.dp))
        Text("Ответ моста", style = MaterialTheme.typography.titleMedium)
        Text(result, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
