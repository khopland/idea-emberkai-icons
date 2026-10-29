package com.github.khopland.symbols.folders

import com.github.khopland.symbols.accents.ModuleAccentStore
import com.github.khopland.symbols.core.ProjectPathUtil
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/** Caches service-root detection and its palette entry on the directory VFS object. */
internal object ServiceFolderIcons {
    private val DIRECTORY_CACHE = Key.create<CachedFolderIcon>("emberkai.serviceFolderIcon")
    private val PALETTE_CACHE = Key.create<PaletteAssignments>("emberkai.serviceFolderPalette")
    private const val NO_PARENT_STAMP = -1L
    private val BUILD_MARKERS = arrayOf("build.gradle.kts", "build.gradle", "pom.xml", "package.json")
    private val IGNORED_TREES = setOf(
        ".git", ".idea", ".gradle", "build", "dist", "node_modules", "out", "target", "vendor", "venv", ".venv",
    )

    fun iconKey(directory: VirtualFile, project: Project?): String? {
        if (project == null || project.isDisposed) return null
        val projectBasePath = ProjectPathUtil.basePath(project) ?: return null
        val path = directory.path
        val modificationStamp = directory.modificationStamp
        val markerMask = serviceMarkerMask(directory)
        val settings = EmberkaiIconsSettings.getInstance()
        val appearance = settings.folderIconAppearanceKey
        val moduleAccentKey = moduleAccentKey(directory, project, settings)
        val parent = directory.parent
        val parentModificationStamp = parent?.modificationStamp ?: NO_PARENT_STAMP
        val cached = directory.getUserData(DIRECTORY_CACHE)
        if (cached != null &&
            cached.path == path &&
            cached.projectBasePath == projectBasePath &&
            cached.modificationStamp == modificationStamp &&
            cached.markerMask == markerMask &&
            cached.parentModificationStamp == parentModificationStamp &&
            cached.moduleAccentKey == moduleAccentKey &&
            cached.appearance == appearance
        ) {
            return cached.iconKey
        }

        val iconKey = if (isServiceRoot(directory, project, markerMask)) {
            val colorKey = moduleAccentKey ?: parent?.let {
                colorKey(path, it, project, projectBasePath, parentModificationStamp, settings.serviceFolderPalette)
            }
            colorKey?.let { ServiceFolderIconStyle.apply(it, settings) }
        } else {
            if (moduleAccentKey == null) {
                parent?.let { releaseColor(path, it, projectBasePath, parentModificationStamp) }
            }
            null
        }
        directory.putUserData(
            DIRECTORY_CACHE,
            CachedFolderIcon(
                path, projectBasePath, modificationStamp, markerMask, parentModificationStamp,
                moduleAccentKey, appearance, iconKey,
            ),
        )
        return iconKey
    }

    private fun isServiceRoot(directory: VirtualFile, project: Project, markerMask: Int): Boolean {
        if (markerMask == 0) return false
        val relativePath = ProjectPathUtil.relativePath(directory, project) ?: return false
        return relativePath.split('/').none { it.lowercase(Locale.ROOT) in IGNORED_TREES }
    }

    /** Directory modification stamps are commonly constant, so track the few relevant children directly. */
    private fun serviceMarkerMask(directory: VirtualFile): Int = BUILD_MARKERS.indices.fold(0) { mask, index ->
        if (directory.findChild(BUILD_MARKERS[index])?.isDirectory == false) mask or (1 shl index) else mask
    }

    private fun moduleAccentKey(
        directory: VirtualFile,
        project: Project,
        settings: EmberkaiIconsSettings,
    ): String? {
        if (!settings.moduleAwareColors) return null
        return ModuleAccentStore.getInstance(project)
            .accentFor(directory, settings.serviceFolderPalette)
            ?.color
            ?.iconKey
    }

    private fun colorKey(
        path: String,
        parent: VirtualFile,
        project: Project,
        projectBasePath: String,
        parentModificationStamp: Long,
        paletteChoice: FolderIconPalette,
    ): String = synchronized(parent) {
        val palette = palette(parent, project, projectBasePath, parentModificationStamp, paletteChoice)
        palette.assignments.getOrPut(path) {
            val used = palette.assignments.values.toSet()
            val colors = paletteChoice.colors.map { it.iconKey }
            colors.firstOrNull { it !in used }
                ?: colors[Math.floorMod(path.lowercase(Locale.ROOT).hashCode(), colors.size)]
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
        paletteChoice: FolderIconPalette,
    ): PaletteAssignments {
        val cached = parent.getUserData(PALETTE_CACHE)
        if (cached != null &&
            cached.projectBasePath == projectBasePath &&
            cached.parentModificationStamp == parentModificationStamp &&
            cached.paletteChoice == paletteChoice
        ) {
            return cached
        }
        return PaletteAssignments(
            projectBasePath,
            parentModificationStamp,
            paletteChoice,
            discoverSiblingColors(parent, project, paletteChoice),
        ).also {
            parent.putUserData(PALETTE_CACHE, it)
        }
    }

    /** Preserve the previous path-ordered palette while paying sibling detection only once per parent. */
    private fun discoverSiblingColors(
        parent: VirtualFile,
        project: Project,
        paletteChoice: FolderIconPalette,
    ): MutableMap<String, String> {
        val assignments = linkedMapOf<String, String>()
        val palette = paletteChoice.colors
        parent.children.asSequence()
            .filter { it.isDirectory }
            .filter { sibling ->
                isServiceRoot(sibling, project, serviceMarkerMask(sibling))
            }
            .sortedBy { it.path.lowercase(Locale.ROOT) }
            .forEachIndexed { index, sibling ->
                assignments[sibling.path] = palette[index % palette.size].iconKey
            }
        return assignments
    }

    private data class CachedFolderIcon(
        val path: String,
        val projectBasePath: String,
        val modificationStamp: Long,
        val markerMask: Int,
        val parentModificationStamp: Long,
        val moduleAccentKey: String?,
        val appearance: String,
        val iconKey: String?,
    )

    private data class PaletteAssignments(
        val projectBasePath: String,
        val parentModificationStamp: Long,
        val paletteChoice: FolderIconPalette,
        val assignments: MutableMap<String, String>,
    )
}

/** Shared style mapping for detected roots and the settings preview. */
internal object ServiceFolderIconStyle {
    fun apply(baseKey: String, settings: EmberkaiIconsSettings): String = styleKey(
        baseKey,
        settings.serviceFolderPalette,
        settings.highContrastFolderIcons,
        settings.monochromeFolderIcons,
        settings.reducedFolderColor,
    )

    fun styleKey(
        baseKey: String,
        paletteChoice: FolderIconPalette,
        highContrast: Boolean,
        monochrome: Boolean,
        reducedFolderColor: Boolean,
    ): String {
        val color = baseKey.substringAfterLast('.')
        return when {
            monochrome -> "folder.service.monochrome"
            highContrast || paletteChoice == FolderIconPalette.ACCESSIBLE ->
                "folder.service.accessible.$color"
            reducedFolderColor -> "folder.service.reduced.$color"
            else -> baseKey
        }
    }
}

internal val EmberkaiIconsSettings.folderIconAppearanceKey: String
    get() = "${serviceFolderPalette.name}:$highContrastFolderIcons:$monochromeFolderIcons:" +
        "$reducedFolderColor:$moduleAwareColors"
