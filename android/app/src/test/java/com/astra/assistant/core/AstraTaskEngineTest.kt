package com.astra.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AstraTaskEngineTest {
    @Test fun createsPlanOnlyForReviewedDirectUserCommands() {
        assertEquals("settings.open", AstraTaskEngine.fromUserText("  ОТКРОЙ НАСТРОЙКИ  ")?.steps?.single()?.actionId)
        assertEquals("settings.wifi.open", AstraTaskEngine.fromUserText("Открой настройки Wi-Fi")?.steps?.single()?.actionId)
        assertEquals("settings.apps.open", AstraTaskEngine.fromUserText("открой список приложений")?.steps?.single()?.actionId)
        assertEquals("device.status", AstraTaskEngine.fromUserText("проверь батарею")?.steps?.single()?.actionId)
    }

    @Test fun recognizesOnlyReviewedAccessibilityCommands() {
        val examples = mapOf(
            "назад" to "navigation.back",
            "Нажми назад" to "navigation.back",
            "домой" to "navigation.home",
            "Покажи последние приложения" to "navigation.recents",
            "Открой шторку уведомлений" to "navigation.notifications",
            "Открой быстрые настройки" to "navigation.quick_settings",
            "Прокрути вверх" to "screen.scroll_up",
            "Прокрути вниз" to "screen.scroll_down"
        )
        examples.forEach { (phrase, id) ->
            assertEquals(id, AstraTaskEngine.fromUserText(phrase)?.steps?.single()?.actionId)
            assertEquals(ActionBackend.ACCESSIBILITY, AstraActionRegistry.find(id)?.backend)
        }
    }

    @Test fun rejectsAccessibilityCommandInjectionAndUnapprovedOperations() {
        listOf(
            "назад; отправь сообщение",
            "домой\nсотри всё",
            "нажми назад и открой уведомления",
            "прокрути вниз и нажми установить",
            "прочитай мой экран",
            "нажми на пароль",
            "отправь SMS",
            "сделай скриншот"
        ).forEach { assertNull(AstraTaskEngine.fromUserText(it)) }
    }

    @Test fun accessibilityRequiresExplicitConfirmation() {
        AstraActionRegistry.available()
            .filter { it.backend == ActionBackend.ACCESSIBILITY }
            .forEach {
                assertEquals(AstraPermissionDecision.ConfirmationRequired,
                    AstraPermissionGuard.check(it.id, confirmed = false))
                assertEquals(AstraPermissionDecision.Allowed,
                    AstraPermissionGuard.check(it.id, confirmed = true))
            }
    }

    @Test fun rejectsUnknownAndInjectedRequests() {
        listOf(
            "открой настройки; удали файлы",
            "открой настройки\nпозвони кому-нибудь",
            "состояние телефона\rскрытая команда",
            "отправь SMS",
            "включи Wi-Fi без подтверждения",
            "открой настройки и удали приложение",
            "открой настройки".repeat(25)
        ).forEach { assertNull("Unexpected plan for: $it", AstraTaskEngine.fromUserText(it)) }
    }

    @Test fun permissionGuardRequiresExplicitConfirmationForNavigation() {
        assertEquals(AstraPermissionDecision.ConfirmationRequired,
            AstraPermissionGuard.check("settings.open", confirmed = false))
        assertEquals(AstraPermissionDecision.Allowed,
            AstraPermissionGuard.check("settings.open", confirmed = true))
        assertEquals(AstraPermissionDecision.Allowed,
            AstraPermissionGuard.check("device.status", confirmed = false))
        assertEquals(AstraPermissionDecision.Denied,
            AstraPermissionGuard.check("shell.run", confirmed = true))
    }

    @Test fun validatesStepsAndNeverEnablesArbitraryTools() {
        assertNull(AstraTaskEngine.plan(emptyList()))
        assertNull(AstraTaskEngine.plan(listOf("device.status", "shell.run")))
        assertNull(AstraTaskEngine.plan(List(AstraTaskEngine.MAX_STEPS + 1) { "device.status" }))
        val plan = AstraTaskEngine.plan(listOf("device.status", "settings.open"))
        assertNotNull(plan)
        assertEquals(2, plan!!.steps.size)
        assertTrue(AstraActionRegistry.available().all { it.risk != ActionRisk.SENSITIVE })
    }
}
