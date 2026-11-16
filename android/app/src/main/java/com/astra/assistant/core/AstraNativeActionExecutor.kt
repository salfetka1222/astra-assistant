package com.astra.assistant.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import com.astra.assistant.AstraAccessibilityService

data class AstraExecutionResult(val success: Boolean, val message: String)

/**
 * Act -> Verify. One step at a time. Device commands never run from AI responses.
 * Settings intents and accessibility actions are separate, tightly controlled backends.
 */
object AstraNativeActionExecutor {
    fun execute(context: Context, plan: AstraTaskPlan, confirmed: Boolean): AstraExecutionResult {
        val step = plan.steps.singleOrNull()
            ?: return AstraExecutionResult(false, "Многошаговое выполнение пока недоступно.")
        val action = AstraActionRegistry.find(step.actionId)
            ?: return AstraExecutionResult(false, "Неизвестное действие Astra.")
        when (AstraPermissionGuard.check(step.actionId, confirmed)) {
            AstraPermissionDecision.Denied ->
                return AstraExecutionResult(false, "Это действие запрещено политикой Astra.")
            AstraPermissionDecision.ConfirmationRequired ->
                return AstraExecutionResult(false, "Сначала подтверди действие в приложении.")
            AstraPermissionDecision.Allowed -> Unit
        }
        return when (action.backend) {
            ActionBackend.DEVICE_STATUS -> readDeviceStatus(context)
            ActionBackend.SETTINGS -> openSettings(context, action)
            ActionBackend.ACCESSIBILITY ->
                AstraExecutionResult(false, "Для этой команды требуется исполнитель Accessibility.")
        }
    }

    /** Asynchronous: global actions and gestures are processed by the Android Accessibility service. */
    fun executeAccessibility(
        plan: AstraTaskPlan,
        confirmed: Boolean,
        onResult: (AstraExecutionResult) -> Unit
    ) {
        val actionId = plan.steps.singleOrNull()?.actionId
        val action = actionId?.let(AstraActionRegistry::find)
        if (action?.backend != ActionBackend.ACCESSIBILITY) {
            onResult(AstraExecutionResult(false, "Недопустимая команда Accessibility."))
            return
        }
        when (AstraPermissionGuard.check(action.id, confirmed)) {
            AstraPermissionDecision.Denied -> {
                onResult(AstraExecutionResult(false, "Действие запрещено политикой Astra."))
                return
            }
            AstraPermissionDecision.ConfirmationRequired -> {
                onResult(AstraExecutionResult(false, "Сначала подтверди действие в чате."))
                return
            }
            AstraPermissionDecision.Allowed -> Unit
        }
        val serviceAction = when (action.id) {
            "navigation.back" -> "back"
            "navigation.home" -> "home"
            "navigation.recents" -> "recents"
            "navigation.notifications" -> "notifications"
            "navigation.quick_settings" -> "quick_settings"
            "screen.scroll_up" -> "scroll_up"
            "screen.scroll_down" -> "scroll_down"
            else -> null
        }
        if (serviceAction == null) {
            onResult(AstraExecutionResult(false, "Неизвестная команда Android."))
            return
        }
        if (!AstraAccessibilityService.isRunning()) {
            onResult(AstraExecutionResult(
                false,
                "Служба Astra Accessibility не активна. Включи её в «Инструменты → Управление Android»."
            ))
            return
        }
        AstraAccessibilityService.performManualAction(serviceAction) { accepted ->
            onResult(AstraExecutionResult(
                accepted,
                if (accepted) "Android принял команду: ${action.title}."
                else "Android не смог выполнить команду: ${action.title}."
            ))
        }
    }

    private fun openSettings(context: Context, action: AstraAction): AstraExecutionResult {
        val intentAction = when (action.id) {
            "settings.open" -> Settings.ACTION_SETTINGS
            "settings.wifi.open" -> Settings.ACTION_WIFI_SETTINGS
            "settings.display.open" -> Settings.ACTION_DISPLAY_SETTINGS
            "settings.sound.open" -> Settings.ACTION_SOUND_SETTINGS
            "settings.apps.open" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> return AstraExecutionResult(false, "Действие не поддерживается устройством.")
        }
        return try {
            context.startActivity(Intent(intentAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            AstraExecutionResult(true, "Команда отправлена Android: открыть ${action.title}.")
        } catch (_: Exception) {
            AstraExecutionResult(false, "Не удалось открыть ${action.title}. Android отклонил действие.")
        }
    }

    private fun readDeviceStatus(context: Context): AstraExecutionResult {
        return try {
            val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memory = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(memory)
            val storage = StatFs(Environment.getDataDirectory().absolutePath)
            val mib = 1024L * 1024L
            val percentText = if (percent in 0..100) "$percent%" else "недоступно"
            AstraExecutionResult(
                true,
                "Состояние телефона\nБатарея: $percentText\n" +
                    "Свободная ОЗУ: ${memory.availMem / mib} МиБ\n" +
                    "Свободное место: ${storage.availableBytes / mib} МиБ"
            )
        } catch (_: Exception) {
            AstraExecutionResult(false, "Не удалось получить показатели устройства.")
        }
    }
}
