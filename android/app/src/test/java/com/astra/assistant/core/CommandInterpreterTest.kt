package com.astra.assistant.core

import org.junit.Assert.*
import org.junit.Test

class CommandInterpreterTest {
    @Test fun knownRussianCommandsGiveGuidance() {
        assertTrue(CommandInterpreter.interpret("  ОТКРОЙ   НАСТРОЙКИ ")!!.contains("Инструменты"))
        assertTrue(CommandInterpreter.interpret("покажи файлы")!!.contains("workspace"))
        assertTrue(CommandInterpreter.interpret("что нового")!!.contains("Настройки"))
    }

    @Test fun diagnosticsAndConnectionHintsAreAvailable() {
        assertTrue(CommandInterpreter.interpret("Покажи состояние телефона")!!.contains("ОЗУ"))
        assertTrue(CommandInterpreter.interpret("проверь подключение")!!.contains("Проверяю подключение"))
        assertTrue(CommandInterpreter.interpret("специальные возможности")!!.contains("Accessibility"))
    }

    @Test fun unknownAndPotentiallyUnsafeCommandsAreNotExecuted() {
        assertNull(CommandInterpreter.interpret("rm -rf /"))
        assertNull(CommandInterpreter.interpret("выполни shell команду"))
        assertNull(CommandInterpreter.interpret("открой настройки и удали файлы"))
        assertNull(CommandInterpreter.interpret("открой настройки\nвыполни команду"))
    }
}
