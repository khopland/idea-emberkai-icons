package com.github.khopland.symbols.overrides

import com.github.khopland.symbols.core.IconPrecedence
import com.github.khopland.symbols.core.IconResolution
import com.github.khopland.symbols.core.MappingKeys
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/** Chooses a useful starting key when the user asks to override an icon. */
internal object IconOverrideKeySuggestions {
    /** Native IDE icons and native project roles cannot be replaced by project overrides. */
    fun keepsNativeIcon(resolution: IconResolution): Boolean =
        resolution.precedence == IconPrecedence.NATIVE_POLICY ||
            (resolution.precedence == IconPrecedence.PROJECT_ROLE && resolution.icon == null)

    fun forFile(file: VirtualFile, resolution: IconResolution): String {
        // Reuse the winning key only when an override with that key would match this file;
        // role and context keys such as folder.role.source are not looked up for overrides.
        val current = resolution.mappingKey
        if (current != null && matches(file, current)) return current
        val name = file.name.lowercase(Locale.ROOT)
        if (file.isDirectory) return "folder.$name"
        val extension = file.extension?.lowercase(Locale.ROOT)
        return if (extension.isNullOrBlank()) "file.$name" else "extension.$extension"
    }

    /** Whether an override with [key] would be considered for [file]. */
    fun matches(file: VirtualFile, key: String): Boolean {
        val normalized = ProjectIconOverrideFile.normalizeKey(key)
        val name = file.name.lowercase(Locale.ROOT)
        val names = if (file.isDirectory) {
            listOf("folder.$name")
        } else {
            listOf("file.$name") + MappingKeys.extensionSuffixes(name).map { "extension.$it" }
        }
        return names.any { candidate ->
            if (normalized == candidate) return@any true
            val prefix = candidate.substringBefore('.') + "."
            val leaf = candidate.removePrefix(prefix)
            if (!normalized.startsWith(prefix) || !normalized.endsWith("/$leaf")) return@any false
            val context = normalized.removePrefix(prefix).removeSuffix("/$leaf")
            parentSuffixes(file).contains(context)
        }
    }

    private fun parentSuffixes(file: VirtualFile): Set<String> {
        val suffixes = mutableSetOf<String>()
        var current = file.parent
        var context = ""
        while (current != null) {
            val parentName = current.name.lowercase(Locale.ROOT)
            context = if (context.isEmpty()) parentName else "$parentName/$context"
            suffixes += context
            current = current.parent
        }
        return suffixes
    }
}
