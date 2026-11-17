package com.astra.assistant.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * One transient, user-reviewed action. The token is never persisted or reused.
 * The UI may deliver rapid taps or stale callbacks during recomposition.
 */
class AstraConfirmationToken(val plan: AstraTaskPlan) {
    private val consumed = AtomicBoolean(false)

    /** Only the first confirm OR cancellation can consume this request. */
    fun consume(): Boolean = consumed.compareAndSet(false, true)
}
