package com.github.khopland.symbols.folders

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.module.JpsModuleSourceRoot

/** Applies IntelliJ's root and package roles to Symbols-style folder artwork. */
internal object ProjectFolderIcons {
    enum class Role(val iconKey: String?) {
        CONTENT_ROOT("folder.role.content-root"),
        SOURCE("folder.role.source"),
        TEST_SOURCE("folder.role.test-source"),
        RESOURCES("folder.role.resources"),
        TEST_RESOURCES("folder.role.test-resources"),
        PACKAGE("folder.role.package"),
        TEST_PACKAGE("folder.role.test-package"),
        NATIVE(null),
    }

    fun role(directory: VirtualFile, project: Project?): Role? {
        if (project == null || project.isDisposed) return null
        val index = ProjectFileIndex.getInstance(project)
        val sourceRoot = index.getSourceRootForFile(directory)
        if (sourceRoot != null) {
            val source = sourceFolder(directory, sourceRoot, index)
            return if (source == null) Role.NATIVE else sourceRole(source.jpsElement, directory == sourceRoot)
        }
        return if (directory == index.getContentRootForFile(directory)) Role.CONTENT_ROOT else null
    }

    fun sourceRole(source: JpsModuleSourceRoot, root: Boolean): Role? {
        // Java and Kotlin's JVM/Multiplatform root types share these public
        // property contracts, so no Kotlin-plugin dependency is necessary.
        val properties = source.properties
        val test = source.rootType.isForTests()
        if (properties is JavaSourceRootProperties) {
            if (properties.isForGeneratedSources) return Role.NATIVE
            if (root) return if (test) Role.TEST_SOURCE else Role.SOURCE
            return if (test) Role.TEST_PACKAGE else Role.PACKAGE
        }
        if (properties is JavaResourceRootProperties) {
            if (properties.isForGeneratedSources) return Role.NATIVE
            if (!root) return null
            return if (test) Role.TEST_RESOURCES else Role.RESOURCES
        }
        // Other language plugins retain ownership of their own root types.
        return Role.NATIVE
    }

    private fun sourceFolder(directory: VirtualFile, root: VirtualFile, index: ProjectFileIndex): SourceFolder? {
        val module: Module = index.getModuleForFile(directory) ?: return null
        for (entry in ModuleRootManager.getInstance(module).contentEntries) {
            for (source in entry.sourceFolders) {
                if (root == source.file) return source
            }
        }
        return null
    }
}
