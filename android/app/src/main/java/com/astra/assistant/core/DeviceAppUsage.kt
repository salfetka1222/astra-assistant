package com.astra.assistant.core

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.TimeUnit

internal object DeviceAppUsage {
    @Suppress("DEPRECATION")
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        return try {
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
                AppOpsManager.MODE_ALLOWED
        } catch (_: SecurityException) {
            false
        }
    }

    /** Called from IO after a tap. Discard events as soon as their counters are updated. */
    @Suppress("DEPRECATION")
    suspend fun read(context: Context, catalog: List<AppCatalogEntry>): List<AppVisit>? {
        if (!hasAccess(context)) return null
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.DAYS.toMillis(1)
        val events = if (Build.VERSION.SDK_INT >= 35) {
            manager.queryEvents(UsageEventsQuery.Builder(start, end).setEventTypes(
                UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN
            ).build())
        } else {
            manager.queryEvents(start, end)
        } ?: return null

        val totals = AppVisits(catalog)
        val event = UsageEvents.Event()
        val jobContext = currentCoroutineContext()
        while (events.getNextEvent(event)) {
            jobContext.ensureActive()
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> totals.foreground(event.packageName, event.timeStamp)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.DEVICE_SHUTDOWN -> totals.interrupt()
            }
        }
        return if (hasAccess(context)) totals.top() else null
    }
}
