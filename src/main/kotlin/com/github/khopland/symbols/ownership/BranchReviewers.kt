package com.github.khopland.symbols.ownership

import com.intellij.openapi.vfs.VirtualFile

/** A path a branch changed, relative to its repository [root]; deleted files no longer exist. */
internal data class ChangedPath(val root: VirtualFile, val path: String) {
    fun file(): VirtualFile? = root.findFileByRelativePath(path)?.takeIf { it.isValid }
}

/**
 * Who has to review a branch: the CODEOWNERS owners of every changed path, busiest owner first.
 * [unowned] paths are governed by a CODEOWNERS file that gives them no owner, and [ungoverned]
 * paths have no CODEOWNERS file at all. Requires a read action.
 */
internal class BranchReviewers(
    val owners: List<Pair<String, List<ChangedPath>>>,
    val unowned: List<ChangedPath>,
    val ungoverned: List<ChangedPath>,
) {
    val changedCount: Int get() = owners.flatMap { it.second }.toSet().size + unowned.size + ungoverned.size

    companion object {
        fun of(changed: List<ChangedPath>): BranchReviewers {
            val byOwner = LinkedHashMap<String, MutableList<ChangedPath>>()
            val unowned = mutableListOf<ChangedPath>()
            val ungoverned = mutableListOf<ChangedPath>()
            for (path in changed.sortedBy { it.path }) {
                val ownership = CodeOwners.ownershipOfPath(path.root, path.path)
                when {
                    ownership == null -> ungoverned += path
                    ownership.owners.isEmpty() -> unowned += path
                    else -> ownership.owners.forEach { byOwner.getOrPut(it) { mutableListOf() } += path }
                }
            }
            val owners = byOwner.entries
                .sortedWith(compareByDescending<Map.Entry<String, List<ChangedPath>>> { it.value.size }.thenBy { it.key.lowercase() })
                .map { it.key to it.value.toList() }
            return BranchReviewers(owners, unowned, ungoverned)
        }
    }
}
