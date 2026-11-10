package com.github.khopland.emberkai.lens

import com.github.khopland.emberkai.IconProviderTestCase
import java.awt.Component
import java.awt.Container
import javax.swing.JSpinner

internal class LensSettingsTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiLensSettings
    private var savedState: EmberkaiLensSettings.State? = null

    override fun setUp() {
        super.setUp()
        settings = EmberkaiLensSettings.getInstance()
        savedState = settings.state
    }

    override fun tearDown() {
        try {
            savedState?.let(settings::loadState)
            IconLensService.getIfCreated(project)?.select(null)
        } finally {
            super.tearDown()
        }
    }

    fun testDefaultsMatchTheHotspotsLensBeforeItHadSettings() {
        settings.loadState(EmberkaiLensSettings.State())
        assertEquals(90, settings.hotspotDays)
        assertEquals(30, settings.hotspotLimit)
        assertEquals(2, settings.hotspotMinimumCommits)
    }

    fun testOutOfRangeValuesAreClamped() {
        settings.loadState(EmberkaiLensSettings.State(hotspotDays = 0, hotspotLimit = -5, hotspotMinimumCommits = 1_000))
        assertEquals(1, settings.hotspotDays)
        assertEquals(1, settings.hotspotLimit)
        assertEquals(100, settings.hotspotMinimumCommits)
    }

    fun testTheSettingsPageAppliesItsSpinners() {
        settings.loadState(EmberkaiLensSettings.State())
        val configurable = LensSettingsConfigurable()
        try {
            val spinners = components(configurable.createComponent()!!).filterIsInstance<JSpinner>()
            assertEquals(3, spinners.size)
            spinners[0].value = 30
            spinners[1].value = 10
            spinners[2].value = 3
            assertTrue(configurable.isModified)
            configurable.apply()
            assertEquals(EmberkaiLensSettings.State(hotspotDays = 30, hotspotLimit = 10, hotspotMinimumCommits = 3), settings.state)
        } finally {
            configurable.disposeUIResources()
        }
    }

    fun testSelectingALensTellsListeners() {
        val seen = mutableListOf<Lens?>()
        project.messageBus.connect(testRootDisposable).subscribe(IconLensListener.TOPIC, IconLensListener { seen += it })
        val service = IconLensService.getInstance(project)
        service.select(IconLens.MIGRATIONS)
        service.select(null)
        assertEquals(listOf(IconLens.MIGRATIONS, null), seen)
    }

    private fun components(root: Component): List<Component> =
        listOf(root) + ((root as? Container)?.components?.flatMap(::components) ?: emptyList())
}
