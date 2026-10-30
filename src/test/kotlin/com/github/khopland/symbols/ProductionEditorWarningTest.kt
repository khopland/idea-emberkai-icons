package com.github.khopland.symbols

import com.github.khopland.symbols.badges.ProductionEditorNotificationProvider
import com.github.khopland.symbols.badges.ProductionEditorTabColorProvider
import com.github.khopland.symbols.badges.ProductionEditorWarning
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel

internal class ProductionEditorWarningTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null
    private val tabs = ProductionEditorTabColorProvider()
    private val banners = ProductionEditorNotificationProvider()

    override fun setUp() {
        super.setUp()
        settings = EmberkaiIconsSettings.getInstance()
        savedState = settings.state
    }

    override fun tearDown() {
        try {
            savedState?.let(settings::loadState)
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

    fun testTheSettingTurnsBothOff() {
        val profile = file("src/main/resources/application-prod.yml")
        settings.setEnabled(IconRuleCategory.PRODUCTION_EDITOR_WARNINGS, false)
        assertNull(tabs.getEditorTabColor(project, profile))
        assertNull(banners.collectNotificationData(project, profile))
    }

    private fun file(path: String): VirtualFile = myFixture.addFileToProject(path, "kind: Deployment").virtualFile

    private fun bannerText(file: VirtualFile): String? {
        myFixture.openFileInEditor(file)
        val editor = FileEditorManager.getInstance(project).getSelectedEditor(file)!!
        val panel = banners.collectNotificationData(project, file)!!.apply(editor) as EditorNotificationPanel
        return panel.text
    }
}
