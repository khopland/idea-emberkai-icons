package com.github.khopland.emberkai.lens

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * The files changed in the most commits recently, for [HotspotsLens]. Registered only when the Git
 * plugin is enabled; without it the lens is not offered.
 */
internal interface Hotspots {
    /** Returns null until the first count finishes, and starts one if needed. */
    fun files(): Set<VirtualFile>?

    fun forget()

    /** Counts again with the current [EmberkaiLensSettings] if the lens is active, or later when it is chosen. */
    fun settingsChanged()

    companion object {
        const val DEFAULT_DAYS = 90
        const val DEFAULT_LIMIT = 30
        const val DEFAULT_MINIMUM_COMMITS = 2

        fun getInstance(project: Project): Hotspots? = project.getService(Hotspots::class.java)

        /**
         * The [limit] paths with the most commits, keeping every path tied with the last one, and
         * ignoring paths changed fewer than [minimumCommits] times: churn, not a single edit, is what
         * makes a hotspot.
         */
        fun rank(
            commitsPerPath: Map<String, Int>,
            limit: Int = DEFAULT_LIMIT,
            minimumCommits: Int = DEFAULT_MINIMUM_COMMITS,
        ): Set<String> {
            val ranked = commitsPerPath.filterValues { it >= minimumCommits }.entries.sortedByDescending { it.value }
            val cutoff = ranked.getOrNull(limit - 1)?.value ?: return ranked.mapTo(LinkedHashSet()) { it.key }
            return ranked.takeWhile { it.value >= cutoff }.mapTo(LinkedHashSet()) { it.key }
        }
    }
}
