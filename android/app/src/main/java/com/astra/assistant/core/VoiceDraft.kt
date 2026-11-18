package com.astra.assistant.core

internal object VoiceDraft {
    private val address = Regex("""^астра[,!:.]?\s+""", RegexOption.IGNORE_CASE)

    fun append(draft: String, speech: String, catalog: AppCatalogSnapshot?): String? {
        if (speech.isBlank() || speech.length > 3000) return null
        val original = speech.trim()
        val candidate = original.replaceFirst(address, "").trimEnd('.', '!', '?').trimEnd()
        val phrase = if (draft.isBlank() && ChatCommand.resolve(candidate, catalog) is ChatCommand.Planned) {
            candidate
        } else original
        val result = if (draft.isBlank()) phrase else draft.trimEnd() + " " + phrase
        return result.takeIf { it.length <= 3000 }
    }
}
