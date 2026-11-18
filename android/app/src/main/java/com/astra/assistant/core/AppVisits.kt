package com.astra.assistant.core

internal data class AppVisit(val app: AppCatalogEntry, val count: Int, val lastSeen: Long)

/** Keep just one counter and timestamp per catalog entry, never a timeline. */
internal class AppVisits(catalog: List<AppCatalogEntry>) {
    private val allowed = catalog.associateBy { it.packageName }
    private val visits = mutableMapOf<String, AppVisit>()
    private var current: String? = null

    fun foreground(packageName: String?, timestamp: Long) {
        val app = allowed[packageName]
        if (app != null) {
            val previous = visits[app.packageName]
            val count = (previous?.count ?: 0) + if (current == app.packageName) 0 else 1
            visits[app.packageName] = AppVisit(app, count, timestamp)
        }
        current = app?.packageName
    }

    fun interrupt() {
        current = null
    }

    fun top(): List<AppVisit> = visits.values.sortedWith(
        compareByDescending<AppVisit> { it.count }.thenByDescending { it.lastSeen }.thenBy { it.app.packageName }
    ).take(10)
}
