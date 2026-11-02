package com.astra.assistant.ui

import android.content.Context
import android.app.ActivityManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astra.assistant.ThemeMode
import com.astra.assistant.data.MessageEntity

@Composable
fun ChatScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showBridge by rememberSaveable { mutableStateOf(false) }
    var showUpdates by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    var deviceSummary by remember { mutableStateOf("Нажми «Обновить состояние», чтобы получить показатели.") }

    if (showUpdates) {
        UpdatesScreen(onBack = { showUpdates = false })
        return
    }

    if (showBridge) {
        BridgeScreen(onBack = { showBridge = false })
        return
    }

    if (showSettings) {
        SettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onBack = { showSettings = false },
            onUpdates = { showSettings = false; showUpdates = true }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                modifier = Modifier.heightIn(min = 92.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                listOf("Главная", "Чат", "Инструменты").forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text(listOf("⌂", "✦", "⚙")[index], fontSize = 27.sp) },
                        label = { Text(label, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ASTRA", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    Text("EVOLUTION • локальный режим", maxLines = 1, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { showSettings = true }) { Text("⚙") }
            }
            when (selectedTab) {
                0 -> {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Центр управления", style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Astra готова к работе. Инструменты подключаются отдельно; ИИ пока в демонстрационном режиме.",
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Button(onClick = { selectedTab = 1 }) { Text("Открыть чат") }
                        }
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Состояние телефона", style = MaterialTheme.typography.titleLarge)
                            Text(deviceSummary, style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = {
                                deviceSummary = try {
                                    val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                                    val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                                    val batteryLabel = if (percent in 0..100) "$percent%" else "недоступно"
                                    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                                    val info = ActivityManager.MemoryInfo()
                                    manager.getMemoryInfo(info)
                                    val storage = StatFs(Environment.getDataDirectory().absolutePath)
                                    val mib = 1024L * 1024L
                                    "Батарея: $batteryLabel\nСвободная ОЗУ: ${info.availMem / mib} МиБ\nСвободное место: ${storage.availableBytes / mib} МиБ"
                                } catch (_: Exception) {
                                    "Не удалось получить показатели устройства."
                                }
                            }) { Text("Обновить состояние") }
                        }
                    }
                    Card(onClick = { showBridge = true }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Termux Bridge", style = MaterialTheme.typography.titleMedium)
                            Text("Подключение и команды", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Card(onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Настройки и обновления", style = MaterialTheme.typography.titleMedium)
                            Text("Тема, версия и безопасность", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    }
                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.id }) { message -> MessageBubble(message) }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = draft, onValueChange = { draft = it },
                            modifier = Modifier.weight(1f), placeholder = { Text("Сообщение Astra…") },
                            shape = RoundedCornerShape(24.dp), maxLines = 4)
                        Button(onClick = { viewModel.send(draft); draft = "" },
                            enabled = draft.isNotBlank()) { Text("➤") }
                    }
                    TextButton(onClick = viewModel::clearHistory) { Text("Очистить историю") }
                }
                else -> {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    Card(onClick = { showBridge = true }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Termux", style = MaterialTheme.typography.titleLarge)
                            Text("Открыть существующий мост", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Card(onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp)) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Параметры Astra", style = MaterialTheme.typography.titleLarge)
                            Text("Тема и проверка обновлений", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("Shizuku и новые инструменты появятся на следующих этапах.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onUpdates: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.height(24.dp))
        Text("Внешний вид", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Выбери, как должна выглядеть Astra Assistant.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))

        ThemeMode.values().forEach { mode ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (themeMode == mode) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                onClick = { onThemeModeChange(mode) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) }
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(mode.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "Следует настройке Android"
                                ThemeMode.LIGHT -> "Светлая тема с мягким красным акцентом"
                                ThemeMode.DARK -> "Тёмная тема с приглушённым красным акцентом"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onUpdates) { Text("Проверить обновления Astra") }

        Spacer(Modifier.height(24.dp))
        Text("О приложении", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Astra Evolution • Beta 3\nЛокальный режим. Настройки темы сохраняются на устройстве. Расширенная персонализация и сведения о создателях появятся в релизе 1.0.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MessageBubble(message: MessageEntity) {
    val isUser = message.role == "user"
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
