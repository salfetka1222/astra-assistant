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
        id = "2.0.0-alpha.3",
        version = "2.0.0-alpha.3",
        changes = listOf(
            ChangeItem("🛡️ Надёжность действий", "Один результат на команду", "Повторные события Accessibility больше не создают лишних ответов в чате."),
            ChangeItem("⏱️ Восстановление", "Тайм-аут жестов", "Если служба Accessibility перестала отвечать, Astra завершит ожидание с сообщением об ошибке."),
            ChangeItem("🔒 Безопасность", "Подтверждения сохранены", "Каждая команда управления Android по-прежнему требует твоего подтверждения."),
            ChangeItem("🧪 Тестирование", "Проверка повторных событий", "Добавлены тесты на ошибку и завершение одной операции. Для чата с ChatGPT пока нужен Termux Bridge.")
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
