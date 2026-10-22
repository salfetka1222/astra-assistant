package com.astra.assistant.core

class AssistantEngine {
    fun replyTo(message: String): String {
        val text = message.trim()
        if (text.isBlank()) return "Напиши сообщение — я на связи."

        return when {
            text.contains("привет", ignoreCase = true) ->
                "Привет! Я Astra Assistant 👋 Пока я работаю в демонстрационном режиме. Следующим этапом подключим настоящую ИИ-модель."
            text.contains("помощ", ignoreCase = true) ->
                "Пока я умею сохранять нашу переписку на телефоне. Позже добавим ИИ, голос, память и инструменты с твоего разрешения."
            else ->
                "Я получил твоё сообщение: «$text»\n\nСейчас это локальный демонстрационный режим без облачного ИИ. История чата сохраняется на устройстве."
        }
    }
}
