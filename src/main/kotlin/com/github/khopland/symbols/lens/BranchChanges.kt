package com.github.khopland.symbols.lens

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * The files the current branch changed, for [ChangedOnBranchLens]. Registered only when the Git
 * plugin is enabled; without it the lens is not offered.
 */
internal interface BranchChanges {
    /**
     * Files that differ from the merge base with the default branch, plus uncommitted and
     * unversioned files. Returns null until the first comparison finishes, and starts one if needed.
     */
    fun changedFiles(): Set<VirtualFile>?

    /** Drops the last comparison, so the next [changedFiles] call compares again. */
    fun forget()

    companion object {
        fun getInstance(project: Project): BranchChanges? = project.getService(BranchChanges::class.java)
    }
}

/** Picks the ref this branch is compared against. */
internal object BranchBase {
    private val DEFAULT_NAMES = listOf("main", "master")

    /**
     * Prefers `upstream`, the pull request target in fork workflows, then the branch `origin/HEAD`
     * points at, then `origin`, then local branches. Returns null when no default branch exists, and
     * the lens then shows only uncommitted changes.
     */
    fun choose(originHead: String?, remoteBranches: Set<String>, localBranches: Set<String>): String? {
        val candidates = DEFAULT_NAMES.map { "upstream/$it" } +
            listOfNotNull(originHead?.takeIf { it.startsWith("origin/") && it != "origin/HEAD" }) +
            DEFAULT_NAMES.map { "origin/$it" }
        return candidates.firstOrNull { it in remoteBranches } ?: DEFAULT_NAMES.firstOrNull { it in localBranches }
    }
}
