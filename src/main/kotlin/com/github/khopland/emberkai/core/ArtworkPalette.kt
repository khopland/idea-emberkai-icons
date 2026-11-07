package com.github.khopland.emberkai.core

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.IconPathPatcher
import java.awt.Color
import java.util.Properties

/**
 * Colors for Emberkai's artwork. Each palette other than [EMBERKAI] ships a retinted copy of Emberkai's
 * set and of the upstream folders under `/emberkai/palettes/<id>/`, generated from the same sources:
 * every shade keeps its luminance, so icons stay as legible as Emberkai's, and takes the theme's hue.
 * Upstream file icons, mostly brand logos, keep their own colors.
 */
internal enum class ArtworkPalette(val displayName: String, val id: String?) {
    EMBERKAI("Emberkai", null),
    CATPPUCCIN("Catppuccin", "catppuccin"),
    NORD("Nord", "nord"),
    GRUVBOX("Gruvbox", "gruvbox");

    /** Palette color RGB to this palette's RGB, for accents painted in code rather than SVG. */
    private val recolor: Map<Int, Int> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val table = id?.let { ArtworkPalette::class.java.classLoader.getResourceAsStream("emberkai/palettes/$it/colors.properties") }
            ?: return@lazy emptyMap()
        Properties().apply { table.use(::load) }.entries.associate { (from, to) ->
            (from as String).toInt(16) to (to as String).toInt(16)
        }
    }

    /** [color] as this palette draws it; colors outside the Emberkai palette are kept. */
    fun recolor(color: Color): Color = recolor[color.rgb and 0xFFFFFF]?.let(::Color) ?: color

    companion object {
        fun fromId(id: String?): ArtworkPalette = entries.firstOrNull { it.name == id } ?: EMBERKAI
    }
}

/**
 * Redirects artwork to the selected palette's copy. A path patcher reaches every place that loads
 * `/emberkai/icons/…` or an upstream folder (mappings, folders, stereotype badges, previews) without each
 * knowing about palettes, and installing one makes IntelliJ reload icons it already resolved.
 */
internal object ArtworkPalettePatcher : IconPathPatcher() {
    @Volatile
    var palette: ArtworkPalette = ArtworkPalette.EMBERKAI
        private set
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
        val retinted = when {
            relative.startsWith(EMBERKAI_ICONS) -> relative.removePrefix("emberkai/")
            relative.startsWith(UPSTREAM_FOLDERS) -> relative
            else -> return null
        }
        return path.removeSuffix(relative) + "emberkai/palettes/$id/" + retinted
    }

    private const val EMBERKAI_ICONS = "emberkai/icons/"
    private const val UPSTREAM_FOLDERS = "symbols/icons/folders/"
}
