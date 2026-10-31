package com.github.khopland.symbols

import com.github.khopland.symbols.badges.FlywayMigrations
import com.github.khopland.symbols.badges.MigrationBranchBase
import com.github.khopland.symbols.badges.OutOfOrderMigration
import com.github.khopland.symbols.git.GitQueries
import com.github.khopland.symbols.lens.BranchChanges
import com.github.khopland.symbols.lens.ChangedOnBranchLens
import com.github.khopland.symbols.lens.Hotspots
import com.github.khopland.symbols.lens.HotspotsLens
import com.github.khopland.symbols.lens.IconLensService
import com.github.khopland.symbols.ownership.BranchReviewers
import com.github.khopland.symbols.ownership.ChangedPath
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import git4idea.repo.GitRepositoryManager
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Runs the real Git comparison against a repository on disk. */
internal class GitBranchChangesTest : HeavyPlatformTestCase() {
    private lateinit var repo: Path

    override fun setUp() {
        super.setUp()
        repo = tempDir.newPath("repo").also(Files::createDirectories)
        git("init", "-q", "-b", "main")
        git("config", "user.email", "test@example.com")
        git("config", "user.name", "Test")
        write("committed.txt", "one")
        write("untouched.txt", "same")
        write("edited.txt", "before")
        git("add", ".")
        git("commit", "-q", "-m", "base")
        git("checkout", "-q", "-b", "feature")
        write("committed.txt", "two")
        git("commit", "-q", "-am", "feature work")
        write("edited.txt", "after")
        write("new.txt", "untracked")

        val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(repo)!!
        VfsUtil.markDirtyAndRefresh(false, true, true, root)
        PsiTestUtil.addContentRoot(module, root)
        ProjectLevelVcsManager.getInstance(project).directoryMappings = listOf(VcsDirectoryMapping(root.path, "Git"))
        PlatformTestUtil.waitWithEventsDispatching(
            "Git repository",
            { GitRepositoryManager.getInstance(project).repositories.isNotEmpty() },
            30,
        )
        ChangeListManagerImpl.getInstanceImpl(project).ensureUpToDate()
    }

    override fun tearDown() {
        try {
            IconLensService.getIfCreated(project)?.select(null)
        } finally {
            super.tearDown()
        }
    }

    fun testComparesWithTheDefaultBranchIncludingUncommittedAndUntrackedFiles() {
        val service = IconLensService.getInstance(project)
        service.select(ChangedOnBranchLens)
        PlatformTestUtil.waitWithEventsDispatching("lens snapshot", { service.snapshot != null }, 30)

        val changed = BranchChanges.getInstance(project)!!.changedFiles()!!.map(VirtualFile::getName).toSet()
        assertEquals(setOf("committed.txt", "edited.txt", "new.txt"), changed)
        assertEquals(3, service.snapshot!!.memberCount)

        // Leaving the lens forgets the comparison instead of tracking the repository in the background.
        service.select(null)
        assertNull(BranchChanges.getInstance(project)!!.changedFiles())
    }

    fun testReviewersAreTheOwnersOfEveryChangedPathIncludingDeletedOnes() {
        write("CODEOWNERS", "* @org/all\n/committed.txt @org/payments\nnew.txt\n")
        git("rm", "-q", "untouched.txt")
        git("commit", "-q", "-m", "remove")
        VfsUtil.markDirtyAndRefresh(false, true, true, LocalFileSystem.getInstance().refreshAndFindFileByNioFile(repo)!!)

        val repository = GitRepositoryManager.getInstance(project).repositories.single()
        val diff = GitQueries.branchDiff(project, repository)
        assertEquals("main", diff.baseRef)
        assertEquals(setOf("CODEOWNERS", "committed.txt", "edited.txt", "new.txt", "untouched.txt"), diff.paths.toSet())

        val reviewers = runReadAction { BranchReviewers.of(diff.paths.map { ChangedPath(diff.root, it) }) }
        assertEquals(listOf("@org/all", "@org/payments"), reviewers.owners.map { it.first })
        assertEquals(
            "The deleted file still needs its owner's review",
            listOf("CODEOWNERS", "edited.txt", "untouched.txt"),
            reviewers.owners.first().second.map { it.path },
        )
        assertEquals(listOf("committed.txt"), reviewers.owners.last().second.map { it.path })
        assertEquals(listOf("new.txt"), reviewers.unowned.map { it.path })
        assertTrue(reviewers.ungoverned.isEmpty())
        assertEquals(5, reviewers.changedCount)
    }

    fun testHotspotsAreFilesChangedInSeveralRecentCommits() {
        val service = IconLensService.getInstance(project)
        service.select(HotspotsLens)
        PlatformTestUtil.waitWithEventsDispatching("lens snapshot", { service.snapshot != null }, 30)

        // committed.txt is the only file in two commits; one edit does not make a hotspot.
        val hotspots = Hotspots.getInstance(project)!!.files()!!.map(VirtualFile::getName).toSet()
        assertEquals(setOf("committed.txt"), hotspots)
        assertEquals(1, service.snapshot!!.memberCount)

        service.select(null)
        assertNull(Hotspots.getInstance(project)!!.files())
    }

    fun testHotspotRankingKeepsTiesAndSkipsSingleCommits() {
        assertEquals(emptySet<String>(), Hotspots.rank(mapOf("a" to 1)))
        val counts = (1..40).associate { "f$it" to 2 } + ("busy" to 9)
        assertEquals("Every file tied with the last place stays", 41, Hotspots.rank(counts).size)
        val ranked = Hotspots.rank((1..40).associate { "f$it" to it + 1 })
        assertEquals(Hotspots.LIMIT, ranked.size)
        assertEquals("f40", ranked.first())
    }

    fun testMigrationAddedBelowTheDefaultBranchLatestIsOutOfOrder() {
        Files.createDirectories(repo.resolve("db/migration"))
        git("checkout", "-q", "main")
        write("db/migration/V1__init.sql", "select 1")
        git("add", ".")
        git("commit", "-q", "-m", "init")
        git("checkout", "-q", "-b", "orders")
        write("db/migration/V2__orders.sql", "select 2")
        git("add", ".")
        git("commit", "-q", "-m", "orders")
        // Meanwhile the default branch moves past V2.
        git("checkout", "-q", "main")
        write("db/migration/V2__payments.sql", "select 2")
        write("db/migration/V3__audit.sql", "select 3")
        git("add", ".")
        git("commit", "-q", "-m", "payments")
        git("checkout", "-q", "orders")
        val folder = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(repo.resolve("db/migration"))!!
        VfsUtil.markDirtyAndRefresh(false, true, true, folder)

        val base = MigrationBranchBase.getInstance(project)!!
        PlatformTestUtil.waitWithEventsDispatching("default branch migrations", { base.migrationsOnBase(folder) != null }, 30)
        val main = base.migrationsOnBase(folder)!!
        assertEquals("main", main.ref)
        assertEquals("3", main.latestVersion)
        assertEquals(setOf("V1__init.sql", "V2__payments.sql", "V3__audit.sql"), main.fileNames)

        val orders = folder.findChild("V2__orders.sql")!!
        assertEquals(OutOfOrderMigration("2", "3", "main"), FlywayMigrations.outOfOrder(orders, project))
        assertNull(FlywayMigrations.outOfOrder(folder.findChild("V1__init.sql")!!, project))
    }

    private fun write(name: String, text: String) {
        Files.writeString(repo.resolve(name), text)
    }

    private fun git(vararg args: String) {
        val process = ProcessBuilder(listOf("git") + args).directory(repo.toFile()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS))
        assertEquals("git ${args.joinToString(" ")}: $output", 0, process.exitValue())
    }
}
