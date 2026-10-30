package com.github.khopland.symbols.lens

import com.github.khopland.symbols.git.GitQueries
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Compares every Git repository in the project with its default branch. Git runs on a background
 * thread, and only while [ChangedOnBranchLens] is active: repository and change list updates
 * schedule a new comparison, at most once per [REFRESH_DELAY_MS], and the lens is recomputed only
 * when the set of changed files differs. Turning the lens off forgets the result, so selecting it
 * again starts from a fresh comparison instead of showing a stale one.
 */
internal class GitBranchChanges(private val project: Project) : BranchChanges, Disposable {
    private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Emberkai branch changes", 1)
    private val refreshScheduled = AtomicBoolean()

    @Volatile
    private var files: Set<VirtualFile>? = null

    init {
        val connection = project.messageBus.connect(this)
        // Commits, checkouts, fetches, and rebases move HEAD or the default branch.
        connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { scheduleRefresh() })
        // Edits, new files, and reverts change the working tree.
        connection.subscribe(ChangeListListener.TOPIC, object : ChangeListListener {
            override fun changeListUpdateDone() = scheduleRefresh()
        })
    }

    override fun changedFiles(): Set<VirtualFile>? = files.also { if (it == null) scheduleRefresh() }

    override fun forget() {
        files = null
    }

    override fun dispose() = forget()

    private fun scheduleRefresh() {
        if (project.isDisposed || !refreshScheduled.compareAndSet(false, true)) return
        AppExecutorUtil.getAppScheduledExecutorService().schedule(
            {
                refreshScheduled.set(false)
                executor.execute(::refresh)
            },
            REFRESH_DELAY_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun refresh() {
        if (project.isDisposed) return
        if (IconLensService.getIfCreated(project)?.active != ChangedOnBranchLens) {
            files = null
            return
        }
        val changed = HashSet<VirtualFile>()
        for (repository in GitRepositoryManager.getInstance(project).repositories) {
            changed += changedFiles(repository)
        }
        if (project.isDisposed || changed == files) return
        files = changed
        IconLensService.getIfCreated(project)?.membershipChanged(ChangedOnBranchLens)
    }

    private fun changedFiles(repository: GitRepository): List<VirtualFile> {
        val diff = GitQueries.branchDiff(project, repository)
        return diff.paths.mapNotNull { diff.root.findFileByRelativePath(it) }
    }

    private companion object {
        const val REFRESH_DELAY_MS = 300L
    }
}
