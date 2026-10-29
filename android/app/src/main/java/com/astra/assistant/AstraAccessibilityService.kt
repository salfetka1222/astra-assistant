package com.astra.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * User-enabled accessibility integration scaffold.
 *
 * Privacy-first MVP: observes only window state changes and stores the current
 * package name locally. It does not read view text, take screenshots, click,
 * type, or transmit anything over the network.
 */
class AstraAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank()) return

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(ACCESSIBILITY_CURRENT_PACKAGE, packageName)
            .putLong(ACCESSIBILITY_LAST_EVENT_AT, System.currentTimeMillis())
            .apply()
    }

    override fun onInterrupt() {
        // No ongoing gestures or capture sessions to interrupt in this scaffold.
    }

    companion object {
        const val PREFS_NAME = "astra_accessibility_status"
        const val ACCESSIBILITY_CURRENT_PACKAGE = "current_package"
        const val ACCESSIBILITY_LAST_EVENT_AT = "last_event_at"
    }
}
