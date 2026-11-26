package com.github.khopland.emberkai.ui

import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.github.khopland.emberkai.settings.IconRuleCategory
import com.github.khopland.emberkai.settings.SettingsPreset
import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

internal class WelcomeNotificationTest : BasePlatformTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null

    override fun setUp() {
        super.setUp()
        settings = EmberkaiIconsSettings.getInstance()
        savedState = settings.state
        settings.loadState(EmberkaiIconsSettings.State())
        PropertiesComponent.getInstance().unsetValue(WelcomeNotification.SHOWN_KEY)
    }

    override fun tearDown() {
        try {
            savedState?.let(settings::loadState)
            PropertiesComponent.getInstance().unsetValue(WelcomeNotification.SHOWN_KEY)
        } finally {
            super.tearDown()
        }
    }

    fun testShownOnceOnAFreshInstall() {
        assertTrue(WelcomeNotification.claim())
        assertFalse("Only the first project opened introduces the plugin", WelcomeNotification.claim())
    }

    fun testNotShownToUsersWhoAlreadyChangedTheFeatures() {
        settings.setEnabled(IconRuleCategory.ARCHITECTURE, true)
        assertFalse(WelcomeNotification.claim())
        assertFalse(WelcomeNotification.claim())
    }

    fun testOffersTheLegendAndThePresets() {
        val notification = WelcomeNotification.create()
        assertEquals(WelcomeNotification.GROUP_ID, notification.groupId)
        assertEquals(
            listOf("Show icon legend", "Just the icons", "Turn everything on", "Settings"),
            notification.actions.map { it.templatePresentation.text },
        )
    }

    fun testPresetsApplyAsOnTheSettingsPage() {
        WelcomeNotification.apply(SettingsPreset.ICONS_ONLY)
        assertEquals(SettingsPreset.ICONS_ONLY, SettingsPreset.matching(settings.state))

        WelcomeNotification.apply(SettingsPreset.EVERYTHING)
        assertEquals(SettingsPreset.EVERYTHING, SettingsPreset.matching(settings.state))
        assertTrue("Presets leave native icons alone", settings.preserveNativeJavaKotlinIcons)
    }
}
