package com.github.khopland.symbols.lens

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

    companion object {
        const val DAYS = 90
        const val LIMIT = 30
        const val MINIMUM_COMMITS = 2

        fun getInstance(project: Project): Hotspots? = project.getService(Hotspots::class.java)

        /**
         * The [LIMIT] paths with the most commits, keeping every path tied with the last one, and
         * ignoring paths changed only once: churn, not a single edit, is what makes a hotspot.
         */
        fun rank(commitsPerPath: Map<String, Int>): Set<String> {
            val ranked = commitsPerPath.filterValues { it >= MINIMUM_COMMITS }.entries.sortedByDescending { it.value }
            val cutoff = ranked.getOrNull(LIMIT - 1)?.value ?: return ranked.mapTo(LinkedHashSet()) { it.key }
            return ranked.takeWhile { it.value >= cutoff }.mapTo(LinkedHashSet()) { it.key }
        }
    }
}
