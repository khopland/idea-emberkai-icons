package com.github.khopland.symbols.artwork

import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders changed icons before and after a change, at 100%, 150% and 200% on the light and dark
 * Project view backgrounds, so a reviewer sees what the SVG diff means. CI passes the changed SVG
 * paths and a directory holding their base versions; skipped unless `-PiconSheet` is passed:
 *
 *     ./gradlew test --tests '*IconContactSheet' -PiconSheet=changed.txt -PiconSheetBase=base
 */
internal class IconContactSheet : BasePlatformTestCase() {
    private val changedList = System.getProperty("emberkai.iconSheet.list")?.let(::File)
    private val baseDir = System.getProperty("emberkai.iconSheet.base")?.let(::File)
    private val output = System.getProperty("emberkai.iconSheet.output")?.let(::File)

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

    fun testRenderContactSheet() {
        val list = changedList ?: return
        // A changed dark variant is shown with its light artwork, and the other way round.
        val icons = list.readLines().map(String::trim).filter { it.endsWith(".svg") }
            .map { it.removeSuffix(DARK_SUFFIX).removeSuffix(".svg") }
            .distinct().sorted()
        val rows = icons.flatMap { icon ->
            val before = baseDir?.let { Row(icon, "before", it.resolve("$icon.svg"), it.resolve("$icon$DARK_SUFFIX")) }
                ?.takeIf { it.exists }
            val after = Row(icon, "after", File("$icon.svg"), File("$icon$DARK_SUFFIX")).takeIf { it.exists }
            when {
                before == null -> listOfNotNull(after?.copy(label = "added"))
                after == null -> listOf(before.copy(label = "removed"))
                else -> listOf(before, after)
            }
        }
        // An upstream icon refresh can touch hundreds of icons; nobody reviews those row by row.
        val sheet = render(rows.take(MAX_ROWS), hidden = (rows.size - MAX_ROWS).coerceAtLeast(0))
        val target = checkNotNull(output) { "emberkai.iconSheet.output is not set" }
        target.parentFile.mkdirs()
        ImageIO.write(sheet, "png", target)
    }

    private fun render(rows: List<Row>, hidden: Int): BufferedImage {
        val cellWidth = SCALES.sumOf { (16 * it).toInt() + GAP } + GAP
        val width = LABEL_WIDTH + 2 * cellWidth
        val height = HEADER + (rows.size + if (hidden > 0) 1 else 0).coerceAtLeast(1) * ROW
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = LIGHT
            g.fillRect(0, 0, LABEL_WIDTH + cellWidth, height)
            g.color = DARK
            g.fillRect(LABEL_WIDTH + cellWidth, 0, cellWidth, height)
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            g.color = TEXT
            g.drawString("Light", LABEL_WIDTH + GAP, 18)
            g.color = DARK_TEXT
            g.drawString("Dark", LABEL_WIDTH + cellWidth + GAP, 18)
            if (rows.isEmpty()) {
                g.color = TEXT
                g.drawString("No icons changed", GAP, HEADER + 20)
            }
            rows.forEachIndexed { index, row ->
                val top = HEADER + index * ROW
                g.color = TEXT
                g.drawString(row.icon.substringAfterLast("resources/"), GAP, top + 20)
                g.color = MUTED
                g.drawString(row.label, GAP, top + 36)
                paintScales(g, row.light, LABEL_WIDTH + GAP, top)
                paintScales(g, row.dark.takeIf(File::isFile) ?: row.light, LABEL_WIDTH + cellWidth + GAP, top)
            }
            if (hidden > 0) {
                g.color = MUTED
                g.drawString("…and $hidden more rows not shown", GAP, HEADER + rows.size * ROW + 20)
            }
        } finally {
            g.dispose()
        }
        return image
    }

    private fun paintScales(g: Graphics2D, file: File, left: Int, top: Int) {
        if (!file.isFile) return
        // Uncached, like IconArtworkLintTest, so the sheet leaves the icon cache as it found it.
        val icon = IconLoader.findIcon(file.toURI().toURL(), false) ?: return
        var x = left
        for (scale in SCALES) {
            val size = (16 * scale).toInt()
            val copy = g.create() as Graphics2D
            try {
                copy.translate(x, top + (ROW - size) / 2)
                copy.scale(scale, scale)
                icon.paintIcon(null, copy, 0, 0)
            } catch (e: Throwable) {
                // The lint reports why; the sheet only has to show that this artwork does not render.
                g.color = FAILED
                g.drawString("does not render", x, top + 28)
                return
            } finally {
                copy.dispose()
            }
            x += size + GAP
        }
    }

    private data class Row(val icon: String, val label: String, val light: File, val dark: File) {
        val exists: Boolean get() = light.isFile || dark.isFile
    }

    private companion object {
        const val DARK_SUFFIX = "_dark.svg"
        val SCALES = listOf(1.0, 1.5, 2.0)
        const val GAP = 14
        const val LABEL_WIDTH = 320
        const val HEADER = 28
        const val ROW = 48
        const val MAX_ROWS = 100
        val LIGHT = Color(0xF7F8FA)
        val DARK = Color(0x2B2D30)
        val TEXT = Color(0x1E1F22)
        val MUTED = Color(0x6C707E)
        val DARK_TEXT = Color(0xDFE1E5)
        val FAILED = Color(0xDC2626)
    }
}
