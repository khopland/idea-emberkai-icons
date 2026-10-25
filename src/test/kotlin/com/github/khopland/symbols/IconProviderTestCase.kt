package com.github.khopland.symbols

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
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
