package com.github.khopland.emberkai.accents

import com.github.khopland.emberkai.settings.ArtworkPalettePatcher
import com.github.khopland.emberkai.folders.FolderIconPalette
import java.awt.Color
import java.util.Locale

/**
 * Folder palette entries available for module accents. The accent is chosen from the same
 * palette as service roots so a module keeps one recognizable color across its folders.
 */
internal enum class ModuleAccentColor(
    val id: String,
    val displayName: String,
    private val lightRgb: Int,
    private val darkRgb: Int,
) {
    BLUE("blue", "Blue", 0x2563EB, 0x60A5FA),
    GREEN("green", "Green", 0x16A34A, 0x4ADE80),
    ORANGE("orange", "Orange", 0xEA580C, 0xFB923C),
    PINK("pink", "Pink", 0xDB2777, 0xF472B6),
    PURPLE("purple", "Purple", 0x9333EA, 0xC084FC),
    RED("red", "Red", 0xDC2626, 0xF87171),
    SKY("sky", "Sky", 0x0284C7, 0x38BDF8),
    YELLOW("yellow", "Yellow", 0xCA8A04, 0xFACC15),
    ;

    val iconKey: String
        get() = "folder.service.$id"

    /** The accent in the selected artwork palette, so module dots match the retinted folders. */
    fun awtColor(dark: Boolean): Color = ArtworkPalettePatcher.palette.recolor(Color(if (dark) darkRgb else lightRgb))

    companion object {
        fun fromId(id: String?): ModuleAccentColor? = entries.firstOrNull { it.id == id }

        /** Derive a stable default color from the module name, following the active palette order. */
        fun defaultFor(moduleName: String, palette: FolderIconPalette): ModuleAccentColor =
            palette.colors[Math.floorMod(moduleName.lowercase(Locale.ROOT).hashCode(), palette.colors.size)]
    }
}
