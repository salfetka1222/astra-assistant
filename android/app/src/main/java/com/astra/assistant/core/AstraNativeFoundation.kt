package com.astra.assistant.core

import java.util.Locale

/** Astra 2.0: a closed allowlist. AI text never grants device permissions. */
enum class ActionRisk { READ_ONLY, NAVIGATION, SENSITIVE }

data class AstraAction(
    val id: String,
    val title: String,
    val risk: ActionRisk,
    val requiresConfirmation: Boolean
)

object AstraActionRegistry {
    private val actions = listOf(
        AstraAction("device.status", "состояние телефона", ActionRisk.READ_ONLY, false),
        AstraAction("settings.open", "настройки Android", ActionRisk.NAVIGATION, true),
        AstraAction("settings.wifi.open", "настройки Wi-Fi", ActionRisk.NAVIGATION, true),
        AstraAction("settings.display.open", "настройки экрана", ActionRisk.NAVIGATION, true),
        AstraAction("settings.sound.open", "настройки звука", ActionRisk.NAVIGATION, true),
        AstraAction("settings.apps.open", "список приложений", ActionRisk.NAVIGATION, true)
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
        // Sensitive operations will require a separate capability-specific policy.
        if (action.risk == ActionRisk.SENSITIVE) return AstraPermissionDecision.Denied
        return if (action.requiresConfirmation && !confirmed) {
            AstraPermissionDecision.ConfirmationRequired
        } else {
            AstraPermissionDecision.Allowed
        }
    }
}

/**
 * Understand -> Plan. Execution and user confirmation happen separately.
 * No free-form instructions, AI responses or multiple commands are interpreted.
 */
object AstraTaskEngine {
    const val MAX_STEPS = 8
    private val statusPhrases = setOf(
        "проверь батарею", "заряд батареи", "диагностика", "состояние телефона",
        "покажи состояние телефона", "проверь телефон"
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
            ?: if (normalized in statusPhrases) "device.status" else return null
        return plan(listOf(actionId))
    }
}
