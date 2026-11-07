package com.github.khopland.symbols.artwork

import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import java.util.Properties
import javax.swing.Icon
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow

/**
 * Checks every bundled SVG before it ships. All artwork must parse and paint at 16px; Emberkai's
 * own artwork must also use the named palette in design/palette.properties and stay legible on
 * both IDE themes, which is what 0.8.0 had to fix by hand. Upstream Symbols artwork is mid-tone by
 * design, so it is held to the structural checks only.
 */
internal class IconArtworkLintTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        if (GraphicsEnvironment.isHeadless()) IconLoader.activate()
    }

    override fun tearDown() {
        try {
            if (GraphicsEnvironment.isHeadless()) IconLoader.deactivate()
        } finally {
            super.tearDown()
        }
    }

    fun testEveryIconPaintsAt16Pixels() {
        val problems = (EMBERKAI.svgs() + SYMBOLS.svgs()).mapNotNull { file ->
            val root = runCatching { parse(file) }.getOrElse { return@mapNotNull "${file.label}: ${it.message}" }
            val viewBox = root.getAttribute("viewBox")
            val size = runCatching { load(file)?.let { it.iconWidth to it.iconHeight } }
                .getOrElse { return@mapNotNull "${file.label}: IntelliJ failed to render it: ${it.message}" }
            when {
                root.tagName != "svg" -> "${file.label}: root element is <${root.tagName}>"
                viewBox.isEmpty() -> "${file.label}: no viewBox, so the artwork cannot scale"
                size == null -> "${file.label}: IntelliJ could not load it"
                size != (16 to 16) -> "${file.label}: paints at ${size.first}x${size.second}, not 16x16"
                else -> null
            }
        }
        assertNoProblems(problems)
    }

    fun testEmberkaiArtworkUsesSquareGrids() {
        val problems = EMBERKAI.svgs().mapNotNull { file ->
            val root = parse(file)
            val size = root.getAttribute("width") to root.getAttribute("height")
            val viewBox = root.getAttribute("viewBox")
            when {
                size != ("16" to "16") -> "${file.label}: width and height must be 16, found ${size.first}x${size.second}"
                viewBox !in GRIDS -> "${file.label}: viewBox \"$viewBox\" is not one of $GRIDS"
                else -> null
            }
        }
        assertNoProblems(problems)
    }

    fun testEmberkaiArtworkUsesThePalette() {
        val palette = Properties().apply { File("design/palette.properties").reader().use(::load) }
            .values.map { normalize(it as String) }.toSet()
        val problems = EMBERKAI.svgs().flatMap { file ->
            // Masks only cut shapes out; their black and white never reach the screen.
            val paint = file.readText().replace(MASK, "")
            COLOR.findAll(paint).map { normalize(it.value) }.distinct()
                .filter { it !in palette }
                .map { "${file.label}: $it is not in design/palette.properties" }
                .toList()
        }
        assertNoProblems(problems)
    }

    fun testDarkVariantsMatchTheirLightArtwork() {
        val problems = EMBERKAI.svgs().filter { it.name.endsWith(DARK_SUFFIX) }.mapNotNull { dark ->
            val light = File(dark.parentFile, dark.name.removeSuffix(DARK_SUFFIX) + ".svg")
            when {
                !light.isFile -> "${dark.label}: no light artwork named ${light.name}"
                parse(light).getAttribute("viewBox") != parse(dark).getAttribute("viewBox") ->
                    "${dark.label}: viewBox differs from ${light.name}"
                else -> null
            }
        }
        assertNoProblems(problems)
    }

    /**
     * Light artwork is measured on the light tree background, and the dark variant (or the light
     * artwork when there is none) on the dark one. At least [MIN_LEGIBLE_INK] of the painted pixels
     * must reach WCAG's 3:1 contrast for graphics; every icon 0.8.0 fixed by hand had none.
     */
    fun testEmberkaiArtworkIsLegibleInBothThemes() {
        val problems = EMBERKAI.svgs().filterNot { it.name.endsWith(DARK_SUFFIX) }.flatMap { light ->
            val dark = File(light.parentFile, light.name.removeSuffix(".svg") + DARK_SUFFIX).takeIf(File::isFile)
            listOf(Theme.LIGHT to light, Theme.DARK to (dark ?: light)).mapNotNull { (theme, file) ->
                val share = legibleInk(load(file) ?: return@mapNotNull null, theme.background)
                if (share >= MIN_LEGIBLE_INK) return@mapNotNull null
                val fix = if (theme == Theme.DARK && dark == null) "; add ${light.name.removeSuffix(".svg")}$DARK_SUFFIX" else ""
                "${file.label}: only ${percent(share)} of its pixels reach 3:1 on the ${theme.label} theme$fix"
            }
        }
        assertNoProblems(problems)
    }

    private fun legibleInk(icon: Icon, background: Int): Double {
        val image = BufferedImage(SAMPLE_SIZE, SAMPLE_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.scale(SAMPLE_SIZE / 16.0, SAMPLE_SIZE / 16.0)
            icon.paintIcon(null, g, 0, 0)
        } finally {
            g.dispose()
        }
        var ink = 0.0
        var legible = 0.0
        for (y in 0 until SAMPLE_SIZE) for (x in 0 until SAMPLE_SIZE) {
            val pixel = image.getRGB(x, y)
            val alpha = (pixel ushr 24) / 255.0
            if (alpha < 0.05) continue
            ink += alpha
            if (contrast(over(pixel, alpha, background), background) >= 3.0) legible += alpha
        }
        return if (ink == 0.0) 0.0 else legible / ink
    }

    private fun assertNoProblems(problems: List<String>) {
        assertTrue("${problems.size} artwork problem(s):\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private enum class Theme(val label: String, val background: Int) {
        // The New UI's Project view backgrounds.
        LIGHT("light", 0xF7F8FA),
        DARK("dark", 0x2B2D30),
    }

    private companion object {
        val EMBERKAI = File("src/main/resources/emberkai")
        val SYMBOLS = File("src/main/resources/symbols")
        const val DARK_SUFFIX = "_dark.svg"
        const val SAMPLE_SIZE = 32
        const val MIN_LEGIBLE_INK = 0.12
        val GRIDS = setOf("0 0 16 16", "0 0 24 24")
        val COLOR = Regex("#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})\\b")
        val MASK = Regex("<mask\\b.*?</mask>", RegexOption.DOT_MATCHES_ALL)

        val File.label: String get() = relativeTo(File("src/main/resources")).path

        fun File.svgs(): List<File> = walk().filter { it.isFile && it.extension == "svg" }.sorted().toList()

        fun parse(file: File) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file).documentElement

        // Uncached: loading every bundled icon would evict the ones other tests compare by identity.
        fun load(file: File): Icon? = IconLoader.findIcon(file.toURI().toURL(), false)

        fun normalize(color: String): String {
            val hex = color.removePrefix("#").uppercase(Locale.ROOT)
            return "#" + if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
        }

        fun percent(share: Double) = "%.0f%%".format(Locale.ROOT, share * 100)

        /** [pixel] composited over [background] with its own [alpha]. */
        fun over(pixel: Int, alpha: Double, background: Int): Int {
            fun channel(shift: Int) =
                (((pixel shr shift) and 0xFF) * alpha + ((background shr shift) and 0xFF) * (1 - alpha)).toInt()
            return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }

        /** WCAG 2 contrast ratio between two opaque RGB colors. */
        fun contrast(a: Int, b: Int): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        fun luminance(rgb: Int): Double {
            fun channel(shift: Int): Double {
                val value = ((rgb shr shift) and 0xFF) / 255.0
                return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
    }
}
