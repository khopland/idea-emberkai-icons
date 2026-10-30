package com.github.khopland.symbols.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

/**
 * What a branch changed in one repository: paths relative to [root], including deleted files, and
 * the default branch it was compared with, or null when only uncommitted changes were compared.
 */
internal class BranchDiff(val root: VirtualFile, val baseRef: String?, val paths: List<String>)

/** Read-only Git commands for background threads; never call them from the EDT or a read action. */
internal object GitQueries {
    /**
     * Files that differ from the merge base with the default branch, plus uncommitted and
     * untracked files that are not ignored. Without a default branch, or before the first commit,
     * this falls back to uncommitted changes.
     */
    fun branchDiff(project: Project, repository: GitRepository): BranchDiff {
        val root = repository.root
        val base = defaultBranch(project, repository)
        val mergeBase = base?.let { run(project, root, GitCommand.MERGE_BASE, "HEAD", it)?.firstOrNull() }
        // Diffing the working tree against the merge base covers commits and uncommitted edits at once.
        val diff = run(project, root, GitCommand.DIFF, "--name-only", "--no-renames", "--no-ext-diff", mergeBase ?: "HEAD", "--")
        val untracked = run(project, root, GitCommand.LS_FILES, "--others", "--exclude-standard")
        val paths = (diff.orEmpty() + untracked.orEmpty()).filter { it.isNotBlank() }.distinct()
        return BranchDiff(root, base.takeIf { mergeBase != null }, paths)
    }

    /** The default branch of [repository], chosen by [BranchBase], or null when it has none. */
    fun defaultBranch(project: Project, repository: GitRepository): String? {
        val branches = repository.branches
        return BranchBase.choose(
            originHead = run(project, repository.root, GitCommand.REV_PARSE, "--abbrev-ref", "origin/HEAD")?.firstOrNull(),
            remoteBranches = branches.remoteBranches.mapTo(HashSet()) { it.name },
            localBranches = branches.localBranches.mapTo(HashSet()) { it.name },
        )
    }

    /** The output lines, or null when the command fails. Quiet: failures are expected, such as a missing ref. */
    fun run(project: Project, root: VirtualFile, command: GitCommand, vararg parameters: String): List<String>? {
        val handler = GitLineHandler(project, root, command).apply {
            setSilent(true)
            setStdoutSuppressed(true)
            addParameters(*parameters)
        }
        val result = Git.getInstance().runCommand(handler)
        return if (result.success()) result.output else null
    }
}
