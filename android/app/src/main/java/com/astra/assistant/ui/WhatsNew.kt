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
        id = "2.0.0-alpha.6",
        version = "2.0.0-alpha.6",
        changes = listOf(
            ChangeItem("🧠 Device Intelligence", "Карта устройства", "Локальная проверка модели телефона, Android, батареи, памяти и энергосбережения без чтения личных файлов."),
            ChangeItem("📱 Системные возможности", "Доступные действия", "Astra проверяет обработчики камеры, будильника и набора номера, а также состояние Accessibility."),
            ChangeItem("💬 Команды чата", "Изучи мой телефон", "Новая команда работает без Termux Bridge. Отчёт также доступен в разделе «Инструменты»."),
            ChangeItem("🔒 Конфиденциальность", "Только по запросу", "Нет фонового сканирования, списка всех приложений и сбора идентификаторов. Чат сохраняет сообщения в локальной истории.")
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
