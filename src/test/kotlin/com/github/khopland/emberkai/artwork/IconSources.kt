package com.github.khopland.emberkai.artwork

import java.io.File
import java.util.Properties

/**
 * Generates Emberkai's shipped SVGs from the token sources in design/icons, so the light and dark
 * variants of an icon cannot drift apart. A source names its colors instead of spelling hex:
 *
 * - a palette name from design/palette.properties, such as `fill="green-400"`, is the same color in
 *   both themes;
 * - a token from design/tokens.properties, such as `stroke="$ink"`, is a different palette color in
 *   each theme;
 * - `<g data-theme="light">` and `<g data-theme="dark">` keep their content only in that theme, for
 *   artwork whose shapes differ, such as the dark stereotype badges' larger glyphs.
 *
 * Each source yields `<name>.svg`, plus `<name>_dark.svg` when the two themes come out different.
 */
internal class IconSources(
    private val sources: File,
    private val palette: Map<String, String>,
    private val tokens: Map<String, Pair<String, String>>,
) {
    /** Every generated file, by its path relative to the artwork root, such as `folders/folder-api_dark.svg`. */
    fun generate(): Map<String, String> = buildMap {
        for (source in sources.walk().filter { it.isFile && it.extension == "svg" }.sorted()) {
            val path = source.relativeTo(sources).invariantSeparatorsPath
            val text = source.readText()
            val light = render(path, text, Theme.LIGHT)
            val dark = render(path, text, Theme.DARK)
            put(path, light)
            if (dark != light) put(path.removeSuffix(".svg") + DARK_SUFFIX, dark)
        }
    }

    private fun render(path: String, source: String, theme: Theme): String =
        PAINT.replace(selectTheme(path, source, theme)) { match ->
            val (attribute, value) = match.destructured
            val name = if (value.startsWith('$')) {
                val token = tokens[value.removePrefix("$")] ?: error("$path: unknown token $value in design/tokens.properties")
                if (theme == Theme.LIGHT) token.first else token.second
            } else {
                value
            }
            val color = palette[name] ?: when {
                // url(#…), none, currentColor and the like pass through unchanged.
                value in NON_COLORS || !NAME.matches(value) -> return@replace match.value
                else -> error("$path: $name is not in design/palette.properties")
            }
            """$attribute="$color""""
        }

    /** Unwraps the `data-theme` groups for [theme] and drops the others, with the lines they open and close on. */
    private fun selectTheme(path: String, source: String, theme: Theme): String {
        val out = StringBuilder()
        var index = 0
        while (true) {
            val open = THEME_GROUP.find(source, index) ?: break
            out.append(source, index, open.range.first)
            val close = closingTag(path, source, open.range.last + 1)
            if (open.groupValues[1] == theme.id) out.append(source, open.range.last + 1, close.first)
            index = close.last + 1
        }
        return out.append(source, index, source.length).toString()
    }

    /** The range of the `</g>` (and the newline after it) that closes a group whose content starts at [from]. */
    private fun closingTag(path: String, source: String, from: Int): IntRange {
        var depth = 1
        for (tag in GROUP_TAG.findAll(source, from)) {
            depth += when {
                tag.value.startsWith("</") -> -1
                tag.value.endsWith("/>") -> 0
                else -> 1
            }
            if (depth == 0) {
                val end = if (source.startsWith("\n", tag.range.last + 1)) tag.range.last + 1 else tag.range.last
                return tag.range.first..end
            }
        }
        error("$path: a data-theme group is never closed")
    }

    private enum class Theme(val id: String) { LIGHT("light"), DARK("dark") }

    companion object {
        const val DARK_SUFFIX = "_dark.svg"
        private val PAINT = Regex("""\b(fill|stroke|stop-color|flood-color|lighting-color|color)="([^"]*)"""")
        private val NON_COLORS = setOf("none", "inherit", "transparent")
        private val NAME = Regex("""\$?[a-z][a-z0-9-]*""")
        private val THEME_GROUP = Regex("""<g data-theme="(light|dark)">\n?""")
        private val GROUP_TAG = Regex("""</g>|<g\b[^>]*>""")

        /** The sources in design/icons, with the repository's palette and tokens. */
        fun fromRepository(): IconSources {
            val palette = properties("design/palette.properties")
            val tokens = properties("design/tokens.properties").mapValues { (name, value) ->
                val parts = value.trim().split(Regex("\\s+"))
                require(parts.size == 2) { "design/tokens.properties: $name must be \"<light> <dark>\"" }
                parts[0] to parts[1]
            }
            return IconSources(File("design/icons"), palette, tokens)
        }

        private fun properties(path: String): Map<String, String> =
            Properties().apply { File(path).reader().use(::load) }.entries.associate { (k, v) -> k as String to v as String }
    }
}
