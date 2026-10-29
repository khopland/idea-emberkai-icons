package com.github.khopland.symbols

import java.io.IOException
import java.io.StringReader
import java.util.Locale
import java.util.Properties

/**
 * Pure helpers for `.idea/emberkai-icons.properties`. Edits are applied line by line so comments,
 * ordering, and unrelated entries survive, and no timestamp churn reaches version control.
 */
internal object ProjectIconOverrideFile {
    private val KEY_PREFIXES = listOf("file.", "extension.", "folder.")
    private val DRIVE = Regex("^[a-zA-Z]:")

    fun normalizeKey(key: String): String = key.trim().replace('\\', '/').lowercase(Locale.ROOT)

    fun isSupportedKey(key: String): Boolean {
        val normalized = normalizeKey(key)
        return KEY_PREFIXES.any { normalized.startsWith(it) && normalized.length > it.length }
    }

    /** Malformed content yields the entries parsed before the error rather than an exception. */
    fun parse(text: String): Map<String, String> {
        val properties = Properties()
        try {
            properties.load(StringReader(text))
        } catch (_: IOException) {
        } catch (_: IllegalArgumentException) {
            // Properties.load rejects malformed \uXXXX escapes after loading the preceding lines.
        }
        val values = linkedMapOf<String, String>()
        properties.stringPropertyNames().sorted().forEach { key ->
            val value = properties.getProperty(key).trim()
            if (value.isNotEmpty()) values[normalizeKey(key)] = value
        }
        return values
    }

    /**
     * A relative artwork path confined to `.idea/emberkai-icons/`, using `/` separators, or null
     * when the value is absolute or escapes the directory.
     */
    fun relativeArtworkPath(value: String): String? {
        val normalized = value.trim().replace('\\', '/').removePrefix("./")
        if (normalized.isEmpty() || normalized.startsWith("/") || DRIVE.containsMatchIn(normalized)) return null
        val segments = normalized.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
        return normalized
    }

    fun isBundledPath(value: String): Boolean = value.trim().startsWith("/")

    /** Only SVG or PNG resources shipped with the plugin may be referenced by absolute path. */
    fun isBundledArtwork(path: String): Boolean {
        val trimmed = path.trim()
        val lower = trimmed.lowercase(Locale.ROOT)
        return isBundledPath(trimmed) && (lower.endsWith(".svg") || lower.endsWith(".png")) &&
            ProjectIconOverrideFile::class.java.getResource(trimmed) != null
    }

    /** Sets [key] to [value], or removes it when [value] is null, preserving every other line. */
    fun update(text: String, key: String, value: String?): String {
        val target = normalizeKey(key)
        val lines = if (text.isEmpty()) mutableListOf() else text.split('\n').toMutableList()
        if (lines.isNotEmpty() && lines.last().isEmpty()) lines.removeAt(lines.lastIndex)
        val result = mutableListOf<String>()
        var replaced = false
        var index = 0
        while (index < lines.size) {
            val start = index
            while (continues(lines[index]) && index + 1 < lines.size) index++
            index++
            val logical = lines.subList(start, index)
            if (entryKey(logical.first())?.let(::normalizeKey) != target) {
                result += logical
                continue
            }
            if (value != null && !replaced) result += entry(target, value)
            replaced = true
        }
        if (!replaced && value != null) result += entry(target, value)
        return result.joinToString("\n", postfix = "\n")
    }

    /** One `key=value` line, escaped so [Properties] reads both back unchanged. */
    fun entry(key: String, value: String): String = "${escape(key, isKey = true)}=${escape(value, isKey = false)}"

    private fun continues(line: String): Boolean {
        if (isComment(line)) return false
        val trailing = line.trimEnd('\r').takeLastWhile { it == '\\' }.length
        return trailing % 2 == 1
    }

    private fun isComment(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")
    }

    private fun entryKey(line: String): String? {
        if (isComment(line)) return null
        val trimmed = line.trimStart()
        val key = StringBuilder()
        var index = 0
        while (index < trimmed.length) {
            val char = trimmed[index]
            if (char == '\\' && index + 1 < trimmed.length) {
                key.append(trimmed[index + 1])
                index += 2
                continue
            }
            if (char == '=' || char == ':' || char.isWhitespace()) break
            key.append(char)
            index++
        }
        return key.toString()
    }

    private fun escape(text: String, isKey: Boolean): String = buildString {
        text.forEachIndexed { index, char ->
            when {
                char == '\\' -> append("\\\\")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                isKey && (char == '=' || char == ':' || char == ' ' || char == '#' || char == '!') ->
                    append('\\').append(char)
                !isKey && index == 0 && char == ' ' -> append("\\ ")
                else -> append(char)
            }
        }
    }
}
