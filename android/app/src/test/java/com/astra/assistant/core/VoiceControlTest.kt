package com.astra.assistant.core

import org.junit.Assert.*
import org.junit.Test

class VoiceControlTest {
    @Test fun proposalsExpireAndOnlyMapToConfirmedNavigation() {
        for (key in VoiceShortcut.entries) {
            val entry = AstraActionRegistry.find(key.actionId)!!
            assertEquals(ActionRisk.NAVIGATION, entry.risk)
            assertTrue(entry.requiresConfirmation)
            val p = VoiceProposal(key, 1_000_000, "a".repeat(36))
            assertEquals(p, VoiceProposal.decode(p.encode(), 1_119_999))
            assertNull(VoiceProposal.decode(p.encode(), 1_120_000))
            assertNull(VoiceProposal.decode(p.encode(), 999_999))
        }
        assertNull(VoiceProposal.decode("shell|1000|" + "a".repeat(36), 1000))
        assertNull(VoiceShortcut.fromKey("wifi; reboot"))
    }

    @Test fun speechHandlesPunctuationAndPreservesQuestionsAndDrafts() {
        assertEquals("открой настройки", VoiceDraft.append("", "Астра, открой настройки.", null))
        assertEquals("Почему не работает Wi-Fi?", VoiceDraft.append("", "Почему не работает Wi-Fi?", null))
        assertEquals("Скажи ещё слово.", VoiceDraft.append("Скажи", "ещё слово.", null))
        assertNull(VoiceDraft.append("x".repeat(3000), "ещё", null))
    }
}
