package com.github.khopland.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import javax.swing.Icon

/** Caches service-root detection and its palette entry on the directory VFS object. */
internal class ServiceFolderIcons(private val mappings: IconMappings) {
    fun icon(directory: VirtualFile, project: Project?): Icon? =
        iconKey(directory, project)?.let(mappings::get)

    fun iconKey(directory: VirtualFile, project: Project?): String? {
        if (project == null || project.isDisposed) return null
        val projectBasePath = ProjectPathUtil.basePath(project) ?: return null
        val path = directory.path
        val modificationStamp = directory.modificationStamp
        val markerMask = serviceMarkerMask(directory)
        val parent = directory.parent
        val parentModificationStamp = parent?.modificationStamp ?: NO_PARENT_STAMP
        val cached = directory.getUserData(DIRECTORY_CACHE)
        if (cached != null &&
            cached.path == path &&
            cached.projectBasePath == projectBasePath &&
            cached.modificationStamp == modificationStamp &&
            cached.markerMask == markerMask &&
            cached.parentModificationStamp == parentModificationStamp
        ) {
            return cached.iconKey
        }

        val iconKey = if (isServiceRoot(directory, project, markerMask)) {
            val colorKey = parent?.let {
                colorKey(path, it, project, projectBasePath, parentModificationStamp)
            }
            colorKey
        } else {
            parent?.let { releaseColor(path, it, projectBasePath, parentModificationStamp) }
            null
        }
        directory.putUserData(
            DIRECTORY_CACHE,
            CachedFolderIcon(path, projectBasePath, modificationStamp, markerMask, parentModificationStamp, iconKey),
        )
        return iconKey
    }

    private fun isServiceRoot(directory: VirtualFile, project: Project, markerMask: Int): Boolean {
        if (markerMask == 0) return false
        val relativePath = ProjectPathUtil.relativePath(directory, project) ?: return false
        if (relativePath.split('/').any { it.lowercase(Locale.ROOT) in IGNORED_TREES }) return false

        return true
    }

    /** Directory modification stamps are commonly constant, so track the few relevant children directly. */
    private fun serviceMarkerMask(directory: VirtualFile): Int = BUILD_MARKERS.indices.fold(0) { mask, index ->
        if (directory.findChild(BUILD_MARKERS[index])?.isDirectory == false) mask or (1 shl index) else mask
    }

    private fun colorKey(
        path: String,
        parent: VirtualFile,
        project: Project,
        projectBasePath: String,
        parentModificationStamp: Long,
    ): String = synchronized(parent) {
        val palette = palette(parent, project, projectBasePath, parentModificationStamp)
        palette.assignments.getOrPut(path) {
            val used = palette.assignments.values.toSet()
            PALETTE.firstOrNull { it !in used }
                ?: PALETTE[Math.floorMod(path.lowercase(Locale.ROOT).hashCode(), PALETTE.size)]
        }
    }

    private fun releaseColor(
        path: String,
        parent: VirtualFile,
        projectBasePath: String,
        parentModificationStamp: Long,
    ) {
        synchronized(parent) {
            val cached = parent.getUserData(PALETTE_CACHE) ?: return
            if (cached.projectBasePath == projectBasePath &&
                cached.parentModificationStamp == parentModificationStamp
            ) {
                cached.assignments.remove(path)
            }
        }
    }

    private fun palette(
        parent: VirtualFile,
        project: Project,
        projectBasePath: String,
        parentModificationStamp: Long,
    ): PaletteAssignments {
        val cached = parent.getUserData(PALETTE_CACHE)
        if (cached != null &&
            cached.projectBasePath == projectBasePath &&
            cached.parentModificationStamp == parentModificationStamp
        ) {
            return cached
        }
        return PaletteAssignments(
            projectBasePath,
            parentModificationStamp,
            discoverSiblingColors(parent, project),
        ).also {
            parent.putUserData(PALETTE_CACHE, it)
        }
    }

    /** Preserve the previous path-ordered palette while paying sibling detection only once per parent. */
    private fun discoverSiblingColors(parent: VirtualFile, project: Project): MutableMap<String, String> {
        val assignments = linkedMapOf<String, String>()
        parent.children.asSequence()
            .filter { it.isDirectory }
            .filter { sibling ->
                isServiceRoot(sibling, project, serviceMarkerMask(sibling))
            }
            .sortedBy { it.path.lowercase(Locale.ROOT) }
            .forEachIndexed { index, sibling ->
                assignments[sibling.path] = PALETTE[index % PALETTE.size]
            }
        return assignments
    }

    private data class CachedFolderIcon(
        val path: String,
        val projectBasePath: String,
        val modificationStamp: Long,
        val markerMask: Int,
        val parentModificationStamp: Long,
        val iconKey: String?,
    )

    private data class PaletteAssignments(
        val projectBasePath: String,
        val parentModificationStamp: Long,
        val assignments: MutableMap<String, String>,
    )

    private companion object {
        val DIRECTORY_CACHE = Key.create<CachedFolderIcon>("emberkai.serviceFolderIcon")
        val PALETTE_CACHE = Key.create<PaletteAssignments>("emberkai.serviceFolderPalette")
        const val NO_PARENT_STAMP = -1L
        val BUILD_MARKERS = arrayOf("build.gradle.kts", "build.gradle", "pom.xml", "package.json")
        val IGNORED_TREES = setOf(
            ".git", ".idea", ".gradle", "build", "dist", "node_modules", "out", "target", "vendor", "venv", ".venv",
        )
        val PALETTE = arrayOf(
            "folder.service.blue",
            "folder.service.green",
            "folder.service.orange",
            "folder.service.pink",
            "folder.service.purple",
            "folder.service.red",
            "folder.service.sky",
            "folder.service.yellow",
        )
    }
}
