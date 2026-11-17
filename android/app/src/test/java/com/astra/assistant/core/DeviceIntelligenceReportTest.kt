package com.astra.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIntelligenceReportTest {
    @Test fun deviceMapIsReadOnlyAndRecognizedOnlyFromUserCommands() {
        val plan = AstraTaskEngine.fromUserText("ИЗУЧИ МОЙ ТЕЛЕФОН")
        assertEquals("device.map", plan?.steps?.single()?.actionId)
        assertEquals("device.map", AstraTaskEngine.fromUserText("карта устройства")?.steps?.single()?.actionId)
        assertEquals(AstraPermissionDecision.Allowed, AstraPermissionGuard.check("device.map", false))
        assertEquals(ActionBackend.DEVICE_MAP, AstraActionRegistry.find("device.map")?.backend)
        assertNull(AstraTaskEngine.fromUserText("изучи мой телефон и отправь данные"))
        assertNull(AstraTaskEngine.fromUserText("изучи мой телефон\nудали файлы"))
    }

    @Test fun printsOnlyBoundedCapabilitiesAndSystemValues() {
        val snapshot = DeviceSnapshot(
            model = "Vendor Model", androidVersion = "16", apiLevel = 36,
            batteryPercent = 82, powerSaveMode = false,
            availableRamMiB = 1280, availableStorageMiB = 51200,
            accessibilityRunning = true,
            cameraAvailable = true, alarmsAvailable = false, dialerAvailable = null
        )
        val result = DeviceIntelligenceReport.format(snapshot)
        assertTrue(result.contains("Android: 16 (API 36)"))
        assertTrue(result.contains("Батарея: 82%"))
        assertTrue(result.contains("Камера: доступно"))
        assertTrue(result.contains("Будильники: не найдено"))
        assertTrue(result.contains("Набор номера: неизвестно"))
        assertTrue(result.contains("Astra Accessibility: активна"))
        assertFalse(result.contains("IMEI"))
    }

    @Test fun missingOrUnexpectedValuesNeverPretendToBeValid() {
        val snapshot = DeviceSnapshot(
            model = "Untrusted\nLabel", androidVersion = "unknown", apiLevel = 35,
            batteryPercent = 900, powerSaveMode = null,
            availableRamMiB = -1, availableStorageMiB = null,
            accessibilityRunning = false,
            cameraAvailable = null, alarmsAvailable = null, dialerAvailable = null
        )
        val result = DeviceIntelligenceReport.format(snapshot)
        assertTrue(result.contains("Устройство: Untrusted Label"))
        assertTrue(result.contains("Батарея: неизвестно"))
        assertTrue(result.contains("Свободная ОЗУ: неизвестно"))
        assertTrue(result.contains("Энергосбережение: неизвестно"))
        assertFalse(result.contains("900%"))
    }
}
