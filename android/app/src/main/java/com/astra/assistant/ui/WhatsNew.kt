package com.astra.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class ChangeItem(val category: String, val title: String, val details: String)
internal data class ChangeLog(val id: String, val version: String, val changes: List<ChangeItem>)

// Bundled with the APK: no network access needed, and no unverified server text is rendered.
internal object WhatsNewCatalog {
    fun shouldShow(lastSeenId: String?, currentId: String): Boolean = lastSeenId != currentId

    val current = ChangeLog(
        id = "2.0.0-alpha.1",
        version = "2.0.0-alpha.1",
        changes = listOf(
            ChangeItem("🧠 Astra Core", "Нативный движок команд", "Добавлены реестр действий, проверка разрешений и планировщик."),
            ChangeItem("📱 Android", "Настройки прямо из чата", "Astra открывает разделы настроек после твоего подтверждения."),
            ChangeItem("🔋 Диагностика", "Без обязательного Termux", "Состояние батареи, ОЗУ и памяти читается через Android API."),
            ChangeItem("🔒 Безопасность", "Только разрешённые действия", "Неизвестные и составные команды не выполняются. Для ответов ChatGPT пока нужен Termux Bridge.")
        )
    )
}

@Composable
internal fun WhatsNewDialog(changelog: ChangeLog, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("🚀 Что нового в Astra ${changelog.version}?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                changelog.changes.forEach { change ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(change.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(change.title, style = MaterialTheme.typography.titleMedium)
                        Text(change.details, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onClose) { Text("Продолжить") } }
    )
}
