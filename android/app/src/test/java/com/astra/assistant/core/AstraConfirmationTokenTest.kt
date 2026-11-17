package com.astra.assistant.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraConfirmationTokenTest {
    @Test fun rapidDoubleConfirmationExecutesOnlyOnce() {
        val plan = AstraTaskEngine.plan(listOf("apps.camera.open"))!!
        val token = AstraConfirmationToken(plan)
        assertSame(plan, token.plan)
        assertTrue(token.consume())
        assertFalse(token.consume())
    }

    @Test fun cancelledRequestCannotBeConfirmedLater() {
        val token = AstraConfirmationToken(AstraTaskEngine.plan(listOf("settings.open"))!!)
        assertTrue(token.consume()) // cancellation
        assertFalse(token.consume()) // stale confirmation callback
    }

    @Test fun eachNewRequestGetsItsOwnConfirmation() {
        val plan = AstraTaskEngine.plan(listOf("apps.alarms.open"))!!
        val first = AstraConfirmationToken(plan)
        val second = AstraConfirmationToken(plan)
        assertTrue(first.consume())
        assertTrue(second.consume())
        assertFalse(first.consume())
        assertFalse(second.consume())
    }
}
