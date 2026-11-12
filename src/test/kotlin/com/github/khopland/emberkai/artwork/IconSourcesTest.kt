package com.github.khopland.emberkai.artwork

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps src/main/resources/emberkai/icons, and its retinted copies in emberkai/palettes, in step with
 * the sources in design/icons. After editing a source, a token or a palette, regenerate the artwork with:
 *
 *     ./gradlew test --tests '*IconSourcesTest' -PwriteIcons
 */
internal class IconSourcesTest {
    @Test
    fun generatedArtworkIsUpToDate() {
        val icons = IconSources.fromRepository().generate()
        // Each theme palette in design/palettes gets a retinted copy of Emberkai's set, plus the color
        // table that code-painted accents use.
        val generated = icons.mapKeys { (path) -> "icons/$path" } + IconPalette.fromRepository().flatMap { palette ->
            val root = "palettes/${palette.id}"
            icons.map { (path, text) -> "$root/icons/$path" to palette.apply(text) } +
                ("$root/colors.properties" to palette.colorTable())
        }
        val shipped = GENERATED.flatMap { File(ARTWORK, it).generatedFiles().entries }
            .associate { (_, file) -> file.relativeTo(ARTWORK).invariantSeparatorsPath to file }
        if (System.getProperty("emberkai.writeIcons") != null) {
            shipped.filterKeys { it !in generated }.values.forEach(File::delete)
            generated.forEach { (path, text) -> File(ARTWORK, path).apply { parentFile.mkdirs() }.writeText(text) }
            return
        }
        val problems = generated.mapNotNull { (path, text) ->
            when (val file = shipped[path]) {
                null -> "$path: missing"
                else -> "$path: differs from its source".takeIf { file.readText() != text }
            }
        } + shipped.keys.filterNot(generated::containsKey).map { "$it: has no source in design/icons or design/palettes" }
        assertTrue(
            "${problems.size} icon(s) out of date; run ./gradlew test --tests '*IconSourcesTest' -PwriteIcons\n" +
                problems.joinToString("\n"),
            problems.isEmpty(),
        )
    }

    @Test
    fun tokensAndThemeGroupsMakeADarkVariant() {
        val dir = Files.createTempDirectory("emberkai-sources").toFile()
        try {
            fun source(name: String, body: String) = File(dir, name).writeText("<svg>\n$body</svg>\n")
            source("plain.svg", """<path fill="green-400" stroke="none"/>""" + "\n")
            source("token.svg", """<path stroke="${'$'}ink" fill="url(#g)"/>""" + "\n")
            source(
                "groups.svg",
                """<g data-theme="light">""" + "\n" + """<circle fill="white"/>""" + "\n</g>\n" +
                    """<g data-theme="dark">""" + "\n" + """<g transform="scale(2)"><circle fill="black"/></g>""" + "\n</g>\n",
            )
            val generated = IconSources(
                dir,
                mapOf("green-400" to "#4ADE80", "slate-800" to "#1E293B", "slate-200" to "#E2E8F0", "white" to "#FFFFFF", "black" to "#000000"),
                mapOf("ink" to ("slate-800" to "slate-200")),
            ).generate()

            assertEquals(
                mapOf(
                    "plain.svg" to "<svg>\n<path fill=\"#4ADE80\" stroke=\"none\"/>\n</svg>\n",
                    "token.svg" to "<svg>\n<path stroke=\"#1E293B\" fill=\"url(#g)\"/>\n</svg>\n",
                    "token_dark.svg" to "<svg>\n<path stroke=\"#E2E8F0\" fill=\"url(#g)\"/>\n</svg>\n",
                    "groups.svg" to "<svg>\n<circle fill=\"#FFFFFF\"/>\n</svg>\n",
                    "groups_dark.svg" to "<svg>\n<g transform=\"scale(2)\"><circle fill=\"#000000\"/></g>\n</svg>\n",
                ),
                generated,
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun palettesKeepEachShadesLuminanceAndTakeTheThemeHue() {
        val palette = IconPalette.of(
            "test",
            mapOf("blue-500" to "#3B82F6", "blue-600" to "#2563EB", "white" to "#FFFFFF", "spring-green" to "#6DB33F"),
            mapOf("blue" to "#5E81AC"),
        )
        val recolored = palette.apply("""<path fill="#2563EB" stroke="#FFFFFF"/><path fill="#6DB33F"/><path fill="#3b82f6"/>""")

        assertEquals("White and brand colors are kept", 2, Regex("#FFFFFF|#6DB33F").findAll(recolored).count())
        val (deep, bright) = Regex("""fill="(#[0-9A-F]{6})"""").findAll(recolored).map { it.groupValues[1] }.toList().let { it[0] to it[2] }
        assertFalse(deep == "#2563EB" || bright == "#3B82F6")
        for ((original, retinted) in listOf("#2563EB" to deep, "#3B82F6" to bright)) {
            assertEquals("$original keeps its luminance", IconPalette.luminance(original), IconPalette.luminance(retinted), 0.004)
        }
        assertEquals("the theme color itself is kept", "#5E81AC", IconPalette.retint("#5E81AC", "#5E81AC", "#5E81AC"))
    }

    /** Service folders and module accents tell modules apart by color, so no palette may merge two of them. */
    @Test
    fun palettesKeepServiceFolderColorsApart() {
        val colors = File("design/palette.properties").reader().use { java.util.Properties().apply { load(it) } }
        for (palette in IconPalette.fromRepository()) {
            val hues = SERVICE_ACCENTS.associateWith { IconPalette.hue(palette.apply(colors.getProperty("$it-500"))) }
            for ((i, a) in SERVICE_ACCENTS.withIndex()) for (b in SERVICE_ACCENTS.drop(i + 1)) {
                val apart = Math.abs(hues.getValue(a) - hues.getValue(b)).let { minOf(it, 360 - it) }
                assertTrue("${palette.id}: $a and $b are only ${apart.toInt()}° apart", apart >= MIN_HUE_DISTANCE)
            }
        }
    }

    @Test
    fun unknownColorsFail() {
        val dir = Files.createTempDirectory("emberkai-sources").toFile()
        try {
            for (paint in listOf("""fill="${'$'}nope"""", """fill="green-401"""")) {
                File(dir, "a.svg").writeText("<svg><path $paint/></svg>")
                val failure = runCatching { IconSources(dir, mapOf("green-400" to "#4ADE80"), emptyMap()).generate() }
                assertTrue(paint, failure.isFailure)
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private companion object {
        val ARTWORK = File("src/main/resources/emberkai")
        val SERVICE_ACCENTS = listOf("blue", "green", "orange", "pink", "purple", "red", "sky", "yellow")

        // Tailwind's own red and orange, the closest pair, are 23° apart.
        const val MIN_HUE_DISTANCE = 20.0
        val GENERATED = listOf("icons", "palettes")

        fun File.generatedFiles(): Map<String, File> = walk().filter { it.isFile && it.extension in setOf("svg", "properties") }
            .associateBy { it.relativeTo(this).invariantSeparatorsPath }
    }
}
