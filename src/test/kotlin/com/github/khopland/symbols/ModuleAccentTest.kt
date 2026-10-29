package com.github.khopland.symbols

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.nio.file.Files
import java.nio.file.Paths

internal class ModuleAccentTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null

    override fun setUp() {
        super.setUp()
        settings = EmberkaiIconsSettings.getInstance()
        savedState = settings.state
        ModuleAccentStore.getInstance(project).clearAll()
    }

    override fun tearDown() {
        savedState?.let(settings::loadState)
        super.tearDown()
    }

    fun testAutomaticColorIsStableAndFollowsThePaletteOrder() {
        assertEquals(
            ModuleAccentColor.defaultFor("project-api", FolderIconPalette.EMBERKAI),
            ModuleAccentColor.defaultFor("PROJECT-API", FolderIconPalette.EMBERKAI),
        )

        val index = Math.floorMod("project-api".hashCode(), 8)
        assertEquals(
            FolderIconPalette.ACCESSIBLE.colors[index],
            ModuleAccentColor.defaultFor("project-api", FolderIconPalette.ACCESSIBLE),
        )
    }

    fun testOverridesAreProjectStateAndCanBeReset() {
        val store = ModuleAccentStore.getInstance(project)
        val automatic = store.colorFor("module-a", FolderIconPalette.EMBERKAI)
        assertNull(store.overrideFor("module-a"))

        store.setOverride("module-a", ModuleAccentColor.RED)
        assertEquals(ModuleAccentColor.RED, store.overrideFor("module-a"))

        val snapshot = store.state
        snapshot.overrides["module-a"] = ModuleAccentColor.BLUE.id
        assertEquals("getState returns a snapshot", ModuleAccentColor.RED, store.overrideFor("module-a"))

        val loaded = ModuleAccentStore.State()
        loaded.overrides["module-b"] = ModuleAccentColor.GREEN.id
        store.loadState(loaded)
        assertNull(store.overrideFor("module-a"))
        assertEquals(ModuleAccentColor.GREEN, store.overrideFor("module-b"))
        loaded.overrides["module-b"] = ModuleAccentColor.BLUE.id
        assertEquals("loadState keeps its own copy", ModuleAccentColor.GREEN, store.overrideFor("module-b"))

        store.clearAll()
        assertEquals(automatic, store.colorFor("module-a", FolderIconPalette.EMBERKAI))
    }

    fun testModuleAwareServiceRootsShareOneAccentAndHonorOverrides() {
        settings.colorServiceFolders = true
        settings.moduleAwareColors = true
        val services = localDirectory("services")
        val alpha = localDirectory("services/alpha")
        val beta = localDirectory("services/beta")
        addLocalFile("services/alpha/build.gradle.kts", "plugins {}")
        addLocalFile("services/beta/package.json", "{}")
        ModuleRootModificationUtil.updateModel(module) { model -> model.addContentEntry(services) }

        val moduleName = ProjectFileIndex.getInstance(project).getModuleForFile(alpha)?.name
        assertNotNull(moduleName)
        val store = ModuleAccentStore.getInstance(project)

        val alphaKey = ServiceFolderIcons.iconKey(alpha, project)
        val betaKey = ServiceFolderIcons.iconKey(beta, project)
        assertNotNull(alphaKey)
        assertEquals("sibling roots in one module share the module color", alphaKey, betaKey)
        assertEquals(
            ServiceFolderIconStyle.apply(
                store.colorFor(moduleName!!, FolderIconPalette.EMBERKAI).iconKey,
                settings,
            ),
            alphaKey,
        )

        store.setOverride(moduleName, ModuleAccentColor.RED)
        assertEquals("folder.service.red", ServiceFolderIcons.iconKey(alpha, project))
        assertResourceIcon("symbols/icons/folders/folder-red.svg", serviceFolderIcon(alpha))
    }

    fun testModuleAccentBadgeDecoratesSourcePackagesAndUsesOverrides() {
        val source = myFixture.tempDirFixture.findOrCreateDir("src/main/kotlin")
        PsiTestUtil.addSourceRoot(module, source, JavaSourceRootType.SOURCE)
        val packageDirectory = myFixture.tempDirFixture.findOrCreateDir("src/main/kotlin/com/example")
        val moduleName = ProjectFileIndex.getInstance(project).getModuleForFile(packageDirectory)?.name
        assertNotNull(moduleName)

        settings.moduleAwareColors = false
        assertFalse(provider.getIcon(packageDirectory, 0, project) is ModuleAccentIcon)

        settings.moduleAwareColors = true
        val store = ModuleAccentStore.getInstance(project)
        val automatic = provider.getIcon(packageDirectory, 0, project)
        assertTrue("module-aware packages carry an accent badge", automatic is ModuleAccentIcon)
        assertEquals(
            store.colorFor(moduleName!!, settings.serviceFolderPalette),
            (automatic as ModuleAccentIcon).accent,
        )

        store.setOverride(moduleName, ModuleAccentColor.GREEN)
        val overridden = provider.getIcon(packageDirectory, 0, project)
        assertTrue(overridden is ModuleAccentIcon)
        assertEquals(ModuleAccentColor.GREEN, (overridden as ModuleAccentIcon).accent)
    }

    fun testServiceRootsDoNotGetADoubleBadge() {
        settings.colorServiceFolders = true
        settings.moduleAwareColors = true
        val service = localDirectory("services/catalog")
        addLocalFile("services/catalog/build.gradle.kts", "plugins {}")

        val icon = provider.getIcon(service, 0, project)
        assertNotNull(icon)
        assertFalse("service roots use the full accent color instead of a badge", icon is ModuleAccentIcon)
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
}
