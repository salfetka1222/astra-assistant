package com.astra.assistant.core

import java.util.Locale

/** Package identifiers and user-visible launcher labels only; never app content. */
data class AppCatalogEntry(val packageName: String, val label: String)

data class AppCatalogSnapshot(
    val scannedAtMillis: Long,
    val apps: List<AppCatalogEntry>,
    val truncated: Boolean
)

object AppCatalog {
    const val MAX_ENTRIES = 500
    private val packagePattern = Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$")

    fun normalize(entries: List<AppCatalogEntry>): Pair<List<AppCatalogEntry>, Boolean> {
        val unique = entries.asSequence()
            .filter { it.packageName.length in 3..200 && packagePattern.matches(it.packageName) }
            .map {
                AppCatalogEntry(
                    packageName = it.packageName,
                    label = it.label.trim().replace(Regex("[\\r\\n\\t]"), " ").take(90)
                        .ifBlank { it.packageName }
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<AppCatalogEntry> { it.label.lowercase(Locale.ROOT) }
                .thenBy { it.packageName })
            .toList()
        return unique.take(MAX_ENTRIES) to (unique.size > MAX_ENTRIES)
    }

    fun search(entries: List<AppCatalogEntry>, query: String): List<AppCatalogEntry> {
        val term = query.trim().lowercase(Locale.ROOT).take(80)
        if (term.isEmpty()) return entries.take(20)
        return entries.asSequence()
            .filter { it.label.lowercase(Locale.ROOT).contains(term) ||
                it.packageName.lowercase(Locale.ROOT).contains(term) }
            .take(20).toList()
    }
}
