package com.github.khopland.emberkai

import com.github.khopland.emberkai.badges.EnvironmentVariant
import com.github.khopland.emberkai.badges.EnvironmentVariantBadgeIcon
import com.github.khopland.emberkai.core.IconMappings
import com.github.khopland.emberkai.core.SymbolsIconProvider
import com.github.khopland.emberkai.folders.ServiceFolderIcons
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.GraphicsEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.Icon

internal abstract class IconProviderTestCase : BasePlatformTestCase() {
    protected val provider = SymbolsIconProvider()

    override fun setUp() {
        super.setUp()
        // Model a checkout root. Individual tests mark real source/resource roots
        // explicitly instead of treating every fixture folder as Java source.
        ModuleRootModificationUtil.updateModel(module) { model ->
            for (entry in model.contentEntries) {
                for (source in entry.sourceFolders) entry.removeSourceFolder(source)
            }
        }
    }

    /** Top-level paths that [localDirectory] and [addLocalFile] created under the shared project base. */
    private val localPaths = mutableListOf<Path>()

    override fun tearDown() {
        try {
            // Light tests share one project directory, so files written there would leak into later tests.
            localPaths.forEach { it.toFile().deleteRecursively() }
            if (localPaths.isNotEmpty()) {
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectBase())
                    ?.let { VfsUtil.markDirtyAndRefresh(false, true, true, it) }
            }
            localPaths.clear()
        } finally {
            super.tearDown()
        }
    }

    /** A real directory under the project base, for rules that read the disk; removed after the test. */
    protected fun localDirectory(relativePath: String): VirtualFile {
        val path = localPath(relativePath)
        Files.createDirectories(path)
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
            ?: error("Could not load project directory: $path")
    }

    /** A real file under the project base; removed after the test. */
    protected fun addLocalFile(relativePath: String, content: String): VirtualFile {
        val path = localPath(relativePath)
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
            ?: error("Could not load project file: $path")
    }

    private fun localPath(relativePath: String): Path {
        val top = projectBase().resolve(relativePath.substringBefore('/'))
        if (!Files.exists(top) && top !in localPaths) localPaths.add(top)
        return projectBase().resolve(relativePath)
    }

    private fun projectBase(): Path = Paths.get(project.basePath!!)

    protected fun serviceFolderIcon(directory: VirtualFile): Icon? =
        IconMappings.get(ServiceFolderIcons.iconKey(directory, project))

    protected fun assertFileIcon(name: String, path: String) {
        val file = myFixture.configureByText(name, "data")
        assertIcon(path, provider.getIcon(file, 0))
        assertIcon(path, provider.getIcon(file.virtualFile, 0, project))
    }

    protected fun assertPathIcon(name: String, path: String) {
        val file = myFixture.addFileToProject(name, "data")
        assertIcon(path, provider.getIcon(file, 0))
        assertIcon(path, provider.getIcon(file.virtualFile, 0, project))
    }

    protected fun assertCustomPathIcon(name: String, icon: String) {
        val file = myFixture.addFileToProject(name, "data")
        assertResourceIcon("emberkai/icons/$icon", provider.getIcon(file, 0))
        assertResourceIcon("emberkai/icons/$icon", provider.getIcon(file.virtualFile, 0, project))
    }

    protected fun assertCustomFolderIcon(name: String, icon: String) {
        val file = myFixture.tempDirFixture.findOrCreateDir(name)
        val directory = PsiManager.getInstance(project).findDirectory(file)
        assertNotNull(directory)
        assertResourceIcon("emberkai/icons/$icon", provider.getIcon(directory!!, 0))
        assertResourceIcon("emberkai/icons/$icon", provider.getIcon(file, 0, project))
    }

    protected fun assertFolderIcon(name: String, path: String) {
        val virtualFile = myFixture.tempDirFixture.findOrCreateDir(name)
        val directory = PsiManager.getInstance(project).findDirectory(virtualFile)
        assertNotNull(directory)
        assertIcon(path, provider.getIcon(directory!!, 0))
        assertIcon(path, provider.getIcon(virtualFile, 0, project))
    }

    protected fun assertNativeFolder(name: String) {
        val file = myFixture.tempDirFixture.findOrCreateDir(name)
        val directory = PsiManager.getInstance(project).findDirectory(file)
        assertNotNull(directory)
        for (flags in intArrayOf(0, Iconable.ICON_FLAG_READ_STATUS)) {
            assertNull(name, provider.getIcon(directory!!, flags))
            assertNull(name, provider.getIcon(file, flags, project))
        }
    }

    protected fun assertIcon(path: String, actual: Icon?) {
        assertResourceIcon("symbols/icons/$path", actual)
    }

    protected fun assertResourceIcon(path: String, actual: Icon?) {
        assertNotNull(actual)
        assertSame(IconLoader.getIcon("/$path", SymbolsIconProvider::class.java), actual)
    }

    protected fun assertEnvironmentBadge(
        baseIconPath: String,
        expectedVariant: EnvironmentVariant,
        actual: Icon?,
    ) {
        val badge = actual as? EnvironmentVariantBadgeIcon
        assertNotNull("Expected an ${expectedVariant.displayName} environment badge", badge)
        assertEquals(expectedVariant, badge!!.variant)
        assertResourceIcon(baseIconPath, badge.baseIcon)
    }

    protected fun assertIconAtIdeSize(path: String, icon: Icon) {
        if (GraphicsEnvironment.isHeadless()) {
            // IconLoader may return a 1x1 placeholder in headless test runs. Check
            // the bundled SVG's declared size instead of the placeholder size.
            val resource = SymbolsIconProvider::class.java.getResourceAsStream("/$path")
            assertNotNull("Missing icon resource: $path", resource)
            val svg = resource!!.bufferedReader().use { it.readText() }
            val root = Regex("<svg\\b([^>]*)>").find(svg)?.groupValues?.get(1)
            assertNotNull("Invalid SVG resource: $path", root)
            assertEquals("$path width", "16", Regex("\\bwidth=\"([^\"]+)\"").find(root!!)?.groupValues?.get(1))
            assertEquals("$path height", "16", Regex("\\bheight=\"([^\"]+)\"").find(root)?.groupValues?.get(1))
        } else {
            assertEquals("$path width", 16, icon.iconWidth)
            assertEquals("$path height", 16, icon.iconHeight)
        }
    }

    protected fun assertNativeIcons(name: String, content: String) {
        val file = myFixture.configureByText(name, content)
        val element = file.findElementAt(content.indexOf("class").coerceAtLeast(0))
        assertNotNull(element)
        for (flags in intArrayOf(0, Iconable.ICON_FLAG_VISIBILITY, Iconable.ICON_FLAG_READ_STATUS)) {
            assertNull(name, provider.getIcon(file, flags))
            assertNull(name, provider.getIcon(element!!, flags))
            assertNull(name, provider.getIcon(file.virtualFile, flags, project))
        }
    }
}
