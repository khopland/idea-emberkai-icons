package com.github.khopland.emberkai.folders

import com.github.khopland.emberkai.accents.ModuleAccentColor
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.github.khopland.emberkai.accents.ModuleAccentColor.BLUE
import com.github.khopland.emberkai.accents.ModuleAccentColor.GREEN
import com.github.khopland.emberkai.accents.ModuleAccentColor.ORANGE
import com.github.khopland.emberkai.accents.ModuleAccentColor.PINK
import com.github.khopland.emberkai.accents.ModuleAccentColor.PURPLE
import com.github.khopland.emberkai.accents.ModuleAccentColor.RED
import com.github.khopland.emberkai.accents.ModuleAccentColor.SKY
import com.github.khopland.emberkai.accents.ModuleAccentColor.YELLOW

/** Color order for automatically assigned service-root folder icons and module accents. */
internal enum class FolderIconPalette(val displayName: String, val colors: List<ModuleAccentColor>) {
    EMBERKAI("Emberkai colors", listOf(BLUE, GREEN, ORANGE, PINK, PURPLE, RED, SKY, YELLOW)),
    ACCESSIBLE("Colorblind-friendly shapes", listOf(BLUE, ORANGE, YELLOW, PURPLE, GREEN, RED, SKY, PINK));

    companion object {
        fun fromId(id: String?): FolderIconPalette =
            entries.firstOrNull { it.name == id } ?: EMBERKAI
    }
}

/** The service-folder palette; settings store it by name, so they need not know the palettes. */
internal var EmberkaiIconsSettings.serviceFolderPalette: FolderIconPalette
    get() = FolderIconPalette.fromId(serviceFolderPaletteId)
    set(value) {
        updateState { it.serviceFolderPalette = value.name }
    }
