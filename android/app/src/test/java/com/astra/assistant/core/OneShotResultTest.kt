package com.astra.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OneShotResultTest {
    @Test fun deliversSuccessOnlyOnceEvenIfCancelledLater() {
        val results = mutableListOf<Boolean>()
        val once = OneShotResult { results.add(it) }
        once.deliver(true)
        once.deliver(false)
        once.deliver(true)
        assertEquals(listOf(true), results)
    }

    @Test fun deliversFailureOnlyOnceEvenIfGestureCompletesLater() {
        val results = mutableListOf<Boolean>()
        val once = OneShotResult { results.add(it) }
        once.deliver(false)
        once.deliver(true)
        assertEquals(listOf(false), results)
    }
}
