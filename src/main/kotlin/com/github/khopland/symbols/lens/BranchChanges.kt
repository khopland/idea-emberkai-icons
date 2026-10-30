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
