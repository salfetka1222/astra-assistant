package com.astra.assistant.ui

import org.junit.Assert.*
import org.junit.Test

class WhatsNewCatalogTest {
    @Test fun newReleaseShowsOnce() {
        assertTrue(WhatsNewCatalog.shouldShow(null, "1.1.0"))
        assertTrue(WhatsNewCatalog.shouldShow("1.0.0", "1.1.0"))
        assertFalse(WhatsNewCatalog.shouldShow("1.1.0", "1.1.0"))
    }

    @Test fun bundledChangelogHasContent() {
        assertEquals("1.1.0", WhatsNewCatalog.current.id)
        assertTrue(WhatsNewCatalog.current.changes.isNotEmpty())
        assertTrue(WhatsNewCatalog.current.changes.all { it.title.isNotBlank() && it.details.isNotBlank() })
    }
}
