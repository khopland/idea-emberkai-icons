package com.github.khopland.symbols

import com.github.khopland.symbols.ModuleAccentColor.BLUE
import com.github.khopland.symbols.ModuleAccentColor.GREEN
import com.github.khopland.symbols.ModuleAccentColor.ORANGE
import com.github.khopland.symbols.ModuleAccentColor.PINK
import com.github.khopland.symbols.ModuleAccentColor.PURPLE
import com.github.khopland.symbols.ModuleAccentColor.RED
import com.github.khopland.symbols.ModuleAccentColor.SKY
import com.github.khopland.symbols.ModuleAccentColor.YELLOW

/** Color order for automatically assigned service-root folder icons and module accents. */
internal enum class FolderIconPalette(val displayName: String, val colors: List<ModuleAccentColor>) {
    EMBERKAI("Emberkai colors", listOf(BLUE, GREEN, ORANGE, PINK, PURPLE, RED, SKY, YELLOW)),
    ACCESSIBLE("Colorblind-friendly shapes", listOf(BLUE, ORANGE, YELLOW, PURPLE, GREEN, RED, SKY, PINK));

    companion object {
        fun fromId(id: String?): FolderIconPalette =
            entries.firstOrNull { it.name == id } ?: EMBERKAI
    }
}
