package com.astra.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.astra.assistant.core.OneShotResult
import java.lang.ref.WeakReference

/**
 * User-initiated Android navigation only. Does not retrieve screen text or touch targets.
 * All requests come from the app UI or its explicit manual control panel.
 */
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

    private fun execute(action: String, callback: (Boolean) -> Unit) {
        when (action) {
            "back" -> callback(performGlobalAction(GLOBAL_ACTION_BACK))
            "home" -> callback(performGlobalAction(GLOBAL_ACTION_HOME))
            "recents" -> callback(performGlobalAction(GLOBAL_ACTION_RECENTS))
            "notifications" -> callback(performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS))
            "quick_settings" -> callback(performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS))
            "scroll_up", "scroll_down" -> swipe(action == "scroll_up", callback)
            else -> callback(false)
        }
    }

    private fun swipe(up: Boolean, callback: (Boolean) -> Unit) {
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
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    callback(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription) {
                    callback(false)
                }
            },
            Handler(Looper.getMainLooper())
        )
        if (!dispatched) callback(false)
    }

    companion object {
        const val PREFS_NAME = "astra_accessibility_status"
        const val ACCESSIBILITY_CURRENT_PACKAGE = "current_package"
        const val ACCESSIBILITY_LAST_EVENT_AT = "last_event_at"

        @Volatile
        private var activeService: WeakReference<AstraAccessibilityService>? = null

        fun isRunning(): Boolean = activeService?.get() != null

        /** Requires a direct user interaction; also used by the existing manual control panel. */
        fun performManualAction(action: String, callback: (Boolean) -> Unit) {
            val handler = Handler(Looper.getMainLooper())
            val once = OneShotResult(callback)
            // Accessibility may disconnect mid-gesture. Never leave the chat waiting forever.
            val timeout = Runnable { once.deliver(false) }
            val finish: (Boolean) -> Unit = { accepted ->
                handler.removeCallbacks(timeout)
                once.deliver(accepted)
            }
            handler.postDelayed(timeout, 6_000L)
            handler.post {
                val service = activeService?.get()
                if (service == null) {
                    finish(false)
                } else {
                    try {
                        service.execute(action, finish)
                    } catch (_: Exception) {
                        finish(false)
                    }
                }
            }
        }
    }
}
