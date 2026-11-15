package com.astra.assistant.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val CHATGPT_AUTH_BRIDGE = "http://127.0.0.1:8765"

private data class AuthResult(val httpCode: Int, val json: JSONObject)

private fun requestAuth(path: String, token: String, post: Boolean = false): AuthResult {
    val connection = URL(CHATGPT_AUTH_BRIDGE + path).openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = if (post) "POST" else "GET"
        connection.connectTimeout = 2500
        connection.readTimeout = 8500
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
        if (post) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val bytes = stream?.use { it.readNBytes(16_384) } ?: byteArrayOf()
        AuthResult(code, JSONObject(bytes.toString(Charsets.UTF_8)))
    } finally {
        connection.disconnect()
    }
}

/** Auth credentials stay in private Termux storage; Astra only sees status and an authorization URL. */
@Composable
fun ChatGPTSignInCard(bridgeToken: String, onFeedback: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val usableToken = bridgeToken.trim().length >= 32
    var busy by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf("unknown") }
    var account by remember { mutableStateOf("") }
    var usageEnabled by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }

    fun refreshStatus() {
        if (!usableToken || busy) return
        busy = true
        scope.launch {
            try {
                val answer = withContext(Dispatchers.IO) {
                    requestAuth("/v1/auth/status", bridgeToken.trim())
                }
                if (answer.httpCode == 200 && answer.json.optBoolean("ok")) {
                    state = answer.json.optString("state", "unknown")
                    account = answer.json.optString("account", "")
                    usageEnabled = answer.json.optBoolean("usage_enabled")
                    if (state == "error") onFeedback("ChatGPT: авторизация не завершилась. Повтори вход.")
                } else onFeedback("Не удалось проверить ChatGPT (HTTP ${answer.httpCode}).")
            } catch (_: Exception) {
                state = "unknown"
                onFeedback("Termux Bridge не отвечает. Запусти python bridge.py в Termux.")
            } finally {
                busy = false
            }
        }
    }

    // A changed token must never keep the status of another connection.
    LaunchedEffect(bridgeToken) {
        state = "unknown"
        account = ""
        usageEnabled = false
    }

    Text("Вход через ChatGPT", style = MaterialTheme.typography.titleMedium)
    Text(
        "Авторизация проходит в браузере OpenAI. Astra не видит пароль и не получает токены аккаунта. " +
            "Если разрешишь, запросы ИИ будут использовать доступную часть твоего плана ChatGPT.",
        style = MaterialTheme.typography.bodyMedium
    )

    val statusText = when {
        state == "connected" && usageEnabled -> "ChatGPT подключён: использование плана разрешено"
        state == "connected" -> "Аккаунт подключён, но доступ к плану не разрешён"
        state == "waiting" -> "Ожидание подтверждения в браузере"
        state == "connecting" -> "Проверка и сохранение разрешений"
        state == "error" -> "Ошибка авторизации — попробуй снова"
        state == "disconnected" -> "Аккаунт не подключён"
        else -> "Статус пока не проверен"
    }
    Text(statusText, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (account.isNotEmpty() && state == "connected") {
        Text(account, style = MaterialTheme.typography.bodySmall)
    }
    if (!usableToken) {
        Text("Сначала укажи токен Termux Bridge выше и проверь соединение.")
    }

    Button(
        enabled = usableToken && !busy,
        onClick = {
            busy = true
            scope.launch {
                try {
                    val answer = withContext(Dispatchers.IO) {
                        requestAuth("/v1/auth/start", bridgeToken.trim(), post = true)
                    }
                    if (answer.httpCode != 200 || !answer.json.optBoolean("ok")) {
                        onFeedback("Не удалось начать вход через ChatGPT (HTTP ${answer.httpCode}).")
                    } else {
                        val url = answer.json.optString("authorize_url", "")
                        val parsed = Uri.parse(url)
                        if (parsed.scheme == "https" && parsed.host == "auth.openai.com" &&
                            parsed.path == "/api/accounts/authorize"
                        ) {
                            state = "waiting"
                            context.startActivity(Intent(Intent.ACTION_VIEW, parsed))
                        } else {
                            onFeedback("Termux вернул неверный адрес авторизации.")
                        }
                    }
                } catch (_: Exception) {
                    onFeedback("Не удалось открыть авторизацию. Проверь Termux Bridge.")
                } finally {
                    busy = false
                }
            }
        }
    ) { Text("Продолжить с ChatGPT") }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = usableToken && !busy, onClick = { refreshStatus() }) {
            Text("Обновить статус")
        }
        OutlinedButton(enabled = !busy, onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/settings/usage")))
        }) { Text("Лимиты") }
    }
    if (state == "connected") {
        TextButton(enabled = !busy, onClick = { confirmDisconnect = true }) {
            Text("Отключить ChatGPT")
        }
    }
    Text(
        "Подключение не даёт доступа к этой переписке в ChatGPT, памяти аккаунта или контактам. " +
            "Системные действия по-прежнему требуют отдельного подтверждения.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Отключить ChatGPT?") },
            text = { Text("Astra прекратит использовать токены. Локальные данные входа будут очищены.") },
            confirmButton = {
                Button(onClick = {
                    confirmDisconnect = false
                    busy = true
                    scope.launch {
                        try {
                            val answer = withContext(Dispatchers.IO) {
                                requestAuth("/v1/auth/disconnect", bridgeToken.trim(), post = true)
                            }
                            if (answer.httpCode == 200 && answer.json.optBoolean("ok")) {
                                state = "disconnected"
                                account = ""
                                usageEnabled = false
                                onFeedback(
                                    if (answer.json.optBoolean("revocation_confirmed")) {
                                        "ChatGPT отключён, удалённое разрешение отозвано."
                                    } else "ChatGPT отключён локально. Проверь доступы в настройках ChatGPT."
                                )
                            } else onFeedback("Не удалось отключить ChatGPT.")
                        } catch (_: Exception) {
                            onFeedback("Не удалось связаться с Termux для отключения.")
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Отключить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text("Отмена") }
            }
        )
    }
}
