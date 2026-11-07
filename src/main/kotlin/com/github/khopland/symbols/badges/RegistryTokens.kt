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
 * variable, such as `${NPM_TOKEN}`, are how these files are meant to be committed, so they are skipped.
 */
internal object RegistryTokens {
    private val NPMRC_ENTRY = Regex("""^\s*(?:\S*:)?(_authToken|_auth|_password)\s*=\s*(.*?)\s*$""")
    private val YARNRC_ENTRY = Regex("""^\s*(npmAuthToken|npmAuthIdent)\s*:\s*(.*?)\s*$""")
    private val CACHE = Key.create<FileContentCache.Inspection<List<RegistryToken>>>("emberkai.registryTokens")

    fun literal(file: VirtualFile): List<RegistryToken> {
        if (file.isDirectory) return emptyList()
        val entry = when (file.name.lowercase(Locale.ROOT)) {
            ".npmrc" -> NPMRC_ENTRY
            ".yarnrc.yml", ".yarnrc.yaml" -> YARNRC_ENTRY
            else -> return emptyList()
        }
        return FileContentCache.inspect(file, CACHE, emptyList()) { literal(it, entry) }
    }

    internal fun literal(text: String, entry: Regex): List<RegistryToken> =
        text.lineSequence().mapIndexedNotNull { index, line ->
            val match = entry.find(line) ?: return@mapIndexedNotNull null
            val value = match.groupValues[2].trim('"', '\'')
            if (value.isEmpty() || "\${" in value) null else RegistryToken(index + 1, match.groupValues[1])
        }.toList()

    internal fun npmrc(text: String): List<RegistryToken> = literal(text, NPMRC_ENTRY)

    internal fun yarnrc(text: String): List<RegistryToken> = literal(text, YARNRC_ENTRY)
}
