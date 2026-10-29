package com.github.khopland.symbols

import com.github.khopland.symbols.lens.IconLens
import com.github.khopland.symbols.lens.IconLensDecorator
import com.github.khopland.symbols.lens.IconLensService
import com.github.khopland.symbols.lens.LensSnapshot
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.SimpleTextAttributes
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

internal class IconLensTest : IconProviderTestCase() {
    override fun tearDown() {
        try {
            IconLensService.getIfCreated(project)?.select(null)
        } finally {
            super.tearDown()
        }
    }

    fun testBlastRadiusCoversProductionConfigAndMigrations() {
        val lens = IconLens.PRODUCTION_BLAST_RADIUS
        assertTrue(contains(lens, "deploy/docker-compose.prod.yml"))
        assertTrue(contains(lens, ".env.production"))
        assertTrue(contains(lens, "db/migration/V1__init.sql"))
        assertFalse(contains(lens, "docker-compose.yml"))
        assertFalse(contains(lens, "README.md"))
        assertFalse(contains(IconLens.API_CONTRACTS, "db/migration/V2__more.sql"))
    }

    fun testInfrastructureLensIncludesKubernetes() {
        myFixture.addFileToProject("k8s/deployment.yaml", "apiVersion: apps/v1\nkind: Deployment\n")
        assertTrue(contains(IconLens.INFRASTRUCTURE, "k8s/deployment.yaml"))
        assertTrue(contains(IconLens.INFRASTRUCTURE, "infra/main.tf"))
    }

    fun testSnapshotHighlightsMembersAndTheFoldersThatContainThem() {
        val migration = myFixture.addFileToProject("service/db/migration/V1__init.sql", "select 1").virtualFile
        val sibling = myFixture.addFileToProject("service/src/App.txt", "data").virtualFile
        val other = myFixture.addFileToProject("docs/guide.md", "# guide").virtualFile

        val snapshot = runReadAction { LensSnapshot.collect(project, IconLens.MIGRATIONS) }

        assertEquals(1, snapshot.memberCount)
        assertTrue(snapshot.highlights(migration))
        assertTrue(snapshot.highlights(migration.parent))
        assertTrue("service/ contains a migration", snapshot.highlights(migration.parent.parent.parent))
        assertFalse(snapshot.highlights(sibling))
        assertFalse("service/src/ contains no migration", snapshot.highlights(sibling.parent))
        assertFalse(snapshot.highlights(other.parent))
    }

    fun testDecoratorDimsNodesOutsideTheActiveLensOnly() {
        val migration = myFixture.addFileToProject("db/migration/V1__init.sql", "select 1")
        val readme = myFixture.addFileToProject("README.md", "# readme")
        val docs = myFixture.tempDirFixture.findOrCreateDir("docs")
        val service = IconLensService.getInstance(project)

        assertSame("No lens leaves nodes alone", ICON, decorate(PsiFileNode(project, readme, ViewSettings.DEFAULT)).getIcon(false))

        PlatformTestUtil.waitForPromise(service.select(IconLens.MIGRATIONS)!!)
        assertEquals(IconLens.MIGRATIONS, service.snapshot?.lens)

        val member = decorate(PsiFileNode(project, migration, ViewSettings.DEFAULT))
        assertSame(ICON, member.getIcon(false))
        assertNull(member.forcedTextForeground)

        val dimmed = decorate(PsiFileNode(project, readme, ViewSettings.DEFAULT))
        assertEquals(SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor, dimmed.forcedTextForeground)
        assertEquals("Outside the Database migrations lens", dimmed.tooltip)
        // Headless runs have no disabled variant, and getDisabledIcon returns the icon itself.
        assertEquals(IconLoader.getDisabledIcon(ICON), dimmed.getIcon(false))

        val folder = PsiDirectoryNode(project, PsiManager.getInstance(project).findDirectory(docs)!!, ViewSettings.DEFAULT)
        assertEquals("Outside the Database migrations lens", decorate(folder).tooltip)

        service.select(null)
        assertNull(service.snapshot)
        assertSame(ICON, decorate(PsiFileNode(project, readme, ViewSettings.DEFAULT)).getIcon(false))
    }

    fun testColoredTextIsGrayedWhenDimmed() {
        val readme = myFixture.addFileToProject("README.md", "# readme")
        myFixture.addFileToProject("openapi.yaml", "openapi: 3.1.0\ninfo: {title: API}")
        PlatformTestUtil.waitForPromise(IconLensService.getInstance(project).select(IconLens.API_CONTRACTS)!!)

        val data = PresentationData().apply {
            setIcon(ICON)
            addText("README.md", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
        IconLensDecorator().decorate(PsiFileNode(project, readme, ViewSettings.DEFAULT), data)

        assertEquals(listOf("README.md"), data.coloredText.map { it.text })
        assertEquals(SimpleTextAttributes.GRAYED_ATTRIBUTES, data.coloredText.single().attributes)
    }

    private fun decorate(node: ProjectViewNode<*>): PresentationData =
        PresentationData().apply {
            setIcon(ICON)
            IconLensDecorator().decorate(node, this)
        }

    private fun contains(lens: IconLens, path: String): Boolean {
        val file = myFixture.findFileInTempDir(path) ?: myFixture.addFileToProject(path, "data").virtualFile
        return lens.contains(file, project)
    }

    private companion object {
        val ICON: Icon = object : Icon {
            override fun paintIcon(component: Component?, g: Graphics, x: Int, y: Int) {
                g.color = Color.ORANGE
                g.fillRect(x, y, iconWidth, iconHeight)
            }

            override fun getIconWidth(): Int = 16

            override fun getIconHeight(): Int = 16
        }
    }
}
