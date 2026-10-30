package com.github.khopland.symbols.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository

/** Read-only Git commands for background threads; never call them from the EDT or a read action. */
internal object GitQueries {
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
