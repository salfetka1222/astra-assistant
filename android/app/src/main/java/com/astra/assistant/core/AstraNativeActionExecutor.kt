package com.astra.assistant.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings

data class AstraExecutionResult(val success: Boolean, val message: String)

/**
 * Act -> Verify. Only registered, one-step native actions are executable in 2.0.
 * Navigation must be called after the explicit confirmation button is pressed.
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
        if (step.actionId == "device.status") return readDeviceStatus(context)

        val intentAction = when (step.actionId) {
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
