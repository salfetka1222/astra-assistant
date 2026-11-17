package com.astra.assistant.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android gesture dispatch may fail immediately and also report a later cancellation.
 * Never deliver multiple results for one user-confirmed command.
 */
internal class OneShotResult(private val onResult: (Boolean) -> Unit) {
    private val completed = AtomicBoolean(false)

    fun deliver(success: Boolean) {
        if (completed.compareAndSet(false, true)) onResult(success)
    }
}
