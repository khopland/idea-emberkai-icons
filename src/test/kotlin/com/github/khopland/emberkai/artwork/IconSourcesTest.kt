package com.github.khopland.emberkai.artwork

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps src/main/resources/emberkai/icons in step with its sources in design/icons. After editing a
 * source or a token, regenerate the artwork with:
 *
 *     ./gradlew test --tests '*IconSourcesTest' -PwriteIcons
 */
internal class IconSourcesTest {
    @Test
    fun generatedArtworkIsUpToDate() {
        val generated = IconSources.fromRepository().generate()
        if (System.getProperty("emberkai.writeIcons") != null) {
            ARTWORK.svgs().filterKeys { it !in generated }.values.forEach(File::delete)
            generated.forEach { (path, text) -> File(ARTWORK, path).apply { parentFile.mkdirs() }.writeText(text) }
            return
        }
        val shipped = ARTWORK.svgs()
        val problems = generated.mapNotNull { (path, text) ->
            when (val file = shipped[path]) {
                null -> "$path: missing"
                else -> "$path: differs from its source".takeIf { file.readText() != text }
            }
        } + shipped.keys.filterNot(generated::containsKey).map { "$it: has no source in design/icons" }
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
        val ARTWORK = File("src/main/resources/emberkai/icons")

        fun File.svgs(): Map<String, File> = walk().filter { it.isFile && it.extension == "svg" }
            .associateBy { it.relativeTo(this).invariantSeparatorsPath }
    }
}
