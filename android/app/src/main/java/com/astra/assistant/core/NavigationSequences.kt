package com.astra.assistant.core

/** Fixed navigation recipes; no user-provided or model-generated action lists. */
internal object NavigationSequences {
    private val recipes = mapOf(
        "open_quick_settings" to listOf("notifications", "quick_settings"),
        "return_home" to listOf("back", "home")
    )

    fun steps(recipeId: String): List<String>? = recipes[recipeId]?.toList()
}
