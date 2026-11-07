package com.github.khopland.symbols.artwork

import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.Properties
import javax.swing.Icon
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow
import org.w3c.dom.Element

/**
 * Checks the icon SVGs before they ship. All artwork must parse and paint at 16px; Emberkai's own
 * artwork must also use the named palette in design/palette.properties and stay legible on both IDE
 * themes, which is what 0.8.0 had to fix by hand. Upstream Symbols artwork is mid-tone by design, so
 * it is held to the structural checks only. Each rule is also run against known-bad artwork, so a
 * rule that stops finding anything fails here rather than passing quietly.
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
        assertNoProblems((EMBERKAI.svgs() + SYMBOLS.svgs()).mapNotNull(::structureProblem))
    }

    fun testEmberkaiArtworkUsesSquareGrids() {
        assertNoProblems(EMBERKAI.svgs().mapNotNull(::gridProblem))
    }

    fun testEmberkaiArtworkUsesThePalette() {
        assertNoProblems(EMBERKAI.svgs().flatMap(::paletteProblems))
    }

    fun testDarkVariantsMatchTheirLightArtwork() {
        assertNoProblems(EMBERKAI.svgs().filter { it.name.endsWith(DARK_SUFFIX) }.mapNotNull(::darkVariantProblem))
    }

    fun testEmberkaiArtworkIsLegibleInBothThemes() {
        assertNoProblems(EMBERKAI.svgs().filterNot { it.name.endsWith(DARK_SUFFIX) }.flatMap(::legibilityProblems))
    }

    fun testEachRuleRejectsKnownBadArtwork() {
        val dir = Files.createTempDirectory("emberkai-lint").toFile()
        try {
            fun svg(name: String, body: String, root: String = """width="16" height="16" viewBox="0 0 16 16""""): File =
                File(dir, name).apply { writeText("""<svg $root fill="none" xmlns="http://www.w3.org/2000/svg">$body</svg>""") }
            val square = """<rect x="2" y="2" width="12" height="12" fill="#1E293B"/>"""

            assertNotNull("no viewBox", structureProblem(svg("a.svg", square, """width="16" height="16"""")))
            assertNotNull("not 16px", structureProblem(svg("b.svg", square, """width="24" height="24" viewBox="0 0 24 24"""")))
            assertNotNull("not XML", structureProblem(File(dir, "c.svg").apply { writeText("<svg") }))
            assertNotNull("odd grid", gridProblem(svg("d.svg", square, """width="16" height="16" viewBox="0 0 20 20"""")))
            assertNotNull("not 16px", gridProblem(svg("d2.svg", square, """width="24" height="24" viewBox="0 0 24 24"""")))
            for (paint in listOf(
                """fill="#123456"""", """fill="#FB923C80"""", """stroke="orange"""", """fill="rgb(1,2,3)"""",
                """style="fill:hsl(0,0%,0%)"""", """fill="url(#g) red"""",
            )) {
                assertFalse(paint, paletteProblems(svg("e.svg", """<rect width="4" height="4" $paint/>""")).isEmpty())
            }
            assertEquals(
                "Palette colors, none, currentColor, references, and masks are allowed",
                emptyList<String>(),
                paletteProblems(
                    svg(
                        "f.svg",
                        """<defs><mask id="m"><rect width="16" height="16" fill="#fff"/><rect fill="#000"/></mask></defs>""" +
                            """<rect width="4" height="4" fill="url(#m) #1E293B" stroke="none" style="fill: #1E293B !important; stroke: currentColor"/>""",
                    ),
                ),
            )
            assertNotNull("orphan dark variant", darkVariantProblem(svg("g_dark.svg", square)))
            assertFalse(
                "pale on light",
                legibilityProblems(svg("h.svg", """<rect x="2" y="2" width="12" height="12" fill="#FEF08A"/>""")).isEmpty(),
            )
            assertFalse("dark on dark", legibilityProblems(svg("i.svg", square)).isEmpty())
            svg("i_dark.svg", """<rect x="2" y="2" width="12" height="12" fill="#E2E8F0"/>""")
            assertEquals("a dark variant fixes the dark theme", emptyList<String>(), legibilityProblems(File(dir, "i.svg")))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun structureProblem(file: File): String? {
        val root = runCatching { parse(file) }.getOrElse { return "${file.label}: ${it.message}" }
        val size = runCatching { load(file)?.let { it.iconWidth to it.iconHeight } }
            .getOrElse { return "${file.label}: IntelliJ failed to render it: ${it.message}" }
        return when {
            root.tagName != "svg" -> "${file.label}: root element is <${root.tagName}>"
            root.getAttribute("viewBox").isEmpty() -> "${file.label}: no viewBox, so the artwork cannot scale"
            size == null -> "${file.label}: IntelliJ could not load it"
            size != (16 to 16) -> "${file.label}: paints at ${size.first}x${size.second}, not 16x16"
            else -> null
        }
    }

    private fun gridProblem(file: File): String? {
        val root = runCatching { parse(file) }.getOrElse { return "${file.label}: ${it.message}" }
        val size = root.getAttribute("width") to root.getAttribute("height")
        val viewBox = root.getAttribute("viewBox")
        return when {
            size != ("16" to "16") -> "${file.label}: width and height must be 16, found ${size.first}x${size.second}"
            viewBox !in GRIDS -> "${file.label}: viewBox \"$viewBox\" is not one of $GRIDS"
            else -> null
        }
    }

    /** Every paint value outside masks, in attributes or `style`, must be a palette color or a non-color. */
    private fun paletteProblems(file: File): List<String> {
        val root = runCatching { parse(file) }.getOrElse { return listOf("${file.label}: ${it.message}") }
        val problems = mutableListOf<String>()
        fun visit(element: Element) {
            // Masks only cut shapes out; their black and white never reach the screen.
            if (element.tagName == "mask") return
            if (element.tagName == "style") problems += "${file.label}: <style> is not supported; use attributes"
            val declarations = element.getAttribute("style").split(';').mapNotNull { declaration ->
                declaration.split(':', limit = 2).takeIf { it.size == 2 }?.let { (name, value) -> name.trim() to value }
            }
            val paints = PAINT_ATTRIBUTES.map { it to element.getAttribute(it) } +
                declarations.filter { it.first in PAINT_ATTRIBUTES }
            for ((name, raw) in paints) {
                // A paint server reference may carry a fallback color, as in url(#g) #1E293B; check the fallback.
                val value = raw.replace(PAINT_SERVER, "").replace(IMPORTANT, "").trim()
                if (value.isEmpty() || value in NON_COLORS) continue
                problems += when {
                    !HEX_COLOR.matches(value) -> "${file.label}: $name=\"$value\" is not a #RGB or #RRGGBB palette color"
                    normalize(value) !in PALETTE -> "${file.label}: ${normalize(value)} is not in design/palette.properties"
                    else -> continue
                }
            }
            val children = element.childNodes
            for (index in 0 until children.length) (children.item(index) as? Element)?.let(::visit)
        }
        visit(root)
        return problems.distinct()
    }

    private fun darkVariantProblem(dark: File): String? {
        val light = File(dark.parentFile, dark.name.removeSuffix(DARK_SUFFIX) + ".svg")
        return when {
            !light.isFile -> "${dark.label}: no light artwork named ${light.name}"
            parse(light).getAttribute("viewBox") != parse(dark).getAttribute("viewBox") ->
                "${dark.label}: viewBox differs from ${light.name}"
            else -> null
        }
    }

    /**
     * Light artwork is measured on the light tree background, and the dark variant (or the light
     * artwork when there is none) on the dark one. At least [MIN_LEGIBLE_INK] of the painted pixels
     * must reach WCAG's 3:1 contrast for graphics at both 100% and 200%; thin strokes blur at 100%,
     * so it usually decides. Every icon 0.8.0 fixed by hand had none.
     */
    private fun legibilityProblems(light: File): List<String> {
        val dark = File(light.parentFile, light.name.removeSuffix(".svg") + DARK_SUFFIX).takeIf(File::isFile)
        return listOf(Theme.LIGHT to light, Theme.DARK to (dark ?: light)).mapNotNull { (theme, file) ->
            // Artwork that does not load or render is reported by testEveryIconPaintsAt16Pixels.
            val icon = runCatching { load(file) }.getOrNull() ?: return@mapNotNull null
            val share = runCatching { SCALES.minOf { legibleInk(icon, theme.background, it) } }
                .getOrElse { return@mapNotNull "${file.label}: IntelliJ failed to render it: ${it.message}" }
            if (share >= MIN_LEGIBLE_INK) return@mapNotNull null
            val fix = if (theme == Theme.DARK && dark == null) "; add ${light.name.removeSuffix(".svg")}$DARK_SUFFIX" else ""
            "${file.label}: only ${percent(share)} of its pixels reach 3:1 on the ${theme.label} theme$fix"
        }
    }

    private fun legibleInk(icon: Icon, background: Int, scale: Int): Double {
        val size = 16 * scale
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.scale(scale.toDouble(), scale.toDouble())
            icon.paintIcon(null, g, 0, 0)
        } finally {
            g.dispose()
        }
        var ink = 0.0
        var legible = 0.0
        for (y in 0 until size) for (x in 0 until size) {
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
        val SCALES = listOf(1, 2)
        const val MIN_LEGIBLE_INK = 0.12
        val GRIDS = setOf("0 0 16 16", "0 0 24 24")
        val PAINT_ATTRIBUTES = setOf("fill", "stroke", "stop-color", "flood-color", "lighting-color", "color")
        val NON_COLORS = setOf("none", "currentColor", "inherit")
        val HEX_COLOR = Regex("#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})")
        val PAINT_SERVER = Regex("""^\s*url\([^)]*\)""")
        val IMPORTANT = Regex("""\s*!important\s*$""")
        val PALETTE: Set<String> by lazy {
            Properties().apply { File("design/palette.properties").reader().use(::load) }
                .values.map { normalize(it as String) }.toSet()
        }

        val File.label: String
            get() = if (startsWith(File("src/main/resources"))) relativeTo(File("src/main/resources")).path else name

        fun File.svgs(): List<File> = walk().filter { it.isFile && it.extension == "svg" }.sorted().toList()

        /** Parses without fetching external DTDs, which editors such as Inkscape declare. */
        fun parse(file: File): Element = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }.newDocumentBuilder().parse(file).documentElement

        // Uncached: loading every bundled icon would evict the ones other tests compare by identity.
        fun load(file: File): Icon? = IconLoader.findIcon(file.toURI().toURL(), false)

        fun normalize(color: String): String {
            val hex = color.trim().removePrefix("#").uppercase(Locale.ROOT)
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
