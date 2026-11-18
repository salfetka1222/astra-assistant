package com.astra.assistant.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astra.assistant.ENABLED
import com.astra.assistant.PENDING
import com.astra.assistant.VOICE_PREFS
import com.astra.assistant.core.AstraActionRegistry
import com.astra.assistant.core.AstraNativeActionExecutor
import com.astra.assistant.core.AstraTaskEngine
import com.astra.assistant.core.VoiceProposal
import kotlinx.coroutines.delay

@Composable
internal fun VoiceProposalPanel(showSettings: Boolean, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf<String?>(null) }

    DisposableEffect(prefs) {
        fun refresh() {
            enabled = prefs.getBoolean(ENABLED, false)
            raw = prefs.getString(PENDING, null)
        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        refresh()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val proposal = if (enabled) VoiceProposal.decode(raw, System.currentTimeMillis()) else null
    LaunchedEffect(raw) {
        val token = raw ?: return@LaunchedEffect
        if (proposal != null) delay((proposal.createdAt + VoiceProposal.LIFETIME_MS - System.currentTimeMillis()).coerceAtLeast(0))
        if (prefs.getString(PENDING, null) == token) prefs.edit().remove(PENDING).apply()
    }
    if (proposal != null) {
        val token = proposal.encode()
        val action = AstraActionRegistry.find(proposal.key.actionId) ?: return
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Внешняя команда", style = MaterialTheme.typography.labelLarge)
                Text("Открыть ${action.title}?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val current = prefs.getString(PENDING, null)
                        if (!prefs.getBoolean(ENABLED, false) || current != token ||
                            VoiceProposal.decode(current, System.currentTimeMillis()) == null) return@Button
                        if (!prefs.edit().remove(PENDING).commit()) return@Button
                        val plan = AstraTaskEngine.plan(listOf(action.id)) ?: return@Button
                        onResult(AstraNativeActionExecutor.execute(context, plan, confirmed = true).message)
                    }) { Text("Открыть") }
                    TextButton(onClick = {
                        if (prefs.getString(PENDING, null) == token) prefs.edit().remove(PENDING).apply()
                    }) { Text("Отмена") }
                }
            }
        }
    } else if (showSettings) {
        TextButton(onClick = { prefs.edit().putBoolean(ENABLED, !enabled).remove(PENDING).apply() }) {
            Text(if (enabled) "Отключить внешние голосовые команды" else "Разрешить внешние голосовые команды")
        }
    }
}
