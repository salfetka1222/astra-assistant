package com.astra.assistant.ui

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.astra.assistant.core.AssistantEngine
import com.astra.assistant.data.AppDatabase
import com.astra.assistant.data.MessageEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).messageDao()
    private val engine = AssistantEngine()

    val messages = dao.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun send(text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        viewModelScope.launch {
            dao.insert(MessageEntity(role = "user", content = cleanText))
            val response = if (cleanText.trim().lowercase().replace(Regex("\\s+"), " ") in setOf(
                "проверь батарею", "заряд батареи", "диагностика", "состояние телефона",
                "покажи состояние телефона", "проверь телефон"
            )) deviceStatus() else engine.replyTo(cleanText)
            dao.insert(MessageEntity(role = "assistant", content = response))
        }
    }

    private suspend fun deviceStatus(): String = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val charge = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            val storage = StatFs(Environment.getDataDirectory().absolutePath)
            val mib = 1024L * 1024L
            "Состояние телефона\nБатарея: ${if (charge in 0..100) "$charge%" else "недоступно"}\n" +
                "Свободная ОЗУ: ${info.availMem / mib} МиБ\n" +
                "Свободное место: ${storage.availableBytes / mib} МиБ"
        } catch (_: Exception) {
            "Не удалось получить показатели устройства. Открой раздел «Инструменты» для диагностики."
        }
    }

    fun clearHistory() {
        viewModelScope.launch { dao.clear() }
    }
}
