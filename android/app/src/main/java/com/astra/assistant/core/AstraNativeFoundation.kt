package com.astra.assistant.core

/**
 * Pure Kotlin foundation for native Astra actions.
 * Planning does not grant permissions and never executes device operations.
 */
enum class ActionRisk { READ_ONLY, NAVIGATION, SENSITIVE }

data class AstraAction(
    val id: String,
    val risk: ActionRisk,
    val requiresConfirmation: Boolean
)

object AstraActionRegistry {
    private val actions = listOf(
        AstraAction("device.status", ActionRisk.READ_ONLY, false),
        AstraAction("settings.open", ActionRisk.NAVIGATION, true),
        AstraAction("settings.wifi.open", ActionRisk.NAVIGATION, true),
        AstraAction("settings.display.open", ActionRisk.NAVIGATION, true),
        AstraAction("settings.sound.open", ActionRisk.NAVIGATION, true),
        AstraAction("settings.apps.open", ActionRisk.NAVIGATION, true)
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
    /** Only known actions are eligible. A caller cannot bypass confirmation. */
    fun check(actionId: String, confirmed: Boolean): AstraPermissionDecision {
        val action = AstraActionRegistry.find(actionId) ?: return AstraPermissionDecision.Denied
        return if (action.requiresConfirmation && !confirmed) {
            AstraPermissionDecision.ConfirmationRequired
        } else {
            AstraPermissionDecision.Allowed
        }
    }
}

/** Plans are bounded, registry-validated, and never run automatically. */
object AstraTaskEngine {
    const val MAX_STEPS = 8

    fun plan(actionIds: List<String>): AstraTaskPlan? {
        if (actionIds.isEmpty() || actionIds.size > MAX_STEPS) return null
        if (actionIds.any { AstraActionRegistry.find(it) == null }) return null
        return AstraTaskPlan(actionIds.map(::AstraTaskStep))
    }
}
