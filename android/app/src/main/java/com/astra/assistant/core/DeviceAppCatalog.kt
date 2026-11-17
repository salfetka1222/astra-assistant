package com.astra.assistant.core

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

/**
 * Only discovers launcher-visible apps, when the owner explicitly asks.
 * Stores a bounded directory inside private app storage; no syncing or network calls.
 */
object DeviceAppCatalog {
    private const val PREFS = "astra_local_app_directory"
    private const val KEY = "snapshot_v1"

    fun scanAndRemember(context: Context): AppCatalogSnapshot {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = pm.queryIntentActivities(launcher, 0).mapNotNull { info ->
            val name = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = runCatching { info.loadLabel(pm)?.toString() }.getOrNull() ?: name
            AppCatalogEntry(name, label)
        }
        val (apps, truncated) = AppCatalog.normalize(found)
        val snapshot = AppCatalogSnapshot(System.currentTimeMillis(), apps, truncated)
        val serialized = JSONObject().apply {
            put("time", snapshot.scannedAtMillis)
            put("truncated", snapshot.truncated)
            put("apps", JSONArray().apply {
                apps.forEach { item ->
                    put(JSONObject().put("package", item.packageName).put("label", item.label))
                }
            })
        }.toString()
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, serialized).commit()) { "Не удалось сохранить карту приложений." }
        return snapshot
    }

    fun load(context: Context): AppCatalogSnapshot? = runCatching {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        val data = JSONObject(stored)
        val arr = data.getJSONArray("apps")
        // Reject malformed/oversized data rather than trust a corrupted local cache.
        require(arr.length() <= AppCatalog.MAX_ENTRIES)
        val items = (0 until arr.length()).map { index ->
            val item = arr.getJSONObject(index)
            AppCatalogEntry(item.getString("package"), item.getString("label"))
        }
        val (apps, discarded) = AppCatalog.normalize(items)
        require(!discarded && apps.size == items.size)
        AppCatalogSnapshot(data.getLong("time"), apps, data.optBoolean("truncated"))
    }.getOrNull()

    fun clear(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().remove(KEY).commit()
}
