package com.github.khopland.symbols

import com.github.khopland.symbols.git.BranchBase
import com.github.khopland.symbols.lens.BranchChanges
import com.github.khopland.symbols.lens.ChangedOnBranchLens
import com.github.khopland.symbols.lens.GitBranchChanges
import com.github.khopland.symbols.lens.IconLens
import com.github.khopland.symbols.lens.IconLensActionGroup
import com.github.khopland.symbols.lens.IconLensService
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.registerOrReplaceServiceInstance

internal class ChangedOnBranchLensTest : IconProviderTestCase() {
    private val changes = FakeBranchChanges()

    override fun tearDown() {
        try {
            IconLensService.getIfCreated(project)?.select(null)
        } finally {
            super.tearDown()
        }
    }

    fun testBaseIsTheDefaultBranchThePullRequestTargets() {
        assertEquals("upstream/main", BranchBase.choose("origin/main", setOf("origin/main", "upstream/main"), setOf("main")))
        assertEquals("origin/develop", BranchBase.choose("origin/develop", setOf("origin/develop", "origin/main"), emptySet()))
        assertEquals("origin/master", BranchBase.choose(null, setOf("origin/master", "origin/feature"), setOf("master")))
        assertEquals("main", BranchBase.choose(null, emptySet(), setOf("feature", "main")))
        assertEquals("origin/main", BranchBase.choose("origin/HEAD", setOf("origin/main"), emptySet()))
        assertNull(BranchBase.choose(null, setOf("origin/feature"), setOf("feature")))
    }

    fun testGitServiceIsRegisteredAndTheLensIsOffered() {
        assertTrue(BranchChanges.getInstance(project) is GitBranchChanges)
        assertTrue(lensMenu().contains(ChangedOnBranchLens.displayName))
    }

    fun testNothingIsDimmedUntilTheComparisonFinishes() {
        project.registerOrReplaceServiceInstance(BranchChanges::class.java, changes, testRootDisposable)
        val changed = myFixture.addFileToProject("service/src/Changed.kt", "class Changed").virtualFile
        val unchanged = myFixture.addFileToProject("docs/guide.md", "# guide").virtualFile
        val service = IconLensService.getInstance(project)

        PlatformTestUtil.waitForPromise(service.select(ChangedOnBranchLens)!!)
        assertNull("Not ready, so nothing is dimmed", service.snapshot)
        assertTrue("The lens asked for a comparison", changes.requests > 0)

        changes.files = setOf(changed)
        service.membershipChanged(ChangedOnBranchLens)
        PlatformTestUtil.waitWithEventsDispatching("lens snapshot", { service.snapshot != null }, 10)

        val snapshot = service.snapshot!!
        assertEquals(1, snapshot.memberCount)
        assertTrue(snapshot.highlights(changed))
        assertTrue(snapshot.highlights(changed.parent.parent))
        assertFalse(snapshot.highlights(unchanged))
        assertFalse(snapshot.highlights(unchanged.parent))
    }

    fun testMembershipFollowsTheComparison() {
        project.registerOrReplaceServiceInstance(BranchChanges::class.java, changes, testRootDisposable)
        val first = myFixture.addFileToProject("First.kt", "class First").virtualFile
        val second = myFixture.addFileToProject("Second.kt", "class Second").virtualFile
        changes.files = setOf(first)
        val service = IconLensService.getInstance(project)
        PlatformTestUtil.waitForPromise(service.select(ChangedOnBranchLens)!!)
        assertTrue(service.snapshot!!.highlights(first))

        changes.files = setOf(first, second)
        service.membershipChanged(ChangedOnBranchLens)
        PlatformTestUtil.waitWithEventsDispatching("updated snapshot", { service.snapshot?.memberCount == 2 }, 10)
        assertTrue(service.snapshot!!.highlights(second))

        // Another lens does not recompute when branch changes report in.
        PlatformTestUtil.waitForPromise(service.select(IconLens.MIGRATIONS)!!)
        service.membershipChanged(ChangedOnBranchLens)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(IconLens.MIGRATIONS, service.snapshot?.lens)
    }

    fun testLeavingTheLensForgetsTheComparison() {
        project.registerOrReplaceServiceInstance(BranchChanges::class.java, changes, testRootDisposable)
        changes.files = emptySet()
        val service = IconLensService.getInstance(project)
        PlatformTestUtil.waitForPromise(service.select(ChangedOnBranchLens)!!)
        assertEquals(0, changes.forgotten)

        service.select(null)
        assertEquals(1, changes.forgotten)
        assertNull(changes.files)
    }

    fun testFoldersAreNeverMembers() {
        project.registerOrReplaceServiceInstance(BranchChanges::class.java, changes, testRootDisposable)
        val folder = myFixture.tempDirFixture.findOrCreateDir("changed")
        changes.files = setOf(folder)
        assertFalse(ChangedOnBranchLens.contains(folder, project))
    }

    private fun lensMenu(): List<String> {
        val context = SimpleDataContext.getProjectContext(project)
        val event = TestActionEvent.createTestEvent(context)
        return IconLensActionGroup().getChildren(event).mapNotNull { it.templatePresentation.text }
    }

    private class FakeBranchChanges : BranchChanges {
        @Volatile
        var files: Set<VirtualFile>? = null
        var requests = 0
        var forgotten = 0

        override fun changedFiles(): Set<VirtualFile>? = files.also { if (it == null) requests++ }

        override fun forget() {
            forgotten++
            files = null
        }
    }
}
