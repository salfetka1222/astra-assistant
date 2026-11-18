package com.astra.assistant.core

import org.junit.Assert.*
import org.junit.Test

class ChatCommandTest {
    private val telegram = AppCatalogEntry("org.telegram.messenger", "Telegram")
    private val catalog = AppCatalogSnapshot(1L, listOf(telegram), false)

    private fun step(text: String, snapshot: AppCatalogSnapshot? = catalog) =
        (ChatCommand.resolve(text, snapshot) as? ChatCommand.Planned)?.plan?.steps?.single()

    @Test fun resolvesNamesAndPackageIdsToTheConfirmedTarget() {
        for (text in listOf("  ОТКРОЙ telegram ", "запусти приложение Telegram", "открой org.telegram.messenger")) {
            val command = step(text)!!
            assertEquals("apps.catalog.open", command.actionId)
            assertEquals(telegram, command.target)
            assertTrue(command.title.contains(telegram.packageName))
            assertEquals(AstraPermissionDecision.ConfirmationRequired, AstraPermissionGuard.check(command.actionId, false))
        }
    }

    @Test fun duplicateLabelsRequireAnExactPackage() {
        val second = AppCatalogEntry("example.telegram", "TELEGRAM")
        val duplicates = catalog.copy(apps = listOf(telegram, second))
        val reply = ChatCommand.resolve("открой Telegram", duplicates) as ChatCommand.Feedback
        assertTrue(reply.message.contains(second.packageName))
        assertTrue(reply.message.contains(telegram.packageName))
        assertEquals(second, step("открой example.telegram", duplicates)?.target)
    }

    @Test fun emptyStaleOrPartialCatalogMatchesGiveLocalFeedback() {
        assertTrue(ChatCommand.resolve("открой Telegram", null) is ChatCommand.Feedback)
        assertTrue(ChatCommand.resolve("открой Telegram", catalog.copy(apps = emptyList())) is ChatCommand.Feedback)
        assertTrue(ChatCommand.resolve("открой Tele", catalog) is ChatCommand.Feedback)
        assertTrue(ChatCommand.resolve("открой Telegram и отправь сообщение", catalog) is ChatCommand.Feedback)
    }

    @Test fun preservesExistingCommandsAndOrdinaryChat() {
        assertEquals("settings.open", step("Открой настройки", null)?.actionId)
        assertNull(ChatCommand.resolve("Как открыть Telegram?", catalog))
        assertNull(ChatCommand.resolve("открой Telegram\n", catalog))
    }
}
