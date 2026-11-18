package com.astra.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.astra.assistant.core.VoiceProposal
import com.astra.assistant.core.VoiceShortcut
import java.util.UUID

internal const val VOICE_PREFS = "astra_voice_proposals"
internal const val ENABLED = "enabled"
internal const val PENDING = "pending"

class VoiceProposalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.astra.assistant.PROPOSE_VOICE") return
        val prefs = context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ENABLED, false)) return
        val key = VoiceShortcut.fromKey(intent.getStringExtra("shortcut")) ?: return
        val proposal = VoiceProposal(key, System.currentTimeMillis(), UUID.randomUUID().toString())
        prefs.edit().putString(PENDING, proposal.encode()).apply()
    }
}
