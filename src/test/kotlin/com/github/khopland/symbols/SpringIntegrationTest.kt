package com.github.khopland.symbols

import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.setEnabled
import com.github.khopland.symbols.spring.ModulithFolderIcon
import com.github.khopland.symbols.spring.ModulithLock
import com.github.khopland.symbols.spring.ModulithSupport
import com.github.khopland.symbols.spring.SpringModulithFolderDecorator
import com.github.khopland.symbols.spring.SpringModulithSupport
import com.github.khopland.symbols.spring.SpringXmlConfigDetector
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.Icon
import org.jetbrains.jps.model.java.JavaSourceRootType

internal class SpringIntegrationTest : IconProviderTestCase() {
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

    fun testSpringDomDescriptionDetection() {
        assertTrue(SpringXmlConfigDetector.isSpringDescription("com.intellij.spring.dom.SpringDomFileDescription"))
        assertTrue(SpringXmlConfigDetector.isSpringDescription("com.intellij.spring.boot.SomeDescription"))
        assertFalse(SpringXmlConfigDetector.isSpringDescription("com.intellij.util.xml.DomFileDescription"))
        assertFalse(SpringXmlConfigDetector.isSpringDescription("com.example.spring.MyDescription"))
    }

    fun testPlainXmlKeepsEmberkaiArtwork() {
        val file = myFixture.addFileToProject("config.xml", "<root/>")
        assertIcon("files/xml.svg", provider.getIcon(file, 0))
        assertIcon("files/xml.svg", provider.getIcon(file.virtualFile, 0, project))
    }

    fun testModulithDecoratorWrapsFrameworkLockIcon() {
        val springLock = TestIcon()
        val data = PresentationData()
        data.setIcon(springLock)
        data.setLocationString("Orders")

        decorator(FakeModulithSupport(mapOf(springLock to ModulithLock.NESTED)))
            .decorate(packageNode(), data)

        val icon = data.getIcon(false)
        assertTrue(icon is ModulithFolderIcon)
        assertEquals(ModulithLock.NESTED, (icon as ModulithFolderIcon).lock)
        assertEquals("Orders", data.getLocationString())
    }

    fun testModulithDecoratorRestoresClearedPackageIcon() {
        val data = PresentationData()
        data.setIcon(null)

        decorator(FakeModulithSupport(library = true)).decorate(packageNode(), data)

        assertResourceIcon("emberkai/icons/folders/folder-package.svg", data.getIcon(false))
    }

    fun testModulithDecoratorLeavesClearedIconWithoutLibrary() {
        val data = PresentationData()
        data.setIcon(null)

        decorator(FakeModulithSupport(library = false)).decorate(packageNode(), data)

        assertNull(data.getIcon(false))
    }

    fun testModulithDecoratorLeavesUnrelatedIconsAlone() {
        val existing = TestIcon()
        val data = PresentationData()
        data.setIcon(existing)

        decorator(FakeModulithSupport(mapOf(TestIcon() to ModulithLock.PUBLIC), library = true))
            .decorate(packageNode(), data)

        assertSame(existing, data.getIcon(false))
    }

    fun testModulithDecoratorHonorsPackageLockSetting() {
        settings.setEnabled(IconRuleCategory.SPRING_MODULITH, false)
        val springLock = TestIcon()
        val data = PresentationData()
        data.setIcon(springLock)

        decorator(FakeModulithSupport(mapOf(springLock to ModulithLock.PUBLIC)))
            .decorate(packageNode(), data)

        assertSame(springLock, data.getIcon(false))
    }

    fun testModulithFolderIconPaintsBadgeInLockColor() {
        for (lock in ModulithLock.entries) {
            val icon = ModulithFolderIcon(TestIcon(), lock)
            assertEquals(16, icon.iconWidth)
            assertEquals(16, icon.iconHeight)

            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try {
                icon.paintIcon(null, graphics, 0, 0)
            } finally {
                graphics.dispose()
            }
            assertEquals("$lock badge", lock.color.rgb, image.getRGB(12, 12))
        }
    }

    fun testModulithSupportIgnoresUnrelatedIcons() {
        assertNull(SpringModulithSupport.lockOf(null))
        assertNull(SpringModulithSupport.lockOf(TestIcon()))
    }

    private fun decorator(support: ModulithSupport): SpringModulithFolderDecorator =
        SpringModulithFolderDecorator(support)

    private fun packageNode(): PsiDirectoryNode {
        val source = myFixture.tempDirFixture.findOrCreateDir("src")
        PsiTestUtil.addSourceRoot(module, source, JavaSourceRootType.SOURCE)
        val directory = myFixture.tempDirFixture.findOrCreateDir("src/com/example")
        val psiDirectory = PsiManager.getInstance(project).findDirectory(directory)
        assertNotNull(psiDirectory)
        return PsiDirectoryNode(project, psiDirectory!!, ViewSettings.DEFAULT)
    }

    private class FakeModulithSupport(
        private val locks: Map<Icon, ModulithLock> = emptyMap(),
        private val library: Boolean = false,
    ) : ModulithSupport {
        override fun lockOf(icon: Icon?): ModulithLock? = icon?.let(locks::get)

        override fun hasModulithLibrary(directory: PsiDirectory): Boolean = library
    }

    private class TestIcon(
        private val width: Int = 16,
        private val height: Int = 16,
    ) : Icon {
        override fun getIconWidth(): Int = width

        override fun getIconHeight(): Int = height

        override fun paintIcon(component: Component?, g: Graphics, x: Int, y: Int) = Unit
    }
}
