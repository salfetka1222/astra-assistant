package com.astra.assistant.core

/** The external helper can propose a destination, never an Intent or executable text. */
internal enum class VoiceShortcut(val key: String, val actionId: String) {
    WIFI("wifi", "settings.wifi.open"),
    DISPLAY("display", "settings.display.open"),
    SOUND("sound", "settings.sound.open"),
    SETTINGS("settings", "settings.open"),
    CAMERA("camera", "apps.camera.open"),
    ALARMS("alarms", "apps.alarms.open");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}

internal data class VoiceProposal(val key: VoiceShortcut, val createdAt: Long, val nonce: String) {
    fun encode() = "${key.key}|${createdAt}|${nonce}"

    companion object {
        const val LIFETIME_MS = 120_000L

        fun decode(value: String?, now: Long): VoiceProposal? {
            val parts = value?.split('|', limit = 3) ?: return null
            if (parts.size != 3 || parts[2].length != 36) return null
            val key = VoiceShortcut.fromKey(parts[0]) ?: return null
            val created = parts[1].toLongOrNull() ?: return null
            if (created !in (now - LIFETIME_MS + 1)..now) return null
            return VoiceProposal(key, created, parts[2])
        }
    }
}
