package com.astra.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatalogTest {
    @Test fun deduplicatesAndSortsWithoutLeakingUnboundedLabels() {
        val raw = listOf(
            AppCatalogEntry("org.example.beta", "Zoo\nFile"),
            AppCatalogEntry("org.example.alpha", "Alpha"),
            AppCatalogEntry("org.example.beta", "Duplicate"),
            AppCatalogEntry("bad package", "Untrusted")
        )
        val (apps, truncated) = AppCatalog.normalize(raw)
        assertEquals(2, apps.size)
        assertEquals("org.example.alpha", apps[0].packageName)
        assertEquals("Zoo File", apps[1].label)
        assertFalse(truncated)
    }

    @Test fun canSearchLabelOrPackageWithoutFullDataAccess() {
        val (entries, _) = AppCatalog.normalize(listOf(
            AppCatalogEntry("org.example.camera", "Camera"),
            AppCatalogEntry("org.example.clock", "Будильник")
        ))
        assertEquals("org.example.camera", AppCatalog.search(entries, "CAM").single().packageName)
        assertEquals("org.example.clock", AppCatalog.search(entries, "example.clock").single().packageName)
        assertTrue(AppCatalog.search(entries, "nonexistent").isEmpty())
    }

    @Test fun limitsCatalogSize() {
        val input = (1..AppCatalog.MAX_ENTRIES + 6).map { AppCatalogEntry("org.example.app$it", "App $it") }
        val (entries, truncated) = AppCatalog.normalize(input)
        assertEquals(AppCatalog.MAX_ENTRIES, entries.size)
        assertTrue(truncated)
    }
}
