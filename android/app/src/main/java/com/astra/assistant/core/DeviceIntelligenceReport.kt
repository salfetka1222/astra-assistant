package com.astra.assistant.core

/** Read-only, deliberately bounded device snapshot: no identifiers, app inventory or user content. */
data class DeviceSnapshot(
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val batteryPercent: Int?,
    val powerSaveMode: Boolean?,
    val availableRamMiB: Long?,
    val availableStorageMiB: Long?,
    val accessibilityRunning: Boolean,
    val cameraAvailable: Boolean?,
    val alarmsAvailable: Boolean?,
    val dialerAvailable: Boolean?
)

object DeviceIntelligenceReport {
    private fun availability(value: Boolean?): String = when (value) {
        true -> "доступно"
        false -> "не найдено"
        null -> "неизвестно"
    }

    fun format(snapshot: DeviceSnapshot): String = buildString {
        appendLine("Карта устройства Astra • локальная проверка")
        appendLine("Устройство: ${snapshot.model.take(90).replace('\n', ' ').replace('\r', ' ')}")
        appendLine("Android: ${snapshot.androidVersion.take(24)} (API ${snapshot.apiLevel})")
        appendLine("Батарея: ${snapshot.batteryPercent?.takeIf { it in 0..100 }?.let { "$it%" } ?: "неизвестно"}")
        appendLine("Энергосбережение: ${snapshot.powerSaveMode?.let { if (it) "включено" else "выключено" } ?: "неизвестно"}")
        appendLine("Свободная ОЗУ: ${snapshot.availableRamMiB?.takeIf { it >= 0 }?.let { "$it МиБ" } ?: "неизвестно"}")
        appendLine("Свободное место: ${snapshot.availableStorageMiB?.takeIf { it >= 0 }?.let { "$it МиБ" } ?: "неизвестно"}")
        appendLine("Astra Accessibility: ${if (snapshot.accessibilityRunning) "активна" else "не активна"}")
        appendLine("Камера: ${availability(snapshot.cameraAvailable)}")
        appendLine("Будильники: ${availability(snapshot.alarmsAvailable)}")
        appendLine("Набор номера: ${availability(snapshot.dialerAvailable)}")
        append("Действия требуют подтверждения. Запросы ChatGPT пока используют Termux Bridge.")
    }
}
