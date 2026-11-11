package com.github.khopland.emberkai.ui

import com.github.khopland.emberkai.folders.FolderIconPalette
import com.github.khopland.emberkai.settings.ArtworkPalette
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings.State
import com.github.khopland.emberkai.settings.IconRuleCategory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** The settings page's Export… and Import… file: a portable properties file of [State]. */
internal object EmberkaiSettingsFile {
    private val EXPORTED_FLAGS = EmberkaiIconsSettings.GENERAL_FLAGS + IconRuleCategory.entries.map(EmberkaiIconsSettings::flag)
    private const val PALETTE_KEY = "serviceFolderPalette"
    private const val ARTWORK_PALETTE_KEY = "artworkPalette"

    /** Writes [state] as a portable properties file. */
    fun export(state: State, path: Path) {
        val properties = Properties()
        EXPORTED_FLAGS.forEach { properties.setProperty(it.name, it.get(state).toString()) }
        properties.setProperty(PALETTE_KEY, state.serviceFolderPalette)
        properties.setProperty(ARTWORK_PALETTE_KEY, state.artworkPalette)
        Files.newOutputStream(path).use { properties.store(it, "Emberkai Icons settings") }
    }

    /** Reads an exported file; missing or invalid values fall back to the defaults. */
    fun import(path: Path): State {
        val properties = Properties()
        Files.newInputStream(path).use(properties::load)
        return State().apply {
            EXPORTED_FLAGS.forEach { property ->
                properties.getProperty(property.name)?.trim()?.toBooleanStrictOrNull()?.let { property.set(this, it) }
            }
            serviceFolderPalette = FolderIconPalette.fromId(properties.getProperty(PALETTE_KEY)?.trim()).name
            artworkPalette = ArtworkPalette.fromId(properties.getProperty(ARTWORK_PALETTE_KEY)?.trim()).name
        }
    }
}
