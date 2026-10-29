package com.github.khopland.symbols

import com.github.khopland.symbols.lens.BranchChanges
import com.github.khopland.symbols.lens.ChangedOnBranchLens
import com.github.khopland.symbols.lens.IconLensService
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
