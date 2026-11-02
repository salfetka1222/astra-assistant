package com.astra.assistant.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AboutAstraScreen(onBack: () -> Unit) {
    var taps by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val appInfo = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }
        TextButton(onClick = { taps++ }) { Text("✦ ASTRA EVOLUTION", style = MaterialTheme.typography.headlineMedium) }
        Text("Версия ${appInfo.versionName} (код ${PackageInfoCompat.getLongVersionCode(appInfo)}) • Android Assistant")
        HorizontalDivider()
        Text("Создатель: ones #manera", style = MaterialTheme.typography.titleLarge)
        Text("Помощник: ChatGPT ♥️", style = MaterialTheme.typography.titleMedium)
        val context = LocalContext.current
        Button(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/astra_evolution"))
            runCatching { context.startActivity(intent) }
        }) { Text("Наш Telegram-канал ↗") }
        Text("Astra — открываемый для пользователей проект Android-инструментов, локального чата и безопасного подключения Termux.")
        Text("Технологии: Kotlin, Jetpack Compose, Material Design 3, Python, Termux Bridge.")
        Text("Приватность: мост работает локально на 127.0.0.1 и требует секретный токен. Разрешения Android включаются пользователем.")
        Text("Текущий чат использует демонстрационный движок; полноценная языковая модель не подключена.")
        if (taps >= 7) Text("✦ Секретное послание: Astra только начинает свою эволюцию! ♥️")
    }
}

@Composable
fun AstraSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val steps = listOf(
        Triple("1. Установи Termux", "Установи Termux из официального F-Droid или GitHub. Команды ниже вводятся в терминале Termux.", ""),
        Triple("2. Подготовь Termux", "Обнови пакеты и установи Python и Git.", "pkg update && pkg install -y python git"),
        Triple("3. Скачай компоненты Astra", "Скачай исходный код с GitHub. Команды работают в Termux.", "git clone -b feature/astra-1.0 https://github.com/salfetka1222/astra-assistant.git"),
        Triple("4. Запусти мост", "Открой папку Termux-агента и запусти локальный сервер. Не публикуй токен подключения.", "cd ~/astra-assistant/termux-agent && python bridge.py"),
        Triple("5. Подключи приложение", "Скопируй токен из Termux, открой Astra → Инструменты → Termux и вставь токен. Затем проверь соединение.", ""),
        Triple("6. Дополнительные разрешения", "Accessibility включается вручную в настройках Android, если нужны действия с интерфейсом. Shizuku необязателен. Не выдавай разрешения, назначение которых непонятно.", "")
    )
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Как подключить Astra", style = MaterialTheme.typography.headlineSmall)
        Text("Настройка выполняется на телефоне. Root не нужен. Установка Termux и запуск моста добровольны.")
        steps.forEach { (title, description, command) ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description)
                    if (command.isNotEmpty()) {
                        Text(command, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Astra Termux", command))
                        }) { Text("Скопировать команду") }
                    }
                }
            }
        }
        Text("Если соединения нет: проверь, что Termux работает, токен введён без пробелов, а порт 8765 не занят. Не открывай порт в интернет.")
    }
}
