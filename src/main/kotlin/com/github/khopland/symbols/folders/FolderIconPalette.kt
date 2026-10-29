package com.github.khopland.symbols.folders

import com.github.khopland.symbols.accents.ModuleAccentColor
import com.github.khopland.symbols.accents.ModuleAccentColor.BLUE
import com.github.khopland.symbols.accents.ModuleAccentColor.GREEN
import com.github.khopland.symbols.accents.ModuleAccentColor.ORANGE
import com.github.khopland.symbols.accents.ModuleAccentColor.PINK
import com.github.khopland.symbols.accents.ModuleAccentColor.PURPLE
import com.github.khopland.symbols.accents.ModuleAccentColor.RED
import com.github.khopland.symbols.accents.ModuleAccentColor.SKY
import com.github.khopland.symbols.accents.ModuleAccentColor.YELLOW

/** Color order for automatically assigned service-root folder icons and module accents. */
internal enum class FolderIconPalette(val displayName: String, val colors: List<ModuleAccentColor>) {
    EMBERKAI("Emberkai colors", listOf(BLUE, GREEN, ORANGE, PINK, PURPLE, RED, SKY, YELLOW)),
    ACCESSIBLE("Colorblind-friendly shapes", listOf(BLUE, ORANGE, YELLOW, PURPLE, GREEN, RED, SKY, PINK));

    companion object {
        fun fromId(id: String?): FolderIconPalette =
            entries.firstOrNull { it.name == id } ?: EMBERKAI
    }
}
