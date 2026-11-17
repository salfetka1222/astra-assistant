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
        id = "2.0.0-alpha.4",
        version = "2.0.0-alpha.4",
        changes = listOf(
            ChangeItem("📱 Нативные команды", "Камера, будильники и телефон", "В чате можно попросить открыть системные приложения, не используя Termux. Набор номера не запускает звонок."),
            ChangeItem("🧭 Диагностика", "Возможности Astra", "Команда «покажи возможности» показывает доступные нативные функции и состояние Accessibility."),
            ChangeItem("🔒 Контроль", "Только разрешённые команды", "Запуск приложений требует подтверждения. Составные и неизвестные запросы не запускают действия."),
            ChangeItem("🧪 Проверки", "Новые регрессионные тесты", "Добавлены тесты запуска системных приложений и блокирования небезопасных запросов. Для диалога с ChatGPT пока нужен Termux Bridge.")
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
