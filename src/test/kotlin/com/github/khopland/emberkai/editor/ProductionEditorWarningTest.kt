package com.github.khopland.emberkai.editor

import com.github.khopland.emberkai.IconProviderTestCase
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import java.awt.Component
import java.awt.Container
import javax.swing.JCheckBox

internal class ProductionEditorWarningTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiEditorSettings
    private var savedState: EmberkaiEditorSettings.State? = null
    private var savedIconsState: EmberkaiIconsSettings.State? = null
    private val tabs = ProductionEditorTabColorProvider()
    private val banners = ProductionEditorNotificationProvider()

    override fun setUp() {
        super.setUp()
        settings = EmberkaiEditorSettings.getInstance()
        savedState = settings.state
        savedIconsState = EmberkaiIconsSettings.getInstance().state
    }

    override fun tearDown() {
        try {
            savedState?.let(settings::loadState)
            savedIconsState?.let(EmberkaiIconsSettings.getInstance()::loadState)
        } finally {
            super.tearDown()
        }
    }

    fun testProductionFilesGetATintedTabAndABanner() {
        val profile = file("src/main/resources/application-prod.yml")
        assertEquals(ProductionEditorWarning.TAB_COLOR, tabs.getEditorTabColor(project, profile))
        assertEquals(
            "This is production configuration; changes here reach production.",
            bannerText(profile),
        )
    }

    fun testFilesInheritingFromAProductionFolderNameTheFolder() {
        val patch = file("deploy/overlays/production/deployment.yaml")
        assertEquals(ProductionEditorWarning.TAB_COLOR, tabs.getEditorTabColor(project, patch))
        assertEquals(
            "This is production configuration; changes here reach production. " +
                "It inherits production from the overlays/production/ folder.",
            bannerText(patch),
        )
    }

    fun testOtherEnvironmentsAndOrdinaryFilesAreLeftAlone() {
        for (path in listOf("src/main/resources/application-staging.yml", "deploy/overlays/dev/deployment.yaml", "README.md")) {
            val file = file(path)
            assertNull(path, tabs.getEditorTabColor(project, file))
            assertNull(path, banners.collectNotificationData(project, file))
        }
    }

    fun testTheBannerAndTheTabTintTurnOffSeparately() {
        val profile = file("src/main/resources/application-prod.yml")
        settings.productionBanner = false
        assertNull(banners.collectNotificationData(project, profile))
        assertEquals(ProductionEditorWarning.TAB_COLOR, tabs.getEditorTabColor(project, profile))

        settings.productionBanner = true
        settings.productionTabTint = false
        assertNull(tabs.getEditorTabColor(project, profile))
        assertNotNull(banners.collectNotificationData(project, profile))
    }

    fun testTheOldSwitchTurnedOffCarriesOverToBothOnce() {
        val icons = EmberkaiIconsSettings.getInstance()
        icons.loadState(EmberkaiIconsSettings.State(productionEditorWarnings = false))
        settings.loadState(EmberkaiEditorSettings.State())
        settings.noStateLoaded()
        assertEquals(EmberkaiEditorSettings.State(productionBanner = false, productionTabTint = false), settings.state)
        assertTrue("The old value is read only once", icons.state.productionEditorWarnings)

        settings.productionBanner = true
        settings.noStateLoaded()
        assertTrue("Turning the banner back on sticks", settings.productionBanner)
    }

    fun testNothingCarriesOverWhenTheOldSwitchWasOn() {
        settings.loadState(EmberkaiEditorSettings.State())
        settings.noStateLoaded()
        assertEquals(EmberkaiEditorSettings.State(), settings.state)
    }

    fun testTheSettingsPageAppliesBothSwitches() {
        settings.loadState(EmberkaiEditorSettings.State())
        val configurable = EditorWarningsConfigurable()
        try {
            val boxes = components(configurable.createComponent()!!).filterIsInstance<JCheckBox>()
            assertEquals(2, boxes.size)
            boxes.forEach { it.isSelected = false }
            assertTrue(configurable.isModified)
            configurable.apply()
            assertEquals(EmberkaiEditorSettings.State(productionBanner = false, productionTabTint = false), settings.state)
        } finally {
            configurable.disposeUIResources()
        }
    }

    private fun file(path: String): VirtualFile = myFixture.addFileToProject(path, "kind: Deployment").virtualFile

    private fun components(root: Component): List<Component> =
        listOf(root) + ((root as? Container)?.components?.flatMap(::components) ?: emptyList())

    private fun bannerText(file: VirtualFile): String? {
        myFixture.openFileInEditor(file)
        val editor = FileEditorManager.getInstance(project).getSelectedEditor(file)!!
        val panel = banners.collectNotificationData(project, file)!!.apply(editor) as EditorNotificationPanel
        return panel.text
    }
}
