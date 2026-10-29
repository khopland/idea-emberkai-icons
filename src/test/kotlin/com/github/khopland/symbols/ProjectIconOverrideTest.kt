package com.github.khopland.symbols

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

internal class ProjectIconOverrideTest : IconProviderTestCase() {
    override fun tearDown() {
        try {
            val base = project.basePath?.let(Paths::get)
            if (base != null) {
                Files.deleteIfExists(base.resolve(".idea/${ProjectIconOverrideStore.PROPERTIES_NAME}"))
                base.resolve(".idea/${ProjectIconOverrideStore.ICONS_DIRECTORY}").toFile().deleteRecursively()
                ProjectIconOverrideStore.getInstance(project).reload()
            }
        } finally {
            super.tearDown()
        }
    }

    fun testProjectFileOverrideWinsOverBundledMappings() {
        writeOverrideFile("extension.yaml=/symbols/icons/files/github.svg\n")
        val file = myFixture.addFileToProject("deployment.yaml", "apiVersion: v1\nkind: Service")

        val resolution = resolve(file.virtualFile)
        assertEquals("extension.yaml", resolution.mappingKey)
        assertEquals(MappingSource.PROJECT_OVERRIDE, resolution.mappingSource)
        assertResourceIcon("symbols/icons/files/github.svg", resolution.icon)
    }

    fun testContextualOverrideUsesTheLongestParentSuffix() {
        writeOverrideFile("file.deploy/prod/values.yml=/emberkai/icons/helm.svg\n")
        val file = myFixture.addFileToProject("deploy/prod/values.yml", "replicas: 1")
        val resolution = resolve(file.virtualFile)
        assertEquals("file.deploy/prod/values.yml", resolution.mappingKey)
        assertEquals(MappingSource.PROJECT_OVERRIDE, resolution.mappingSource)
        assertResourceIcon("emberkai/icons/helm.svg", resolution.icon)
    }

    fun testLocalArtworkIsLoadedFromTheOverrideDirectory() {
        writeLocalFile(".idea/${ProjectIconOverrideStore.ICONS_DIRECTORY}/custom.svg", SVG)
        writeOverrideFile("extension.custom=custom.svg\n")
        val file = myFixture.addFileToProject("example.custom", "data")
        val resolution = resolve(file.virtualFile)
        assertEquals("extension.custom", resolution.mappingKey)
        assertEquals(MappingSource.PROJECT_OVERRIDE, resolution.mappingSource)
        assertNotNull(resolution.icon)
        assertTrue(resolution.resourcePath!!.endsWith("custom.svg"))
    }

    fun testFolderOverrideWinsOverProjectRolesAndFolderNames() {
        writeOverrideFile("folder.assets=/symbols/icons/files/github.svg\n")
        val folder = myFixture.addFileToProject("assets/readme.md", "data").parent!!.virtualFile
        val resolution = resolve(folder)
        assertEquals("folder.assets", resolution.mappingKey)
        assertEquals(MappingSource.PROJECT_OVERRIDE, resolution.mappingSource)
        assertResourceIcon("symbols/icons/files/github.svg", resolution.icon)
    }

    fun testNativeIconsStillTakePrecedenceOverProjectOverrides() {
        writeOverrideFile("extension.kt=/symbols/icons/files/github.svg\n")
        val file = myFixture.addFileToProject("Example.kt", "fun example() = Unit")
        val resolution = resolve(file.virtualFile)
        assertEquals(IconPrecedence.NATIVE_POLICY, resolution.precedence)
        assertNull(resolution.icon)
    }

    fun testOverridesCanBeAddedAndRemovedThroughTheStore() {
        val store = ProjectIconOverrideStore.getInstance(project)
        store.ensurePropertiesFile()
        store.putOverride("extension.custom", "/symbols/icons/files/github.svg")
        assertEquals("/symbols/icons/files/github.svg", store.overrides()["extension.custom"])
        assertNotNull(store.fileOverride(myFixture.addFileToProject("a.custom", "x").virtualFile, "a.custom"))

        store.removeOverride("extension.custom")
        assertNull(store.overrides()["extension.custom"])
    }

    fun testCatalogContainsBundledKeysWithoutProjectOverrides() {
        writeOverrideFile("extension.custom=/symbols/icons/files/github.svg\n")
        val catalog = IconMappings().catalog()
        assertTrue(catalog.any { it.key == "extension.yaml" })
        assertTrue(catalog.none { it.key == "extension.custom" })
        assertNotNull(catalog.first { it.key == "extension.yaml" }.icon)
    }

    fun testSettingsExportImportAndResetRoundTrip() {
        val state = EmberkaiIconsSettings.State().apply {
            colorServiceFolders = false
            moduleAwareColors = true
            semanticTooltips = false
            serviceFolderPalette = FolderIconPalette.ACCESSIBLE.name
            setEnabled(IconRuleCategory.HELM, false)
            setEnabled(IconRuleCategory.ARCHITECTURE, true)
        }
        val file = Files.createTempFile("emberkai-settings", ".properties")
        try {
            EmberkaiIconsSettings.export(state, file)
            val imported = EmberkaiIconsSettings.import(file)
            assertEquals("every exported setting round-trips", state, imported)
            assertFalse(imported.colorServiceFolders)
            assertTrue(imported.moduleAwareColors)
            assertFalse(imported.semanticTooltips)
            assertEquals(FolderIconPalette.ACCESSIBLE.name, imported.serviceFolderPalette)
            assertFalse(imported.isEnabled(IconRuleCategory.HELM))
            assertTrue(imported.isEnabled(IconRuleCategory.ARCHITECTURE))

            // Unknown or invalid values fall back to the defaults instead of failing the import.
            Files.writeString(file, "colorServiceFolders=maybe\nserviceFolderPalette=NOPE\nhelm=false\n")
            val partial = EmberkaiIconsSettings.import(file)
            val defaults = EmberkaiIconsSettings.State()
            assertEquals(defaults.colorServiceFolders, partial.colorServiceFolders)
            assertEquals(defaults.serviceFolderPalette, partial.serviceFolderPalette)
            assertFalse(partial.helm)
            assertEquals(defaults.architecture, partial.architecture)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    fun testMalformedOverridesAndMissingArtworkFallBackToBundledMappings() {
        writeOverrideFile(
            "extension.yaml=missing.svg\n" +
                "extension.md=/symbols/icons/files/does-not-exist.svg\n" +
                "extension.txt=../../outside.svg\n" +
                "file.broken=\\uZZZZ\n",
        )
        val yaml = resolve(myFixture.addFileToProject("plain.yaml", "a: 1").virtualFile)
        assertEquals(MappingSource.UPSTREAM, yaml.mappingSource)
        val markdown = resolve(myFixture.addFileToProject("notes.md", "# notes").virtualFile)
        assertNotSame(MappingSource.PROJECT_OVERRIDE, markdown.mappingSource)
        val text = resolve(myFixture.addFileToProject("notes.txt", "text").virtualFile)
        assertNotSame(MappingSource.PROJECT_OVERRIDE, text.mappingSource)
    }

    fun testArtworkPathsStayInsideTheOverrideDirectory() {
        assertEquals("nested/icon.svg", ProjectIconOverrideFile.relativeArtworkPath("nested\\icon.svg"))
        assertEquals("icon.svg", ProjectIconOverrideFile.relativeArtworkPath("./icon.svg"))
        assertNull(ProjectIconOverrideFile.relativeArtworkPath("../icon.svg"))
        assertNull(ProjectIconOverrideFile.relativeArtworkPath("nested/../../icon.svg"))
        assertNull(ProjectIconOverrideFile.relativeArtworkPath("C:\\icons\\icon.svg"))
        assertNull(ProjectIconOverrideFile.relativeArtworkPath("\\\\server\\icon.svg"))
        assertTrue(ProjectIconOverrideFile.isBundledArtwork("/symbols/icons/files/github.svg"))
        assertFalse(ProjectIconOverrideFile.isBundledArtwork("/META-INF/plugin.xml"))
        assertFalse(ProjectIconOverrideFile.isBundledArtwork("/symbols/icons/files/does-not-exist.svg"))
        assertEquals("folder.deploy/prod", ProjectIconOverrideFile.normalizeKey(" Folder.Deploy\\Prod "))
    }

    fun testEditsPreserveCommentsAndOtherEntries() {
        val original = "# team overrides\nextension.yaml=/a.svg\n\n! legacy\nfile.multi=first\\\n  second\n"
        val replaced = ProjectIconOverrideFile.update(original, "Extension.YAML", "b.svg")
        assertEquals("# team overrides\nextension.yaml=b.svg\n\n! legacy\nfile.multi=first\\\n  second\n", replaced)
        val removed = ProjectIconOverrideFile.update(replaced, "file.multi", null)
        assertEquals("# team overrides\nextension.yaml=b.svg\n\n! legacy\n", removed)
        val added = ProjectIconOverrideFile.update(removed, "file.with space", "my icon.svg")
        assertEquals(mapOf("extension.yaml" to "b.svg", "file.with space" to "my icon.svg"), ProjectIconOverrideFile.parse(added))
    }

    fun testUnsavedEditsToTheOverridesFileApplyImmediately() {
        writeOverrideFile("# empty\n")
        val file = myFixture.addFileToProject("service.custom", "x").virtualFile
        assertNotSame(MappingSource.PROJECT_OVERRIDE, resolve(file).mappingSource)

        val properties = ProjectIconOverrideStore.getInstance(project).propertiesFile()!!
        val document = FileDocumentManager.getInstance().getDocument(properties)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText("extension.custom=/symbols/icons/files/github.svg\n")
        }
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
        assertEquals(MappingSource.PROJECT_OVERRIDE, resolve(file).mappingSource)
        FileDocumentManager.getInstance().saveDocument(document)
    }

    fun testDeletingLocalArtworkOnDiskDropsTheOverride() {
        val svg = writeLocalFile(".idea/${ProjectIconOverrideStore.ICONS_DIRECTORY}/custom.svg", SVG)
        writeOverrideFile("extension.custom=custom.svg\n")
        val file = myFixture.addFileToProject("example.custom", "data").virtualFile
        assertEquals(MappingSource.PROJECT_OVERRIDE, resolve(file).mappingSource)

        WriteAction.run<IOException> { svg.delete(this) }
        assertNull(resolve(file).mappingSource)
    }

    fun testImportedArtworkIsCopiedWithItsDarkVariant() {
        val source = writeLocalFile("artwork-source/brand.svg", SVG)
        writeLocalFile("artwork-source/brand_dark.svg", SVG.replace("#2563eb", "#93c5fd"))
        val store = ProjectIconOverrideStore.getInstance(project)
        assertEquals("brand.svg", store.importArtwork(source))
        assertNotNull(store.iconsDirectory()?.findChild("brand_dark.svg"))
        // Re-importing identical artwork reuses the copy; artwork already inside the folder is referenced in place.
        assertEquals("brand.svg", store.importArtwork(source))
        assertEquals("brand.svg", store.importArtwork(store.iconsDirectory()!!.findChild("brand.svg")!!))
        Paths.get(project.basePath!!).resolve("artwork-source").toFile().deleteRecursively()
    }

    fun testOverriddenFilesAreDescribedInTooltips() {
        writeOverrideFile("extension.custom=/symbols/icons/files/github.svg\n")
        val file = myFixture.addFileToProject("a.custom", "x").virtualFile
        assertEquals("Project icon override", IconDescriptions.describe(file, resolve(file)))
    }

    fun testKeySuggestionsOnlyAcceptKeysThatMatchTheFile() {
        val file = myFixture.addFileToProject("deploy/prod/values.yaml", "a: 1").virtualFile
        assertTrue(IconOverrideKeySuggestions.matches(file, "file.values.yaml"))
        assertTrue(IconOverrideKeySuggestions.matches(file, "extension.yaml"))
        assertTrue(IconOverrideKeySuggestions.matches(file, "file.prod/values.yaml"))
        assertTrue(IconOverrideKeySuggestions.matches(file, "extension.deploy/prod/yaml"))
        assertFalse(IconOverrideKeySuggestions.matches(file, "file.staging/values.yaml"))
        assertFalse(IconOverrideKeySuggestions.matches(file, "folder.prod"))
        assertTrue(IconOverrideKeySuggestions.matches(file.parent, "folder.deploy/prod"))
    }

    private fun resolve(file: VirtualFile): IconResolution =
        ReadAction.computeBlocking<IconResolution, RuntimeException> {
            IconResolver.resolveWithDetails(file, project)
        }

    private fun writeOverrideFile(content: String) {
        writeLocalFile(".idea/${ProjectIconOverrideStore.PROPERTIES_NAME}", content)
        ProjectIconOverrideStore.getInstance(project).reload()
    }

    private fun writeLocalFile(relativePath: String, content: String): VirtualFile {
        val path = Paths.get(project.basePath!!).resolve(relativePath)
        Files.createDirectories(path.parent)
        if (!Files.exists(path)) Files.createFile(path)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString())
            ?: error("Could not load $path")
        try {
            WriteAction.run<IOException> { file.setBinaryContent(content.toByteArray(Charsets.UTF_8)) }
        } catch (exception: IOException) {
            error("Could not write $path: ${exception.message}")
        }
        return file
    }

    private companion object {
        const val SVG = """
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16">
              <rect width="16" height="16" fill="#2563eb"/>
            </svg>
        """
    }
}
