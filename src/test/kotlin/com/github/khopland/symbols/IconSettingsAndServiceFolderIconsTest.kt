package com.github.khopland.symbols

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

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

    fun testKubernetesCategorySwitchChangesIconResolution() {
        settings.colorServiceFolders = false
        settings.setEnabled(IconRuleCategory.KUBERNETES, false)
        val file = myFixture.addFileToProject("deployment.yaml", "apiVersion: v1\nkind: Service")
        assertResourceIcon("symbols/icons/files/yaml.svg", provider.getIcon(file, 0))
        assertResourceIcon("symbols/icons/files/yaml.svg", provider.getIcon(file.virtualFile, 0, project))

        settings.setEnabled(IconRuleCategory.KUBERNETES, true)
        assertResourceIcon("emberkai/icons/kubernetes/service.svg", provider.getIcon(file, 0))
        assertResourceIcon("emberkai/icons/kubernetes/service.svg", provider.getIcon(file.virtualFile, 0, project))
    }

    fun testEveryContextCategoryCanBeDisabledWithoutDisablingFormatIcons() {
        assertCategoryGate(
            IconRuleCategory.CI, "azure-pipelines-team.yml", "trigger: none",
            "emberkai/icons/ci/azurepipelines.svg", "symbols/icons/files/yaml.svg",
        )
        assertCategoryGate(
            IconRuleCategory.MIGRATION, "db/migration/V1__create_tables.sql", "",
            "emberkai/icons/migration.svg", "symbols/icons/files/database.svg",
        )
        assertCategoryGate(
            IconRuleCategory.HELM, "chart/values.yaml", "replicas: 1",
            "emberkai/icons/helm.svg", "symbols/icons/files/yaml.svg",
            "chart/Chart.yaml" to "apiVersion: v2\nname: chart",
        )
        assertCategoryGate(
            IconRuleCategory.ANSIBLE, "playbook.yaml", "- hosts: all\n  tasks: []",
            "emberkai/icons/ansible.svg", "symbols/icons/files/yaml.svg",
        )
        assertCategoryGate(
            IconRuleCategory.INFRASTRUCTURE, "template.yaml",
            "AWSTemplateFormatVersion: '2010-09-09'\nResources: {}",
            "emberkai/icons/infrastructure.svg", "symbols/icons/files/yaml.svg",
        )
        assertCategoryGate(
            IconRuleCategory.GITOPS, "flux-resource.yaml",
            "apiVersion: source.toolkit.fluxcd.io/v1\nkind: GitRepository\nmetadata: {}",
            "emberkai/icons/gitops/flux.svg", "symbols/icons/files/yaml.svg",
        )
        assertCategoryGate(
            IconRuleCategory.API_SPECIFICATION, "contract.yaml", "openapi: 3.1.0\ninfo: {title: API}",
            "emberkai/icons/openapi.svg", "symbols/icons/files/yaml.svg",
        )
        assertCategoryGate(
            IconRuleCategory.KUBERNETES, "deployment.yaml", "apiVersion: v1\nkind: Service",
            "emberkai/icons/kubernetes/service.svg", "symbols/icons/files/yaml.svg",
        )
        assertCategoryGate(
            IconRuleCategory.SPRING_BOOT, "service/src/main/resources/application-local.yaml", "server: {}",
            "emberkai/icons/spring.svg", "symbols/icons/files/yaml.svg",
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
        val icons = ServiceFolderIcons(IconMappings())
        assertNull(icons.icon(directory, project))

        lateinit var marker: VirtualFile
        WriteCommandAction.runWriteCommandAction(project) {
            marker = directory.createChildData(this@IconSettingsAndServiceFolderIconsTest, "package.json")
            VfsUtil.saveText(marker, "{}")
        }
        assertNotNull(
            "A service manifest created after the first lookup should invalidate its cached result",
            icons.icon(directory, project),
        )
        WriteCommandAction.runWriteCommandAction(project) { marker.delete(this@IconSettingsAndServiceFolderIconsTest) }
        assertNull(icons.icon(directory, project))
    }

    fun testSiblingServiceRootsReceiveDifferentPaletteIcons() {
        val alpha = localDirectory("services/alpha")
        val beta = localDirectory("services/beta")
        addLocalFile("services/alpha/build.gradle.kts", "plugins {}")
        addLocalFile("services/beta/package.json", "{}")

        val icons = ServiceFolderIcons(IconMappings())
        val betaIcon = icons.icon(beta, project)
        val alphaIcon = icons.icon(alpha, project)
        assertNotNull(alphaIcon)
        assertNotNull(betaIcon)
        assertNotSame(alphaIcon, betaIcon)
        assertResourceIcon("symbols/icons/folders/folder-blue.svg", alphaIcon)
        assertResourceIcon("symbols/icons/folders/folder-green.svg", betaIcon)
    }

    fun testDependenciesAreNotClassifiedAsServiceRoots() {
        val packageDirectory = localDirectory("node_modules/example")
        addLocalFile("node_modules/example/package.json", "{}")
        assertNull(ServiceFolderIcons(IconMappings()).icon(packageDirectory, project))
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

            assertTrue(ProjectPathUtil.isSamePath(realRoot.toString(), alias.toString()))
            assertTrue(ProjectPathUtil.isAncestorPath(alias.toString(), realChild.toString()))
        } finally {
            temporaryRoot.toFile().deleteRecursively()
        }
    }

    private fun localDirectory(relativePath: String): VirtualFile {
        val path = Paths.get(project.basePath!!).resolve(relativePath)
        Files.createDirectories(path)
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString())
            ?: error("Could not load project directory: $path")
    }

    private fun addLocalFile(relativePath: String, content: String): VirtualFile {
        val path = Paths.get(project.basePath!!).resolve(relativePath)
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString())
            ?: error("Could not load project file: $path")
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
