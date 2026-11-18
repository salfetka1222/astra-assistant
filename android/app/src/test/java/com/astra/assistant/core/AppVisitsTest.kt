package com.astra.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVisitsTest {
    private val a = AppCatalogEntry("app.one", "Один")
    private val b = AppCatalogEntry("app.two", "Два")

    @Test fun activitiesWithinAnAppCountOnceAndUpdateLastSeen() {
        val visits = AppVisits(listOf(a, b))
        visits.foreground(a.packageName, 10)
        visits.foreground(a.packageName, 12)
        assertEquals(listOf(AppVisit(a, 1, 12)), visits.top())
        visits.foreground(b.packageName, 15)
        visits.foreground(a.packageName, 20)
        assertEquals(listOf(AppVisit(a, 2, 20), AppVisit(b, 1, 15)), visits.top())
    }

    @Test fun unknownPackagesAndScreenOffEndAVisit() {
        val visits = AppVisits(listOf(a))
        visits.foreground("other.app", 1)
        assertTrue(visits.top().isEmpty())
        visits.foreground(a.packageName, 2)
        visits.foreground("other.app", 3)
        visits.foreground(a.packageName, 4)
        visits.interrupt()
        visits.foreground(a.packageName, 5)
        assertEquals(listOf(AppVisit(a, 3, 5)), visits.top())
    }

    @Test fun topTenBreaksTiesByRecency() {
        val apps = (1..14).map { AppCatalogEntry("test.app$it", "Имя") }
        val visits = AppVisits(apps)
        apps.forEachIndexed { i, app -> visits.foreground(app.packageName, i.toLong()) }
        assertEquals(apps.asReversed().take(10), visits.top().map { it.app })
    }
}
