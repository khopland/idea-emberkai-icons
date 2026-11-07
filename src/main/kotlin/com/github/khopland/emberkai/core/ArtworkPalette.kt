package com.github.khopland.emberkai.core

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.IconPathPatcher

/**
 * Colors for Emberkai's own artwork. Each palette other than [EMBERKAI] ships a retinted copy of the
 * whole set under `/emberkai/palettes/<id>/icons/`, generated from the same sources: every shade keeps
 * its lightness, so icons stay as legible as Emberkai's, and takes the theme's hue. Upstream Symbols
 * artwork keeps its own colors.
 */
internal enum class ArtworkPalette(val displayName: String, val id: String?) {
    EMBERKAI("Emberkai", null),
    CATPPUCCIN("Catppuccin", "catppuccin"),
    NORD("Nord", "nord"),
    GRUVBOX("Gruvbox", "gruvbox");

    companion object {
        fun fromId(id: String?): ArtworkPalette = entries.firstOrNull { it.name == id } ?: EMBERKAI
    }
}

/**
 * Redirects Emberkai artwork to the selected palette's copy. A path patcher reaches every place that
 * loads `/emberkai/icons/…` (mappings, folders, stereotype badges, previews) without each knowing about
 * palettes, and installing one makes IntelliJ reload icons it already resolved.
 */
internal object ArtworkPalettePatcher : IconPathPatcher() {
    @Volatile
    private var palette: ArtworkPalette = ArtworkPalette.EMBERKAI
    private var installed = false
    private val pluginClassLoader: ClassLoader = ArtworkPalettePatcher::class.java.classLoader

    @Synchronized
    fun select(selected: ArtworkPalette) {
        if (selected == palette && installed == (selected.id != null)) return
        palette = selected
        if (installed) IconLoader.removePathPatcher(this)
        installed = selected.id != null
        if (installed) IconLoader.installPathPatcher(this)
    }

    @Synchronized
    fun uninstall() {
        if (installed) IconLoader.removePathPatcher(this)
        installed = false
        palette = ArtworkPalette.EMBERKAI
    }

    override fun patchPath(path: String, classLoader: ClassLoader?): String? {
        val id = palette.id ?: return null
        if (classLoader != null && classLoader !== pluginClassLoader) return null
        val relative = path.removePrefix("/")
        if (!relative.startsWith(ICONS)) return null
        return path.removeSuffix(relative) + "emberkai/palettes/$id/" + relative.removePrefix("emberkai/")
    }

    private const val ICONS = "emberkai/icons/"
}
