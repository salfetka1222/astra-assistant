package com.astra.assistant.core

/** Offline command hints. Text never triggers an Android action or a shell command. */
internal object CommandInterpreter {
    fun interpret(input: String): String? {
        val text = input.trim().lowercase().replace(Regex("\\s+"), " ")
        if (text.length > 100 || text.contains('\n')) return null
        return when {
            text in setOf("что ты умеешь", "команды", "список команд", "помощь по командам") ->
                "Умею открывать настройки Android, проверять батарею и память, а через Accessibility — переходить домой, назад, открывать уведомления и прокручивать экран. Команды Android требуют подтверждения. Для диалога с ChatGPT пока нужен Termux Bridge."
            text in setOf("открой настройки", "настройки android", "открыть настройки", "запусти настройки") ->
                "Открой «Инструменты → Мост Termux → Открыть: Настройки Android». Из чата приложение не запускается автоматически."
            text in setOf("покажи файлы", "список файлов", "файлы termux", "что в workspace") ->
                "Запрашиваю список файлов workspace через локальный Termux Bridge. Нужен работающий мост и токен."
            text in setOf("проверь батарею", "заряд батареи", "диагностика", "состояние телефона", "покажи состояние телефона", "проверь телефон") ->
                "Открой «Инструменты → Мост Termux → Батарея, ОЗУ и память». Показатели будут прочитаны с устройства после нажатия кнопки."
            text in setOf("проверь подключение", "статус termux", "проверить мост", "диагностика подключения", "termux не работает") ->
                "Проверяю подключение Termux Bridge. Если мост недоступен, проверь bridge.py и токен."
            text in setOf("включи accessibility", "настрой accessibility", "специальные возможности") ->
                "Открой «Инструменты → Управление Android → Настроить» и вручную включи службу Astra Accessibility."
            text in setOf("что нового", "изменения", "история обновлений") ->
                "Открой «Настройки → Что нового в Astra», чтобы увидеть изменения последнего выпуска."
            else -> null
        }
    }
}
