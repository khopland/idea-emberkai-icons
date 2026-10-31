package com.github.khopland.symbols

import com.github.khopland.symbols.lens.IconLensService
import com.github.khopland.symbols.lens.ModuleDependentsLens
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import java.nio.file.Files

/** Uses real modules, which light tests cannot create. */
internal class ModuleDependentsLensTest : HeavyPlatformTestCase() {
    override fun tearDown() {
        try {
            IconLensService.getIfCreated(project)?.select(null)
        } finally {
            super.tearDown()
        }
    }

    fun testLensCoversTheModuleAndEveryModuleThatDependsOnItTransitively() {
        val core = moduleWithFile("core")
        val api = moduleWithFile("api")
        val app = moduleWithFile("app")
        val tools = moduleWithFile("tools")
        ModuleRootModificationUtil.addDependency(api.first, core.first)
        ModuleRootModificationUtil.addDependency(app.first, api.first)
        ModuleRootModificationUtil.addDependency(core.first, tools.first)

        val lens = ModuleDependentsLens("core")
        assertEquals(setOf("core", "api", "app"), lens.modules(project))
        assertTrue(lens.contains(core.second, project))
        assertTrue("app reaches core through api", lens.contains(app.second, project))
        assertFalse("core depends on tools, not the other way round", lens.contains(tools.second, project))
        assertFalse("folders are never members", lens.contains(core.second.parent, project))

        ModuleRootModificationUtil.addDependency(tools.first, app.first)
        assertTrue("a new dependency is picked up", lens.contains(tools.second, project))
        assertEquals(emptySet<String>(), ModuleDependentsLens("missing").modules(project))
    }

    fun testSelectingTheLensHighlightsDependentFiles() {
        val core = moduleWithFile("core")
        val app = moduleWithFile("app")
        val other = moduleWithFile("other")
        ModuleRootModificationUtil.addDependency(app.first, core.first)
        val service = IconLensService.getInstance(project)

        PlatformTestUtil.waitForPromise(service.select(ModuleDependentsLens("core"))!!)

        val snapshot = service.snapshot!!
        assertEquals(2, snapshot.memberCount)
        assertTrue(snapshot.highlights(app.second))
        assertFalse(snapshot.highlights(other.second))
        assertNull("an equal lens is the same lens", service.select(ModuleDependentsLens("core")))
    }

    fun testDependentsClosureFollowsChainsAndCycles() {
        val graph = mapOf("a" to listOf("b"), "b" to listOf("c", "a"), "c" to listOf("b"), "d" to listOf("a"))
        assertEquals(setOf("a", "b", "c"), ModuleDependentsLens.dependentsClosure("a") { graph[it].orEmpty() })
        assertEquals(setOf("d", "a", "b", "c"), ModuleDependentsLens.dependentsClosure("d") { graph[it].orEmpty() })
    }

    private fun moduleWithFile(name: String): Pair<Module, VirtualFile> {
        val root = tempDir.newPath(name).also(Files::createDirectories)
        Files.writeString(root.resolve("Main.txt"), name)
        val directory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root)!!
        val module = createModule(name)
        PsiTestUtil.addContentRoot(module, directory)
        return module to directory.findChild("Main.txt")!!
    }
}
