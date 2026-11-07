package com.github.khopland.emberkai.core

import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import javax.swing.Icon

internal class ArtworkPaletteTest : BasePlatformTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null

    override fun setUp() {
        super.setUp()
        if (GraphicsEnvironment.isHeadless()) IconLoader.activate()
        settings = EmberkaiIconsSettings.getInstance()
        savedState = settings.state
    }

    override fun tearDown() {
        try {
            savedState?.let(settings::loadState)
            if (GraphicsEnvironment.isHeadless()) IconLoader.deactivate()
        } finally {
            super.tearDown()
        }
    }

    fun testEveryPaletteShipsTheWholeSet() {
        val loader = ArtworkPalette::class.java.classLoader
        val icons = IconMappings.catalog().map { it.resourcePath }.filter { it.startsWith("/emberkai/icons/") }.distinct()
        assertTrue(icons.isNotEmpty())
        for (palette in ArtworkPalette.entries.filter { it.id != null }) {
            settings.artworkPalette = palette
            val missing = icons.map { checkNotNull(ArtworkPalettePatcher.patchPath(it, loader)) }
                .filter { loader.getResource(it.removePrefix("/")) == null }
            assertEquals("${palette.displayName} is missing artwork", emptyList<String>(), missing)
        }
    }

    fun testSelectingAPaletteRetintsLoadedIcons() {
        val icon = IconLoader.getIcon("/emberkai/icons/helm.svg", ArtworkPalette::class.java)
        val emberkai = pixels(icon)

        settings.artworkPalette = ArtworkPalette.NORD
        assertEquals("/emberkai/palettes/nord/icons/helm.svg", ArtworkPalettePatcher.patchPath("/emberkai/icons/helm.svg", null))
        assertNull("Upstream art keeps its colors", ArtworkPalettePatcher.patchPath("/symbols/icons/files/json.svg", null))
        val nord = pixels(icon)
        assertFalse("The same icon instance repaints in Nord colors", emberkai.contentEquals(nord))

        settings.artworkPalette = ArtworkPalette.EMBERKAI
        assertNull(ArtworkPalettePatcher.patchPath("/emberkai/icons/helm.svg", null))
        assertTrue(emberkai.contentEquals(pixels(icon)))
    }

    fun testExportedSettingsKeepThePalette() {
        val file = kotlin.io.path.createTempFile(suffix = ".properties")
        try {
            EmberkaiIconsSettings.export(EmberkaiIconsSettings.State(artworkPalette = "GRUVBOX"), file)
            assertEquals("GRUVBOX", EmberkaiIconsSettings.import(file).artworkPalette)
        } finally {
            java.nio.file.Files.deleteIfExists(file)
        }
    }

    private fun pixels(icon: Icon): IntArray {
        val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.scale(2.0, 2.0)
            icon.paintIcon(null, g, 0, 0)
        } finally {
            g.dispose()
        }
        return image.getRGB(0, 0, 32, 32, null, 0, 32)
    }
}
