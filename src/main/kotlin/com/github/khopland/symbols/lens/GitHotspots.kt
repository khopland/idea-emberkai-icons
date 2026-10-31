package com.github.khopland.symbols.lens

import com.github.khopland.symbols.git.GitQueries
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.GitCommand
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager

/**
 * Counts commits per file over the last [Hotspots.DAYS] days in every Git repository while
 * [HotspotsLens] is active. Only commits move the counts, so repository changes alone schedule a
 * new count. Files that no longer exist are dropped before ranking, so they do not take a place.
 */
internal class GitHotspots(project: Project) : GitLensFiles(project, HotspotsLens, "Emberkai hotspots"), Hotspots {
    init {
        project.messageBus.connect(this)
            .subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { scheduleRefresh() })
    }

    override fun compute(): Set<VirtualFile> =
        GitRepositoryManager.getInstance(project).repositories.flatMapTo(HashSet(), ::hotspots)

    private fun hotspots(repository: GitRepository): List<VirtualFile> {
        val log = GitQueries.run(
            project, repository.root, GitCommand.LOG,
            "--since=${Hotspots.DAYS}.days.ago", "--no-merges", "--no-renames", "--name-only", "--format=",
        ) ?: return emptyList()
        val counts = log.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
        return Hotspots.rank(counts.filterKeys { repository.root.findFileByRelativePath(it) != null })
            .mapNotNull { repository.root.findFileByRelativePath(it) }
    }
}
