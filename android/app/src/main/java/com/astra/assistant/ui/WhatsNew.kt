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
        id = "1.3.0",
        version = "1.3.0",
        changes = listOf(
            ChangeItem("📱 Диагностика", "Состояние телефона прямо в чате", "Показывает заряд батареи, доступную оперативную память и свободное место."),
            ChangeItem("🔗 Termux", "Подключение моста из чата", "Проверка состояния Termux Bridge по локальному защищённому соединению."),
            ChangeItem("📂 Workspace", "Просмотр файлов и обзор Python", "Чат использует только разрешённые инструменты чтения Termux Bridge."),
            ChangeItem("🛠 Инструменты", "Единый центр диагностики", "Проверка Android Accessibility, подключения Termux и доступных инструментов."),
            ChangeItem("⚙️ Навигация", "Быстрый переход к настройкам", "Команды и кнопки для открытия настроек Android и раздела подключения."),
            ChangeItem("🔒 Надёжность", "Защита локального обмена", "Ограничен размер ответов Termux, сохранена проверка токена и списка доступных инструментов.")
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
