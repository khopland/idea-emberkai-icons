package com.github.khopland.emberkai.lens

import com.github.khopland.emberkai.git.GitQueries
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.GitCommand
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager

/**
 * Counts commits per file over the last [EmberkaiLensSettings.hotspotDays] days in every Git repository while
 * [HotspotsLens] is active. Only commits move the counts, so repository changes alone schedule a
 * new count. Files that no longer exist are dropped before ranking, so they do not take a place.
 */
internal class GitHotspots(project: Project) : GitLensFiles(project, HotspotsLens, "Emberkai hotspots"), Hotspots {
    init {
        project.messageBus.connect(this)
            .subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { scheduleRefresh() })
    }

    override fun settingsChanged() = scheduleRefresh()

    override fun compute(): Set<VirtualFile> {
        val settings = EmberkaiLensSettings.getInstance()
        return GitRepositoryManager.getInstance(project).repositories.flatMapTo(HashSet()) { hotspots(it, settings) }
    }

    private fun hotspots(repository: GitRepository, settings: EmberkaiLensSettings): List<VirtualFile> {
        val log = GitQueries.run(
            project, repository.root, GitCommand.LOG,
            "--since=${settings.hotspotDays}.days.ago", "--no-merges", "--no-renames", "--name-only", "--format=",
        ) ?: return emptyList()
        val counts = log.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
        val existing = counts.filterKeys { repository.root.findFileByRelativePath(it) != null }
        return Hotspots.rank(existing, settings.hotspotLimit, settings.hotspotMinimumCommits)
            .mapNotNull { repository.root.findFileByRelativePath(it) }
    }
}
