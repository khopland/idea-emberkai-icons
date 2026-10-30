package com.github.khopland.symbols.lens

import com.github.khopland.symbols.git.GitQueries
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager

/**
 * Compares every Git repository in the project with its default branch while
 * [ChangedOnBranchLens] is active. Repository and change list updates schedule a new comparison.
 */
internal class GitBranchChanges(project: Project) :
    GitLensFiles(project, ChangedOnBranchLens, "Emberkai branch changes"), BranchChanges {
    init {
        val connection = project.messageBus.connect(this)
        // Commits, checkouts, fetches, and rebases move HEAD or the default branch.
        connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { scheduleRefresh() })
        // Edits, new files, and reverts change the working tree.
        connection.subscribe(ChangeListListener.TOPIC, object : ChangeListListener {
            override fun changeListUpdateDone() = scheduleRefresh()
        })
    }

    override fun changedFiles(): Set<VirtualFile>? = files()

    override fun compute(): Set<VirtualFile> =
        GitRepositoryManager.getInstance(project).repositories.flatMapTo(HashSet(), ::changedFiles)

    private fun changedFiles(repository: GitRepository): List<VirtualFile> {
        val diff = GitQueries.branchDiff(project, repository)
        return diff.paths.mapNotNull { diff.root.findFileByRelativePath(it) }
    }
}
