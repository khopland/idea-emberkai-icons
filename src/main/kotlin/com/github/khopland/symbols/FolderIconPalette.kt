package com.github.khopland.symbols

/** Color order for automatically assigned service-root folder icons. */
internal enum class FolderIconPalette(val displayName: String) {
    EMBERKAI("Emberkai colors"),
    ACCESSIBLE("Colorblind-friendly shapes");

    companion object {
        fun fromId(id: String?): FolderIconPalette =
            entries.firstOrNull { it.name == id } ?: EMBERKAI
    }
}
