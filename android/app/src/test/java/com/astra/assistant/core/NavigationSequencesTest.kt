package com.astra.assistant.core

import org.junit.Assert.*
import org.junit.Test

class NavigationSequencesTest {
    @Test fun knownRecipesHaveTwoAllowlistedSteps() {
        val allowed = setOf("back", "home", "notifications", "quick_settings")
        for (id in listOf("open_quick_settings", "return_home")) {
            val steps = NavigationSequences.steps(id)
            assertNotNull(steps)
            assertEquals(2, steps!!.size)
            assertTrue(steps.all { it in allowed })
        }
    }

    @Test fun arbitraryRecipesCannotBeExecuted() {
        assertNull(NavigationSequences.steps("shell"))
        assertNull(NavigationSequences.steps(""))
        assertNull(NavigationSequences.steps("open_quick_settings;home"))
    }
}
