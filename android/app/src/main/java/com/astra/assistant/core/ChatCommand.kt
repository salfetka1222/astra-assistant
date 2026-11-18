package com.astra.assistant.core

/** Resolves only text submitted by the owner. Catalog commands never need the chat backend. */
sealed interface ChatCommand {
    data class Planned(val plan: AstraTaskPlan) : ChatCommand
    data class Feedback(val message: String) : ChatCommand

    companion object {
        private val openApp = Regex("""(?:открой|запусти)\s+(?:приложение\s+)?(.+)""", RegexOption.IGNORE_CASE)

        fun resolve(text: String, catalog: AppCatalogSnapshot?): ChatCommand? {
            AstraTaskEngine.fromUserText(text)?.let { return Planned(it) }
            if (text.length > 230 || text.any { it.isISOControl() }) return null
            val query = openApp.matchEntire(text.trim())?.groupValues?.get(1)?.trim() ?: return null
            val apps = catalog?.apps ?: return Feedback(
                "Сначала открой «Инструменты → Каталог приложений» и нажми «Изучить приложения»."
            )
            val byPackage = apps.singleOrNull { it.packageName == query }
            val matches = if (byPackage != null) listOf(byPackage) else apps.filter { it.label.equals(query, ignoreCase = true) }
            return when (matches.size) {
                0 -> Feedback("В каталоге нет приложения с таким названием. Проверь название в «Инструментах» или обнови каталог.")
                1 -> Planned(AstraTaskPlan(listOf(AstraTaskStep("apps.catalog.open", matches.single()))))
                else -> Feedback("Так называются несколько приложений. Отправь «открой» и точный идентификатор из каталога.\n" +
                    matches.take(5).joinToString("\n") { it.packageName })
            }
        }
    }
}
