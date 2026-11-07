package com.github.khopland.symbols.badges

import com.github.khopland.symbols.core.FileContentCache
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/** A package registry credential written into a config file, by its key and 1-based line. */
internal data class RegistryToken(val line: Int, val key: String)

/**
 * Finds npm and Yarn registry credentials written as literal values: `_authToken`, `_auth`, and
 * `_password` in `.npmrc` (also when scoped to a registry, as in `//registry.npmjs.org/:_authToken`),
 * and `npmAuthToken` and `npmAuthIdent` in `.yarnrc.yml`. Values that reference an environment
 * variable, such as `${NPM_TOKEN}`, are how these files are meant to be committed, so they are
 * skipped, as are comment lines and empty or null values.
 */
internal object RegistryTokens {
    private val NPMRC_ENTRY = Regex("""^\s*(?:\S*:)?(_authToken|_auth|_password)\s*=\s*(.*?)\s*$""")
    private val YARNRC_ENTRY = Regex("""^\s*(npmAuthToken|npmAuthIdent)\s*:(.*)$""")
    private val YAML_COMMENT = Regex("""(?:^|\s)#.*$""")
    private val UNSET = setOf("", "null", "~")
    private val CACHE = Key.create<FileContentCache.Inspection<List<RegistryToken>>>("emberkai.registryTokens")

    fun literal(file: VirtualFile): List<RegistryToken> {
        if (file.isDirectory) return emptyList()
        val reader: (String) -> List<RegistryToken> = when (file.name.lowercase(Locale.ROOT)) {
            ".npmrc" -> ::npmrc
            ".yarnrc.yml" -> ::yarnrc
            else -> return emptyList()
        }
        // On the EDT, reading the file would be a slow operation; use what a background lookup cached.
        if (!FileContentCache.mayLoad()) return FileContentCache.peek(file, CACHE)?.value.orEmpty()
        return FileContentCache.inspect(file, CACHE, emptyList(), reader)
    }

    internal fun npmrc(text: String): List<RegistryToken> = tokens(text, NPMRC_ENTRY, setOf('#', ';')) { raw ->
        unquote(raw)
    }

    internal fun yarnrc(text: String): List<RegistryToken> = tokens(text, YARNRC_ENTRY, setOf('#')) { raw ->
        val value = raw.trim()
        if (value.startsWith('"') || value.startsWith('\'')) unquote(value) else value.replace(YAML_COMMENT, "").trim()
    }

    private fun tokens(text: String, entry: Regex, comments: Set<Char>, value: (String) -> String): List<RegistryToken> =
        text.lineSequence().mapIndexedNotNull { index, line ->
            if (line.trimStart().firstOrNull() in comments) return@mapIndexedNotNull null
            val match = entry.find(line) ?: return@mapIndexedNotNull null
            val literal = value(match.groupValues[2])
            if (literal in UNSET || '$' in literal) null else RegistryToken(index + 1, match.groupValues[1])
        }.toList()

    /** The text inside a leading quote up to its closing quote, or the trimmed value when unquoted. */
    private fun unquote(raw: String): String {
        val value = raw.trim()
        val quote = value.firstOrNull()?.takeIf { it == '"' || it == '\'' } ?: return value
        return value.substring(1).substringBefore(quote)
    }
}
