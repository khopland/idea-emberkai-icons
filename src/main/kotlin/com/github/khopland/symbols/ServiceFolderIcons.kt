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
        val settings = EmberkaiIconsSettings.getInstance()
        val appearance = settings.folderIconAppearanceKey
        val parent = directory.parent
        val parentModificationStamp = parent?.modificationStamp ?: NO_PARENT_STAMP
        val cached = directory.getUserData(DIRECTORY_CACHE)
        if (cached != null &&
            cached.path == path &&
            cached.projectBasePath == projectBasePath &&
            cached.modificationStamp == modificationStamp &&
            cached.markerMask == markerMask &&
            cached.parentModificationStamp == parentModificationStamp &&
            cached.appearance == appearance
        ) {
            return cached.iconKey
        }

        val iconKey = if (isServiceRoot(directory, project, markerMask)) {
            val colorKey = parent?.let {
                colorKey(path, it, project, projectBasePath, parentModificationStamp, settings.serviceFolderPalette)
            }
            colorKey?.let { ServiceFolderIconStyle.apply(it, settings) }
        } else {
            parent?.let { releaseColor(path, it, projectBasePath, parentModificationStamp) }
            null
        }
        directory.putUserData(
            DIRECTORY_CACHE,
            CachedFolderIcon(path, projectBasePath, modificationStamp, markerMask, parentModificationStamp, appearance, iconKey),
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
        paletteChoice: FolderIconPalette,
    ): String = synchronized(parent) {
        val palette = palette(parent, project, projectBasePath, parentModificationStamp, paletteChoice)
        palette.assignments.getOrPut(path) {
            val used = palette.assignments.values.toSet()
            val colors = basePalette(paletteChoice)
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
        parent.children.asSequence()
            .filter { it.isDirectory }
            .filter { sibling ->
                isServiceRoot(sibling, project, serviceMarkerMask(sibling))
            }
            .sortedBy { it.path.lowercase(Locale.ROOT) }
            .forEachIndexed { index, sibling ->
                val palette = basePalette(paletteChoice)
                assignments[sibling.path] = palette[index % palette.size]
            }
        return assignments
    }

    private data class CachedFolderIcon(
        val path: String,
        val projectBasePath: String,
        val modificationStamp: Long,
        val markerMask: Int,
        val parentModificationStamp: Long,
        val appearance: String,
        val iconKey: String?,
    )

    private data class PaletteAssignments(
        val projectBasePath: String,
        val parentModificationStamp: Long,
        val paletteChoice: FolderIconPalette,
        val assignments: MutableMap<String, String>,
    )

    private companion object {
        val DIRECTORY_CACHE = Key.create<CachedFolderIcon>("emberkai.serviceFolderIcon")
        val PALETTE_CACHE = Key.create<PaletteAssignments>("emberkai.serviceFolderPalette")
        const val NO_PARENT_STAMP = -1L
        fun basePalette(choice: FolderIconPalette): Array<String> = when (choice) {
            FolderIconPalette.EMBERKAI -> PALETTE
            FolderIconPalette.ACCESSIBLE -> ACCESSIBLE_PALETTE
        }

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
        val ACCESSIBLE_PALETTE = arrayOf(
            "folder.service.blue",
            "folder.service.orange",
            "folder.service.yellow",
            "folder.service.purple",
            "folder.service.green",
            "folder.service.red",
            "folder.service.sky",
            "folder.service.pink",
        )
    }
}

/** Shared style mapping for detected roots and the settings preview. */
internal object ServiceFolderIconStyle {
    private val colorNames = listOf("blue", "green", "orange", "pink", "purple", "red", "sky", "yellow")
    private val accessibleColorNames = listOf("blue", "orange", "yellow", "purple", "green", "red", "sky", "pink")

    fun keyForIndex(
        index: Int,
        paletteChoice: FolderIconPalette,
        highContrast: Boolean,
        monochrome: Boolean,
        reducedFolderColor: Boolean,
    ): String {
        val palette = if (paletteChoice == FolderIconPalette.ACCESSIBLE) {
            accessibleColorNames
        } else {
            colorNames
        }
        val color = palette[Math.floorMod(index, palette.size)]
        return apply("folder.service.$color", paletteChoice, highContrast, monochrome, reducedFolderColor)
    }

    fun apply(baseKey: String, settings: EmberkaiIconsSettings): String = apply(
        baseKey,
        settings.serviceFolderPalette,
        settings.highContrastFolderIcons,
        settings.monochromeFolderIcons,
        settings.reducedFolderColor,
    )

    private fun apply(
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
    get() = "${serviceFolderPalette.name}:$highContrastFolderIcons:$monochromeFolderIcons:$reducedFolderColor"
