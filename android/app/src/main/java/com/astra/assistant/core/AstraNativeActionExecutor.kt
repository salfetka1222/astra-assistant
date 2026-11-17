package com.astra.assistant.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.MediaStore
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
            ActionBackend.DEVICE_CAPABILITIES -> readNativeCapabilities()
            ActionBackend.DEVICE_MAP -> AstraExecutionResult(true, DeviceIntelligence.report(context))
            ActionBackend.SETTINGS -> openSettings(context, action)
            ActionBackend.APP_LAUNCH -> openSystemApp(context, action)
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

    private fun readNativeCapabilities(): AstraExecutionResult {
        val count = AstraActionRegistry.available().size
        val accessibility = if (AstraAccessibilityService.isRunning()) "активна" else "не подключена"
        return AstraExecutionResult(
            true,
            "Нативные возможности Astra\n" +
                "Зарегистрировано команд: $count\n" +
                "Accessibility: $accessibility\n" +
                "Без Termux: диагностика телефона, открытие системных приложений и настроек.\n" +
                "Для навигации требуется подтверждение. Для ответов ChatGPT пока нужен Termux Bridge."
        )
    }

    /** Opens a system application; never dials a number or accesses its private data. */
    private fun openSystemApp(context: Context, action: AstraAction): AstraExecutionResult {
        val intentAction = when (action.id) {
            "apps.camera.open" -> MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
            "apps.alarms.open" -> AlarmClock.ACTION_SHOW_ALARMS
            "apps.dialer.open" -> Intent.ACTION_DIAL
            else -> return AstraExecutionResult(false, "Приложение не разрешено политикой Astra.")
        }
        return try {
            val intent = Intent(intentAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) == null) {
                return AstraExecutionResult(false, "На устройстве не найдено приложение для команды: ${action.title}.")
            }
            context.startActivity(intent)
            AstraExecutionResult(true, "Запрос открыть ${action.title} передан Android. Результат на экране ещё не проверен.")
        } catch (_: ActivityNotFoundException) {
            AstraExecutionResult(false, "Не найдено приложение для команды: ${action.title}.")
        } catch (_: SecurityException) {
            AstraExecutionResult(false, "Android запретил открытие: ${action.title}.")
        } catch (_: Exception) {
            AstraExecutionResult(false, "Не удалось отправить команду открытия: ${action.title}.")
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
            AstraExecutionResult(true, "Запрос открыть ${action.title} передан Android. Результат на экране ещё не проверен.")
        } catch (_: ActivityNotFoundException) {
            AstraExecutionResult(false, "На устройстве нет обработчика для команды: ${action.title}.")
        } catch (_: SecurityException) {
            AstraExecutionResult(false, "Android запретил открытие: ${action.title}.")
        } catch (_: Exception) {
            AstraExecutionResult(false, "Не удалось отправить команду открытия: ${action.title}.")
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
