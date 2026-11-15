package com.astra.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidCommandParserTest {
    @Test fun recognizesOnlyReviewedCommands() {
        assertEquals(ApprovedAndroidCommand.SETTINGS, AndroidCommandParser.parse("  ОТКРОЙ   НАСТРОЙКИ "))
        assertEquals(ApprovedAndroidCommand.WIFI_SETTINGS, AndroidCommandParser.parse("Открой настройки Wi-Fi"))
        assertEquals(ApprovedAndroidCommand.DISPLAY_SETTINGS, AndroidCommandParser.parse("открой настройки экрана"))
        assertEquals(ApprovedAndroidCommand.SOUND_SETTINGS, AndroidCommandParser.parse("открой настройки звука"))
        assertEquals(ApprovedAndroidCommand.APP_SETTINGS, AndroidCommandParser.parse("открой список приложений"))
    }

    @Test fun rejectsMultistepOrUnapprovedCommands() {
        listOf(
            "открой настройки и выключи защиту",
            "открой настройки; rm -rf /",
            "открой настройки\nудали файлы",
            "открой настройки\rскрытое действие",
            "позвони маме",
            "настройки моего аккаунта",
            "открой телеграм и отправь сообщение",
            "открой настройки".repeat(20)
        ).forEach { assertNull("Unexpected action for: $it", AndroidCommandParser.parse(it)) }
    }
}
