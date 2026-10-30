package com.github.khopland.symbols.ownership

import com.github.khopland.symbols.git.GitQueries
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import git4idea.repo.GitRepositoryManager

/**
 * Tools > Find Reviewers for This Branch: compares every Git repository with its default branch,
 * the same comparison the Changed on this branch lens uses, and lists the CODEOWNERS owners of the
 * changed files. Registered only when the Git plugin is enabled.
 */
internal class FindBranchReviewersAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabledAndVisible = project != null && !project.isDisposed &&
            GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project?.takeUnless { it.isDisposed } ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Finding reviewers for this branch", true) {
            override fun run(indicator: ProgressIndicator) {
                val diffs = GitRepositoryManager.getInstance(project).repositories.map {
                    indicator.checkCanceled()
                    GitQueries.branchDiff(project, it)
                }
                val reviewers = ReadAction.compute<BranchReviewers, RuntimeException> {
                    BranchReviewers.of(diffs.flatMap { diff -> diff.paths.map { ChangedPath(diff.root, it) } })
                }
                val bases = diffs.mapNotNull { it.baseRef }.distinct()
                ApplicationManager.getApplication().invokeLater({
                    if (!project.isDisposed) BranchReviewersDialog(project, reviewers, bases).show()
                }, ModalityState.nonModal())
            }
        })
    }
}
