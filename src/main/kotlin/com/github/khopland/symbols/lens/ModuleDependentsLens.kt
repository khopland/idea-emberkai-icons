package com.github.khopland.symbols.lens

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Files in [moduleName] and in every module that depends on it, directly or through other modules,
 * in any dependency scope. Answers what a change to the module can break. The dependents are
 * worked out again whenever the project's roots or module dependencies change.
 */
internal data class ModuleDependentsLens(val moduleName: String) : Lens {
    override val displayName: String get() = "Depends on $moduleName"

    @Volatile
    private var cached: Pair<Long, Set<String>>? = null

    override fun contains(file: VirtualFile, project: Project): Boolean {
        if (file.isDirectory) return false
        val module = ProjectFileIndex.getInstance(project).getModuleForFile(file) ?: return false
        return module.name in modules(project)
    }

    /** [moduleName] and its transitive dependents; empty when the module no longer exists. */
    fun modules(project: Project): Set<String> {
        val stamp = ProjectRootManager.getInstance(project).modificationCount
        cached?.takeIf { it.first == stamp }?.let { return it.second }
        val manager = ModuleManager.getInstance(project)
        val modules = if (manager.findModuleByName(moduleName) == null) {
            emptySet()
        } else {
            dependentsClosure(moduleName) { name ->
                manager.findModuleByName(name)?.let(manager::getModuleDependentModules).orEmpty().map { it.name }
            }
        }
        cached = stamp to modules
        return modules
    }

    companion object {
        /** [start] plus everything reachable through [dependentsOf]; cycles are fine. */
        fun dependentsClosure(start: String, dependentsOf: (String) -> Collection<String>): Set<String> {
            val seen = linkedSetOf(start)
            val queue = ArrayDeque(listOf(start))
            while (queue.isNotEmpty()) {
                for (dependent in dependentsOf(queue.removeFirst())) {
                    if (seen.add(dependent)) queue.addLast(dependent)
                }
            }
            return seen
        }
    }
}
