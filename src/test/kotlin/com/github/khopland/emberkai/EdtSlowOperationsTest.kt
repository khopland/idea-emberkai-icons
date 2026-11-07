package com.github.khopland.emberkai

import com.github.khopland.emberkai.badges.RegistryToken
import com.github.khopland.emberkai.badges.RegistryTokens
import com.github.khopland.emberkai.core.IconResolver
import com.github.khopland.emberkai.lens.IconLens
import com.github.khopland.emberkai.node.StereotypeBadgeIcon
import com.github.khopland.emberkai.overrides.ProjectIconOverrideStore
import com.github.khopland.emberkai.settings.EmberkaiScope
import com.github.khopland.emberkai.settings.EmberkaiScopesProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.SlowOperations
import java.nio.file.Files
import java.nio.file.Path

/**
 * EDT code that runs outside an action, such as a dialog button, a list selection, or a scope
 * picker, must not load VFS children or file content from disk: the IDE reports that as "Slow
 * operations are prohibited on EDT". Unit-test mode allows it, and light fixtures keep files in
 * memory where nothing is slow, so these tests use a project on disk whose folders the VFS has not
 * loaded yet.
 */
internal class EdtSlowOperationsTest : HeavyPlatformTestCase() {
    private lateinit var base: Path

    override fun setUp() {
        super.setUp()
        base = Path.of(project.basePath!!)
        write(".github/CODEOWNERS", "* @org/platform\n")
        write("docs/README.md", "")
        val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base)!!
        PsiTestUtil.addContentRoot(module, root)
    }

    fun testScopePickerListsScopesWithoutReadingCodeOwners() {
        onEdtOutsideAnAction { EmberkaiScopesProvider(project).customScopes }
    }

    /**
     * Explain This Icon, the override dialog, and the coverage audit read and write overrides from
     * button and key listeners, including right after a save or an edit dropped the cached overrides.
     */
    fun testOverrideDialogsEditOverridesFromUiEvents() {
        write(".idea/emberkai-icons.properties", "file.readme.md=/symbols/icons/files/github.svg\n")
        write("artwork/brand.svg", SVG)
        write("artwork/brand_dark.svg", SVG)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base.resolve(".idea"))
        val artwork = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base.resolve("artwork/brand.svg"))!!
        val store = ProjectIconOverrideStore.getInstance(project)

        onEdtOutsideAnAction {
            assertEquals("/symbols/icons/files/github.svg", store.overrides()["file.readme.md"])
            assertEquals("brand.svg", store.importArtwork(artwork))
            assertTrue(store.putOverride("extension.md", "brand.svg"))
            assertEquals(setOf("file.readme.md", "extension.md"), store.overrides().keys)
            assertTrue(store.removeOverride("file.readme.md"))
            assertEquals(setOf("extension.md"), store.overrides().keys)
            assertNotNull(store.iconsDirectory()?.findChild("brand_dark.svg"))
        }
    }

    /**
     * The Project view computes icons in the background, but editor tabs and scope-based file colors
     * ask on the EDT. There, TypeScript stereotypes come only from what a background caller cached.
     */
    fun testNodeStereotypesOnTheEdtUseOnlyCachedResults() {
        write("api/package.json", """{ "dependencies": { "@nestjs/common": "11.0.0" } }""")
        write("api/src/orders.controller.ts", "import { Controller } from '@nestjs/common';\n@Controller()\nexport class Orders {}\n")
        val controller = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base.resolve("api/src/orders.controller.ts"))!!

        onEdtOutsideAnAction {
            assertFalse("Nothing is cached yet", EmberkaiScope.ENTRY_POINTS.contains(controller, project))
            assertFalse(IconResolver.resolve(controller, project) is StereotypeBadgeIcon)
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            assertTrue(EmberkaiScope.ENTRY_POINTS.contains(controller, project))
        }.get()

        onEdtOutsideAnAction {
            assertTrue("A background lookup warmed the caches", EmberkaiScope.ENTRY_POINTS.contains(controller, project))
            assertTrue(IconResolver.resolve(controller, project) is StereotypeBadgeIcon)
        }
    }

    fun testRegistryTokensOnTheEdtUseOnlyCachedResults() {
        write("api/.npmrc", "//registry.npmjs.org/:_authToken=npm_abc123\n")
        val npmrc = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base.resolve("api/.npmrc"))!!

        onEdtOutsideAnAction {
            assertEquals(emptyList<RegistryToken>(), RegistryTokens.literal(npmrc))
            IconResolver.resolve(npmrc, project)
            IconLens.PRODUCTION_BLAST_RADIUS.contains(npmrc, project)
        }
        ApplicationManager.getApplication().executeOnPooledThread { RegistryTokens.literal(npmrc) }.get()
        onEdtOutsideAnAction {
            assertEquals(listOf(RegistryToken(1, "_authToken")), RegistryTokens.literal(npmrc))
        }
    }

    private fun write(path: String, text: String) {
        base.resolve(path).also { Files.createDirectories(it.parent) }.let { Files.writeString(it, text) }
    }

    private fun onEdtOutsideAnAction(block: () -> Unit) {
        System.setProperty(SlowOperations.FORBID_SLOW_OPS_PROPERTY, "true")
        try {
            // Like a Swing event the IDE dispatches: no enclosing action section exempts the block.
            SlowOperations.startSection(SlowOperations.RESET).use { block() }
        } finally {
            System.clearProperty(SlowOperations.FORBID_SLOW_OPS_PROPERTY)
        }
    }

    private companion object {
        const val SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\"/>"
    }
}
