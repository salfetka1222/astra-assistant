package com.astra.assistant.ui

import android.app.Application
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
            dao.insert(MessageEntity(role = "assistant", content = engine.replyTo(cleanText)))
        }
    }

    fun clearHistory() {
        viewModelScope.launch { dao.clear() }
    }
}
