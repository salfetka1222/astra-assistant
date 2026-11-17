package com.astra.assistant.core

import java.util.Locale

/** A closed allowlist: natural language can select actions, never create new permissions. */
enum class ActionRisk { READ_ONLY, NAVIGATION, SENSITIVE }
enum class ActionBackend { DEVICE_STATUS, DEVICE_CAPABILITIES, SETTINGS, APP_LAUNCH, ACCESSIBILITY }

data class AstraAction(
    val id: String,
    val title: String,
    val risk: ActionRisk,
    val requiresConfirmation: Boolean,
    val backend: ActionBackend
)

object AstraActionRegistry {
    private val actions = listOf(
        AstraAction("device.status", "состояние телефона", ActionRisk.READ_ONLY, false, ActionBackend.DEVICE_STATUS),
        AstraAction("device.capabilities", "возможности Astra", ActionRisk.READ_ONLY, false, ActionBackend.DEVICE_CAPABILITIES),
        AstraAction("apps.camera.open", "камеру", ActionRisk.NAVIGATION, true, ActionBackend.APP_LAUNCH),
        AstraAction("apps.alarms.open", "будильники", ActionRisk.NAVIGATION, true, ActionBackend.APP_LAUNCH),
        AstraAction("apps.dialer.open", "набор номера", ActionRisk.NAVIGATION, true, ActionBackend.APP_LAUNCH),
        AstraAction("settings.open", "настройки Android", ActionRisk.NAVIGATION, true, ActionBackend.SETTINGS),
        AstraAction("settings.wifi.open", "настройки Wi-Fi", ActionRisk.NAVIGATION, true, ActionBackend.SETTINGS),
        AstraAction("settings.display.open", "настройки экрана", ActionRisk.NAVIGATION, true, ActionBackend.SETTINGS),
        AstraAction("settings.sound.open", "настройки звука", ActionRisk.NAVIGATION, true, ActionBackend.SETTINGS),
        AstraAction("settings.apps.open", "список приложений", ActionRisk.NAVIGATION, true, ActionBackend.SETTINGS),
        AstraAction("navigation.back", "Назад", ActionRisk.NAVIGATION, true, ActionBackend.ACCESSIBILITY),
        AstraAction("navigation.home", "Главный экран", ActionRisk.NAVIGATION, true, ActionBackend.ACCESSIBILITY),
        AstraAction("navigation.recents", "Недавние приложения", ActionRisk.NAVIGATION, true, ActionBackend.ACCESSIBILITY),
        AstraAction("navigation.notifications", "Панель уведомлений", ActionRisk.NAVIGATION, true, ActionBackend.ACCESSIBILITY),
        AstraAction("navigation.quick_settings", "Панель быстрых настроек", ActionRisk.NAVIGATION, true, ActionBackend.ACCESSIBILITY),
        AstraAction("screen.scroll_up", "Прокрутка вверх", ActionRisk.NAVIGATION, true, ActionBackend.ACCESSIBILITY),
        AstraAction("screen.scroll_down", "Прокрутка вниз", ActionRisk.NAVIGATION, true, ActionBackend.ACCESSIBILITY)
    ).associateBy { it.id }

    fun find(id: String): AstraAction? = actions[id]
    fun available(): List<AstraAction> = actions.values.toList()
}

data class AstraTaskStep(val actionId: String)
data class AstraTaskPlan(val steps: List<AstraTaskStep>)

sealed class AstraPermissionDecision {
    data object Denied : AstraPermissionDecision()
    data object ConfirmationRequired : AstraPermissionDecision()
    data object Allowed : AstraPermissionDecision()
}

object AstraPermissionGuard {
    fun check(actionId: String, confirmed: Boolean): AstraPermissionDecision {
        val action = AstraActionRegistry.find(actionId) ?: return AstraPermissionDecision.Denied
        // Sensitive operations require a dedicated capability-specific policy.
        if (action.risk == ActionRisk.SENSITIVE) return AstraPermissionDecision.Denied
        return if (action.requiresConfirmation && !confirmed) {
            AstraPermissionDecision.ConfirmationRequired
        } else {
            AstraPermissionDecision.Allowed
        }
    }
}

/**
 * Understand -> Plan only. AI-generated responses are never parsed as device commands.
 * Multistep plans may be validated but are not executable in this preview.
 */
object AstraTaskEngine {
    const val MAX_STEPS = 8

    private val statusPhrases = setOf(
        "проверь батарею", "заряд батареи", "диагностика", "состояние телефона",
        "покажи состояние телефона", "проверь телефон"
    )
    private val nativePhrases = mapOf(
        "что ты умеешь на телефоне" to "device.capabilities",
        "покажи возможности" to "device.capabilities",
        "какие команды доступны" to "device.capabilities",
        "открой камеру" to "apps.camera.open",
        "запусти камеру" to "apps.camera.open",
        "открой будильник" to "apps.alarms.open",
        "покажи будильники" to "apps.alarms.open",
        "открой телефон" to "apps.dialer.open",
        "открой набор номера" to "apps.dialer.open"
    )
    private val accessibilityPhrases = mapOf(
        "назад" to "navigation.back",
        "нажми назад" to "navigation.back",
        "вернись назад" to "navigation.back",
        "домой" to "navigation.home",
        "на главный экран" to "navigation.home",
        "открой главный экран" to "navigation.home",
        "последние приложения" to "navigation.recents",
        "покажи последние приложения" to "navigation.recents",
        "открой недавние приложения" to "navigation.recents",
        "открой уведомления" to "navigation.notifications",
        "покажи уведомления" to "navigation.notifications",
        "открой шторку уведомлений" to "navigation.notifications",
        "открой быстрые настройки" to "navigation.quick_settings",
        "покажи быстрые настройки" to "navigation.quick_settings",
        "открой шторку быстрых настроек" to "navigation.quick_settings",
        "прокрути вверх" to "screen.scroll_up",
        "прокрутить вверх" to "screen.scroll_up",
        "прокрути вниз" to "screen.scroll_down",
        "прокрутить вниз" to "screen.scroll_down"
    )

    fun plan(actionIds: List<String>): AstraTaskPlan? {
        if (actionIds.isEmpty() || actionIds.size > MAX_STEPS) return null
        if (actionIds.any { AstraActionRegistry.find(it) == null }) return null
        return AstraTaskPlan(actionIds.map(::AstraTaskStep))
    }

    fun fromUserText(input: String): AstraTaskPlan? {
        if (input.length > 100 || input.any { it == '\n' || it == '\r' }) return null
        val normalized = input.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val actionId = AndroidCommandParser.parse(input)?.actionId
            ?: nativePhrases[normalized]
            ?: accessibilityPhrases[normalized]
            ?: if (normalized in statusPhrases) "device.status" else return null
        return plan(listOf(actionId))
    }
}
