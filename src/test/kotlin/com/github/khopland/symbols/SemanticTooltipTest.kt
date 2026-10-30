package com.github.khopland.symbols

import com.github.khopland.symbols.core.IconDescriptions
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.tooltips.SemanticTooltipDecorator
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.jps.model.java.JavaSourceRootType

internal class SemanticTooltipTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null

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

    fun testContextRulesDescribeWhatWasRecognized() {
        assertDescription("Kubernetes manifest", "deploy/service.yaml", "apiVersion: v1\nkind: Service")
        assertDescription("Flyway versioned migration", "db/migration/V1__create_tables.sql", "select 1;")
        assertDescription("Flyway repeatable migration", "db/migration/R__refresh_views.sql", "select 1;")
        assertDescription("Liquibase changelog", "db/changelog/db.changelog-master.yaml", "databaseChangeLog: []")
        assertDescription("OpenAPI contract", "contract.yaml", "openapi: 3.1.0\ninfo: {title: API}")
        assertDescription("GitHub Actions workflow", ".github/workflows/ci.yml", "on: push")
    }

    fun testEnvironmentVariantsAreAppendedToTheBaseDescription() {
        myFixture.addFileToProject("chart/Chart.yaml", "apiVersion: v2\nname: demo")
        assertDescription("Helm chart values · production", "chart/values-production.yaml", "replicas: 3")
        assertDescription("Helm chart", "chart/Chart.yaml", null)

        myFixture.addFileToProject("service/build.gradle.kts", "plugins { id(\"org.springframework.boot\") }")
        assertDescription(
            "Spring Boot configuration · staging",
            "service/src/main/resources/application-staging.yaml", "server: {}",
        )
        assertDescription("Docker Compose file · production", "docker-compose.prod.yml", "services: {}")
    }

    fun testProjectRolesAndModuleAccentsAreDescribed() {
        val testRoot = myFixture.tempDirFixture.findOrCreateDir("src/test/kotlin")
        PsiTestUtil.addSourceRoot(module, testRoot, JavaSourceRootType.TEST_SOURCE)
        val moduleName = ProjectFileIndex.getInstance(project).getModuleForFile(testRoot)?.name
        assertNotNull(moduleName)

        settings.moduleAwareColors = false
        assertEquals("Test source root", describe(testRoot))
        settings.moduleAwareColors = true
        assertEquals("Test source root · module accent: $moduleName", describe(testRoot))
    }

    fun testPlainMappingsAndNativeIconsStayQuiet() {
        assertDescription(null, "notes.txt", "notes")
        assertDescription(null, "data.json", "{}")
        assertDescription(null, "settings.yaml", "key: value")
        assertDescription(null, "Example.kt", "fun example() = Unit")
        assertNull(describe(myFixture.tempDirFixture.findOrCreateDir("docs")))
    }

    fun testDecoratorSetsTooltipAndHonorsTheSetting() {
        val file = myFixture.addFileToProject("deploy/service.yaml", "apiVersion: v1\nkind: Service")
        val node = PsiFileNode(project, file, ViewSettings.DEFAULT)

        val data = PresentationData()
        SemanticTooltipDecorator().decorate(node, data)
        assertEquals("Kubernetes manifest", data.tooltip)

        settings.semanticTooltips = false
        val disabled = PresentationData()
        SemanticTooltipDecorator().decorate(node, disabled)
        assertNull(disabled.tooltip)
    }

    fun testDecoratorAppendsToNativeTooltips() {
        val directory = myFixture.tempDirFixture.findOrCreateDir("migrations")
        val psiDirectory = PsiManager.getInstance(project).findDirectory(directory)
        assertNotNull(psiDirectory)
        val data = PresentationData()
        data.tooltip = "Native tooltip"
        SemanticTooltipDecorator().decorate(PsiDirectoryNode(project, psiDirectory!!, ViewSettings.DEFAULT), data)
        assertEquals("Native tooltip · Database migrations folder", data.tooltip)
    }

    fun testAppendTooltipKeepsExistingText() {
        assertEquals("Kubernetes manifest", SemanticTooltipDecorator.appendTooltip(null, "Kubernetes manifest"))
        assertEquals("Read-only · Flux resource", SemanticTooltipDecorator.appendTooltip("Read-only", "Flux resource"))
        assertEquals(
            "<html><b>Module</b><br>A &amp; B</html>",
            SemanticTooltipDecorator.appendTooltip("<html><b>Module</b></html>", "A & B"),
        )
        assertEquals("Source root", SemanticTooltipDecorator.appendTooltip("Source root", "Source root"))
    }

    private fun assertDescription(expected: String?, path: String, content: String?) {
        val file = if (content == null) {
            myFixture.findFileInTempDir(path) ?: error("Missing fixture file: $path")
        } else {
            myFixture.addFileToProject(path, content).virtualFile
        }
        assertEquals(path, expected, describe(file))
    }

    private fun describe(file: VirtualFile): String? =
        IconDescriptions.describe(file, IconResolver.resolveWithDetails(file, project))
}
