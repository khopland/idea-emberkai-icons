package com.github.khopland.symbols.git

/** Picks the default branch that the current branch is compared against. */
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
