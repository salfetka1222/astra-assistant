package com.astra.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/** Manual-only Android controls. No screen content capture or background automation. */
class AstraAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = WeakReference(this)
    }

    override fun onDestroy() {
        if (activeService?.get() === this) activeService = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank()) return
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(ACCESSIBILITY_CURRENT_PACKAGE, packageName)
            .putLong(ACCESSIBILITY_LAST_EVENT_AT, System.currentTimeMillis())
            .apply()
    }

    override fun onInterrupt() = Unit

    private fun execute(action: String): Boolean = when (action) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        "scroll_up", "scroll_down" -> swipe(action == "scroll_up")
        else -> false
    }

    private fun swipe(up: Boolean): Boolean {
        val display = resources.displayMetrics
        val x = display.widthPixels * 0.5f
        val top = display.heightPixels * 0.32f
        val bottom = display.heightPixels * 0.72f
        val path = Path().apply {
            moveTo(x, if (up) bottom else top)
            lineTo(x, if (up) top else bottom)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        const val PREFS_NAME = "astra_accessibility_status"
        const val ACCESSIBILITY_CURRENT_PACKAGE = "current_package"
        const val ACCESSIBILITY_LAST_EVENT_AT = "last_event_at"
        private var activeService: WeakReference<AstraAccessibilityService>? = null

        /** Called only from a direct button press in Astra's local UI. */
        fun performManualAction(action: String, callback: (Boolean) -> Unit) {
            Handler(Looper.getMainLooper()).post {
                val service = activeService?.get()
                callback(service?.execute(action) ?: false)
            }
        }
    }
}
