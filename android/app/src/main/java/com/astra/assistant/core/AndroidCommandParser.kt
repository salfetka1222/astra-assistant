package com.astra.assistant.core

import java.util.Locale

/**
 * Only exact, reviewed phrases can request an Android action.
 * This parser never executes an action and never interprets AI-generated commands.
 */
enum class ApprovedAndroidCommand(val title: String) {
    SETTINGS("настройки Android"),
    WIFI_SETTINGS("настройки Wi-Fi"),
    DISPLAY_SETTINGS("настройки экрана"),
    SOUND_SETTINGS("настройки звука"),
    APP_SETTINGS("список приложений")
}

object AndroidCommandParser {
    private val allowedPhrases = mapOf(
        "открой настройки" to ApprovedAndroidCommand.SETTINGS,
        "открыть настройки" to ApprovedAndroidCommand.SETTINGS,
        "запусти настройки" to ApprovedAndroidCommand.SETTINGS,
        "настройки android" to ApprovedAndroidCommand.SETTINGS,
        "открой настройки wi-fi" to ApprovedAndroidCommand.WIFI_SETTINGS,
        "открой настройки wifi" to ApprovedAndroidCommand.WIFI_SETTINGS,
        "открой настройки вайфая" to ApprovedAndroidCommand.WIFI_SETTINGS,
        "открой настройки экрана" to ApprovedAndroidCommand.DISPLAY_SETTINGS,
        "открой настройки дисплея" to ApprovedAndroidCommand.DISPLAY_SETTINGS,
        "открой настройки звука" to ApprovedAndroidCommand.SOUND_SETTINGS,
        "открой список приложений" to ApprovedAndroidCommand.APP_SETTINGS,
        "открой настройки приложений" to ApprovedAndroidCommand.APP_SETTINGS
    )

    fun parse(input: String): ApprovedAndroidCommand? {
        if (input.length > 100 || input.any { it == '\n' || it == '\r' }) return null
        val normalized = input.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        return allowedPhrases[normalized]
    }
}
