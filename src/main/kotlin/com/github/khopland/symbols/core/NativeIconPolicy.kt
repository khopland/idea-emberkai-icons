package com.github.khopland.symbols.core

import com.github.khopland.symbols.folders.ProjectFolderIcons
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import java.util.ResourceBundle

/** Keeps native file icons and protects folders owned by other IDE features. */
internal object NativeIconPolicy {
    private val CONFIG = ResourceBundle.getBundle(
        "emberkai.native", Locale.ROOT, SymbolsIconProvider::class.java.classLoader,
    )
    private val SOURCE_EXTENSIONS = names("file.extensions")
    private val TOOL_SCRIPT_SUFFIXES = names("file.toolScriptSuffixes")
    private val FOLDER_NAMES = names("folder.names")
    // Case-insensitive, so the ancestor walk needs no lowercased copy of each folder name.
    private val FOLDER_TREES = names("folder.trees").toSortedSet(String.CASE_INSENSITIVE_ORDER)

    fun isSource(file: VirtualFile): Boolean {
        val extension = file.extension ?: return false
        return extension.lowercase(Locale.ROOT) in SOURCE_EXTENSIONS
    }

    fun usesFileIcon(file: VirtualFile, name: String): Boolean =
        EmberkaiIconsSettings.getInstance().preserveNativeJavaKotlinIcons &&
            isSource(file) && TOOL_SCRIPT_SUFFIXES.none { name.endsWith(it) }

    fun usesFolderIcon(directory: VirtualFile, name: String, project: Project?): Boolean {
        if (name in FOLDER_NAMES) return true
        var parent = directory.parent
        while (parent != null) {
            if (parent.name in FOLDER_TREES) return true
            parent = parent.parent
        }
        if (project == null || project.isDisposed) return false
        if (ProjectPathUtil.isProjectBase(directory, project)) return true
        // Source/package roles are themed separately by ProjectFolderIcons.
        val index = ProjectFileIndex.getInstance(project)
        return index.isInLibrary(directory) || index.isExcluded(directory)
    }

    private fun names(key: String): Set<String> =
        CONFIG.getString(key)
            .split(",")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()
}
