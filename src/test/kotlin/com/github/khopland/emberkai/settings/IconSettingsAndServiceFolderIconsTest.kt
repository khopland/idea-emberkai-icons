package com.github.khopland.emberkai.settings

import com.github.khopland.emberkai.IconProviderTestCase
import com.github.khopland.emberkai.core.CategoryGroup
import com.github.khopland.emberkai.core.IconRuleCategory
import com.github.khopland.emberkai.core.ProjectPathUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBTextField
import java.awt.Component
import java.awt.Container
import java.io.IOException
import java.nio.file.Files
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel

internal class IconSettingsAndServiceFolderIconsTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null

    override fun setUp() {
        super.setUp()
        settings = EmberkaiIconsSettings.getInstance()
        savedState = settings.state
    }

    override fun tearDown() {
        savedState?.let(settings::loadState)
        super.tearDown()
    }

    fun testSettingsCopyStateAndToggleEveryCategory() {
        val loaded = EmberkaiIconsSettings.State()
        settings.loadState(loaded)
        loaded.ci = false
        assertTrue("loadState keeps its own copy", settings.isEnabled(IconRuleCategory.CI))

        val snapshot = settings.state
        snapshot.helm = false
        assertTrue("getState returns a snapshot", settings.isEnabled(IconRuleCategory.HELM))

        for (category in IconRuleCategory.entries) {
            settings.setEnabled(category, false)
            assertFalse("$category disabled", settings.isEnabled(category))
            settings.setEnabled(category, true)
            assertTrue("$category enabled", settings.isEnabled(category))
        }
    }

    fun testEveryCategoryAndGeneralFlagHasItsOwnField() {
        val fields = EmberkaiIconsSettings.GENERAL_FLAGS + IconRuleCategory.entries.map(EmberkaiIconsSettings::flag)
        assertEquals(fields.size, fields.map { it.name }.toSet().size)
    }

    fun testConfigurableAppliesAndResetsTheForm() {
        val configurable = EmberkaiIconsConfigurable()
        try {
            val root = configurable.createComponent()
            assertFalse(configurable.isModified)

            val helm = checkBoxes(root).single { it.text == IconRuleCategory.HELM.displayName }
            helm.doClick()
            assertTrue(configurable.isModified)
            configurable.reset()
            assertFalse(configurable.isModified)
            assertTrue(helm.isSelected)

            helm.doClick()
            configurable.apply()
            assertFalse(configurable.isModified)
            assertFalse(settings.isEnabled(IconRuleCategory.HELM))
        } finally {
            configurable.disposeUIResources()
        }
    }

    fun testSettingsPageGroupsCategoriesAndFiltersBySection() {
        val configurable = EmberkaiIconsConfigurable()
        try {
            val root = configurable.createComponent()
            val order = components(root).mapNotNull { (it as? JLabel)?.text ?: (it as? JCheckBox)?.text }
            for (group in CategoryGroup.entries) {
                val start = order.indexOf(group.title)
                assertTrue("${group.title} section", start >= 0)
                val rows = order.drop(start + 2).takeWhile { label -> CategoryGroup.entries.none { it.title == label } }
                for (category in IconRuleCategory.entries.filter { it.group == group }) {
                    assertTrue("${category.displayName} is listed under ${group.title}", category.displayName in rows)
                }
            }

            val filter = components(root).filterIsInstance<JBTextField>().single()
            val visible = { text: String -> components(root).single { (it as? JCheckBox)?.text == text || (it as? JLabel)?.text == text }.isVisible }
            filter.text = "warnings"
            assertTrue("A section title matches its rows", visible(IconRuleCategory.CONFIG_DRIFT.displayName))
            assertTrue(visible(CategoryGroup.WARNINGS.title))
            assertFalse("Sections without a match hide their title", visible(CategoryGroup.TOOL_ICONS.title))
            assertFalse(visible(IconRuleCategory.HELM.displayName))

            filter.text = "helm"
            assertTrue(visible(IconRuleCategory.HELM.displayName))
            assertTrue(visible(CategoryGroup.TOOL_ICONS.title))
            assertFalse(visible(CategoryGroup.WARNINGS.title))

            filter.text = ""
            assertFalse("Single features stay under Advanced", visible(IconRuleCategory.HELM.displayName))
            assertTrue(visible("Keep IntelliJ's native Java and Kotlin file icons"))
            components(root).filterIsInstance<ActionLink>().single { "legend" !in it.text }.doClick()
            assertTrue(components(root).filterIsInstance<JCheckBox>().all { it.isVisible })
        } finally {
            configurable.disposeUIResources()
        }
    }

    fun testPresetsSetFeaturesButKeepLookAndAccessibility() {
        val custom = EmberkaiIconsSettings.State(highContrastFolderIcons = true, serviceFolderPalette = "ACCESSIBLE")
        assertEquals("The defaults are the recommended preset", SettingsPreset.RECOMMENDED, SettingsPreset.matching(EmberkaiIconsSettings.State()))

        val iconsOnly = SettingsPreset.ICONS_ONLY.applyTo(custom)
        assertTrue(iconsOnly.isEnabled(IconRuleCategory.HELM))
        assertFalse("Off by default, so off in Icons only", iconsOnly.isEnabled(IconRuleCategory.ARCHITECTURE))
        assertFalse(iconsOnly.isEnabled(IconRuleCategory.CONFIG_DRIFT))
        assertFalse(iconsOnly.isEnabled(IconRuleCategory.STEREOTYPE_BADGES))
        assertFalse(iconsOnly.semanticTooltips)
        assertTrue(iconsOnly.highContrastFolderIcons)
        assertEquals("ACCESSIBLE", iconsOnly.serviceFolderPalette)
        assertEquals(SettingsPreset.ICONS_ONLY, SettingsPreset.matching(iconsOnly))

        val everything = SettingsPreset.EVERYTHING.applyTo(custom)
        assertTrue(SettingsPreset.PRESET_FLAGS.all { it.get(everything) })
        assertTrue(everything.highContrastFolderIcons)

        everything.helm = false
        assertNull("A changed feature is a custom mix", SettingsPreset.matching(everything))
    }

    fun testConfigurablePresetPickerFollowsTheCheckboxes() {
        settings.loadState(EmberkaiIconsSettings.State())
        val configurable = EmberkaiIconsConfigurable()
        try {
            val root = configurable.createComponent()
            val picker = components(root).filterIsInstance<JComboBox<*>>().single { it.accessibleContext.accessibleName == "Feature preset" }
            assertEquals("Recommended", picker.selectedItem.toString())

            picker.selectedIndex = SettingsPreset.ICONS_ONLY.ordinal
            assertTrue(configurable.isModified)
            assertFalse(checkBoxes(root).single { it.text == IconRuleCategory.CONFIG_DRIFT.displayName }.isSelected)

            checkBoxes(root).single { it.text == IconRuleCategory.CONFIG_DRIFT.displayName }.doClick()
            assertEquals("Custom", picker.selectedItem.toString())

            configurable.apply()
            assertTrue(settings.isEnabled(IconRuleCategory.CONFIG_DRIFT))
            assertFalse(settings.isEnabled(IconRuleCategory.UNPINNED_REFERENCES))
            assertNull(SettingsPreset.matching(settings.state))
        } finally {
            configurable.disposeUIResources()
        }
    }

    private fun checkBoxes(component: Component): List<JCheckBox> = components(component).filterIsInstance<JCheckBox>()

    private fun components(component: Component): List<Component> = buildList {
        add(component)
        if (component is Container) component.components.forEach { addAll(components(it)) }
    }

    fun testKubernetesCategorySwitchChangesIconResolution() {
        settings.colorServiceFolders = false
        settings.setEnabled(IconRuleCategory.KUBERNETES, false)
        val file = myFixture.addFileToProject("deployment.yaml", "apiVersion: v1\nkind: Service")
        assertResourceIcon("emberkai/icons/originals/files/yaml.svg", provider.getIcon(file, 0))
        assertResourceIcon("emberkai/icons/originals/files/yaml.svg", provider.getIcon(file.virtualFile, 0, project))

        settings.setEnabled(IconRuleCategory.KUBERNETES, true)
        assertResourceIcon("emberkai/icons/kubernetes/service.svg", provider.getIcon(file, 0))
        assertResourceIcon("emberkai/icons/kubernetes/service.svg", provider.getIcon(file.virtualFile, 0, project))
    }

    fun testEveryContextCategoryCanBeDisabledWithoutDisablingFormatIcons() {
        assertCategoryGate(
            IconRuleCategory.CI, "azure-pipelines-team.yml", "trigger: none",
            "emberkai/icons/ci/azurepipelines.svg", "emberkai/icons/originals/files/yaml.svg",
        )
        assertCategoryGate(
            IconRuleCategory.MIGRATION, "db/migration/V1__create_tables.sql", "",
            "emberkai/icons/migration.svg", "emberkai/icons/originals/files/database.svg",
        )
        assertCategoryGate(
            IconRuleCategory.HELM, "chart/values.yaml", "replicas: 1",
            "emberkai/icons/helm.svg", "emberkai/icons/originals/files/yaml.svg",
            "chart/Chart.yaml" to "apiVersion: v2\nname: chart",
        )
        assertCategoryGate(
            IconRuleCategory.ANSIBLE, "playbook.yaml", "- hosts: all\n  tasks: []",
            "emberkai/icons/ansible.svg", "emberkai/icons/originals/files/yaml.svg",
        )
        assertCategoryGate(
            IconRuleCategory.INFRASTRUCTURE, "template.yaml",
            "AWSTemplateFormatVersion: '2010-09-09'\nResources: {}",
            "emberkai/icons/infrastructure.svg", "emberkai/icons/originals/files/yaml.svg",
        )
        assertCategoryGate(
            IconRuleCategory.GITOPS, "flux-resource.yaml",
            "apiVersion: source.toolkit.fluxcd.io/v1\nkind: GitRepository\nmetadata: {}",
            "emberkai/icons/gitops/flux.svg", "emberkai/icons/originals/files/yaml.svg",
        )
        assertCategoryGate(
            IconRuleCategory.API_SPECIFICATION, "contract.yaml", "openapi: 3.1.0\ninfo: {title: API}",
            "emberkai/icons/openapi.svg", "emberkai/icons/originals/files/yaml.svg",
        )
        assertCategoryGate(
            IconRuleCategory.KUBERNETES, "deployment.yaml", "apiVersion: v1\nkind: Service",
            "emberkai/icons/kubernetes/service.svg", "emberkai/icons/originals/files/yaml.svg",
        )
        assertCategoryGate(
            IconRuleCategory.SPRING_BOOT, "service/src/main/resources/application-local.yaml", "server: {}",
            "emberkai/icons/spring.svg", "emberkai/icons/originals/files/yaml.svg",
            "service/build.gradle.kts" to "plugins { id(\"org.springframework.boot\") }",
        )
    }

    fun testNativeJavaKotlinIconSetting() {
        val file = myFixture.addFileToProject("Example.kt", "fun example() = Unit")
        settings.preserveNativeJavaKotlinIcons = true
        assertNull(provider.getIcon(file, 0))
        assertNull(provider.getIcon(file.virtualFile, 0, project))

        settings.preserveNativeJavaKotlinIcons = false
        assertResourceIcon("symbols/icons/files/kotlin.svg", provider.getIcon(file, 0))
        assertResourceIcon("symbols/icons/files/kotlin.svg", provider.getIcon(file.virtualFile, 0, project))
    }

    fun testServiceRootCacheTracksManifestCreationAndDeletion() {
        val directory = localDirectory("services/catalog")
        assertNull(serviceFolderIcon(directory))

        lateinit var marker: VirtualFile
        WriteCommandAction.runWriteCommandAction(project) {
            marker = directory.createChildData(this@IconSettingsAndServiceFolderIconsTest, "package.json")
            VfsUtil.saveText(marker, "{}")
        }
        assertNotNull(
            "A service manifest created after the first lookup should invalidate its cached result",
            serviceFolderIcon(directory),
        )
        WriteCommandAction.runWriteCommandAction(project) { marker.delete(this@IconSettingsAndServiceFolderIconsTest) }
        assertNull(serviceFolderIcon(directory))
    }

    fun testSiblingServiceRootsReceiveDifferentPaletteIcons() {
        val alpha = localDirectory("services/alpha")
        val beta = localDirectory("services/beta")
        addLocalFile("services/alpha/build.gradle.kts", "plugins {}")
        addLocalFile("services/beta/package.json", "{}")
        val betaIcon = serviceFolderIcon(beta)
        val alphaIcon = serviceFolderIcon(alpha)
        assertNotNull(alphaIcon)
        assertNotNull(betaIcon)
        assertNotSame(alphaIcon, betaIcon)
        assertResourceIcon("symbols/icons/folders/folder-blue.svg", alphaIcon)
        assertResourceIcon("symbols/icons/folders/folder-green.svg", betaIcon)
    }

    fun testDependenciesAreNotClassifiedAsServiceRoots() {
        val packageDirectory = localDirectory("node_modules/example")
        addLocalFile("node_modules/example/package.json", "{}")
        assertNull(serviceFolderIcon(packageDirectory))
    }

    fun testCanonicalPathComparisonFollowsSymlinks() {
        val temporaryRoot = Files.createTempDirectory("emberkai-paths")
        try {
            val realRoot = Files.createDirectories(temporaryRoot.resolve("real"))
            val realChild = Files.createDirectories(realRoot.resolve("nested"))
            val alias = temporaryRoot.resolve("alias")
            try {
                Files.createSymbolicLink(alias, realRoot)
            } catch (_: IOException) {
                return
            } catch (_: UnsupportedOperationException) {
                return
            }

            val canonicalAlias = ProjectPathUtil.canonicalPath(alias.toString())
            assertTrue(FileUtil.pathsEqual(ProjectPathUtil.canonicalPath(realRoot.toString()), canonicalAlias))
            assertTrue(FileUtil.isAncestor(canonicalAlias, ProjectPathUtil.canonicalPath(realChild.toString()), false))
        } finally {
            temporaryRoot.toFile().deleteRecursively()
        }
    }


    private fun assertCategoryGate(
        category: IconRuleCategory,
        path: String,
        content: String,
        enabledIcon: String,
        fallbackIcon: String,
        vararg prerequisites: Pair<String, String>,
    ) {
        prerequisites.forEach { (prerequisitePath, prerequisiteContent) ->
            myFixture.addFileToProject(prerequisitePath, prerequisiteContent)
        }
        val file: PsiFile = myFixture.addFileToProject(path, content)

        val badgesWereEnabled = settings.isEnabled(IconRuleCategory.ENVIRONMENT_VARIANTS)
        settings.setEnabled(IconRuleCategory.ENVIRONMENT_VARIANTS, false)
        try {
            settings.setEnabled(category, false)
            assertResourceIcon(fallbackIcon, provider.getIcon(file, 0))
            settings.setEnabled(category, true)
            assertResourceIcon(enabledIcon, provider.getIcon(file, 0))
        } finally {
            settings.setEnabled(IconRuleCategory.ENVIRONMENT_VARIANTS, badgesWereEnabled)
        }
    }
}
