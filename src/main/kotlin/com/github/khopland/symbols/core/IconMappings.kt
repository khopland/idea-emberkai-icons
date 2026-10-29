package com.github.khopland.symbols.core

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import java.util.ResourceBundle
import javax.swing.Icon

/**
 * Loads artwork and gives local mappings precedence over the upstream bundle.
 *
 * Both bundles are read into maps once. A [ResourceBundle] lookup is a hash lookup plus fallback
 * work, and the Project view asks for an icon for every node it draws, so the hot path stays on
 * plain maps. Icons load on first use, so the intermediate matches the contextual search discards
 * never touch [IconLoader].
 */
internal class IconMappings {
    private val upstream = load("symbols.icons")
    private val custom = load("emberkai.icons")

    /** Deepest contextual key per prefix, so the parent walk stops where a match can still exist. */
    private val contextDepths: Map<String, Int> = buildMap {
        for (key in upstream.keys + custom.keys) {
            merge(key.substringBefore('.') + ".", key.count { it == '/' }, ::maxOf)
        }
    }

    fun get(key: String?): Icon? = resolve(key)?.icon

    fun resolve(key: String?): MappedIcon? {
        if (key == null) return null
        custom[key]?.let { return MappedIcon(key, MappingSource.CUSTOM, it) }
        upstream[key]?.let { return MappedIcon(key, MappingSource.UPSTREAM, "/symbols/icons/$it") }
        return null
    }

    fun contextualMatch(file: VirtualFile, prefix: String, name: String): MappedIcon? =
        MappingKeys.contextual(file.parent, prefix, name, contextDepth(prefix), ::resolve)

    fun contextualExtensionMatch(file: VirtualFile, name: String): MappedIcon? =
        MappingKeys.contextualExtension(file.parent, name, contextDepth("extension."), ::resolve)

    fun extensionMatch(name: String): MappedIcon? = MappingKeys.extension(name, ::resolve)

    /** Every bundled mapping key and artwork path, excluding project-local overrides. */
    fun catalog(): List<IconCatalogEntry> {
        val entries = linkedMapOf<String, IconCatalogEntry>()
        upstream.forEach { (key, path) ->
            entries[key] = IconCatalogEntry(key, MappingSource.UPSTREAM, "/symbols/icons/$path")
        }
        custom.forEach { (key, path) ->
            entries[key] = IconCatalogEntry(key, MappingSource.CUSTOM, path)
        }
        return entries.values.sortedBy { it.key.lowercase(Locale.ROOT) }
    }

    private fun contextDepth(prefix: String): Int = contextDepths[prefix] ?: 0

    private fun load(bundle: String): Map<String, String> {
        val resource = ResourceBundle.getBundle(bundle, Locale.ROOT, SymbolsIconProvider::class.java.classLoader)
        return resource.keys.asSequence().associateWith(resource::getString)
    }
}

/**
 * The key lookup order shared by bundled mappings and project overrides. Contextual keys prefix a
 * name with parent folders, such as `file.deploy/prod/values.yaml`; the longest matching suffix wins
 * without depending on the checkout path. Compound extensions are tried before shorter ones, so
 * `extension.gradle.kts` wins over `extension.kts`.
 *
 * [maxDepth] bounds the parent walk: bundled keys are at most two folders deep, so a file in a deep
 * tree no longer builds and looks up a key for every ancestor up to the filesystem root. Callers
 * with user-defined keys pass the deepest depth among their keys.
 */
internal object MappingKeys {
    fun <T : Any> contextual(
        parent: VirtualFile?,
        prefix: String,
        name: String,
        maxDepth: Int = Int.MAX_VALUE,
        lookup: (String) -> T?,
    ): T? {
        var current = parent
        var context = name
        var longest: T? = null
        var depth = 0
        while (current != null && depth < maxDepth) {
            context = current.name.lowercase(Locale.ROOT) + "/" + context
            longest = lookup(prefix + context) ?: longest
            current = current.parent
            depth++
        }
        return longest
    }

    fun <T : Any> contextualExtension(
        parent: VirtualFile?,
        name: String,
        maxDepth: Int = Int.MAX_VALUE,
        lookup: (String) -> T?,
    ): T? = extensionSuffixes(name).firstNotNullOfOrNull { contextual(parent, "extension.", it, maxDepth, lookup) }

    fun <T : Any> extension(name: String, lookup: (String) -> T?): T? =
        extensionSuffixes(name).firstNotNullOfOrNull { lookup("extension.$it") }

    /** `app.gradle.kts` yields itself, `gradle.kts`, and `kts`. */
    fun extensionSuffixes(name: String): Sequence<String> =
        generateSequence(name) { it.substringAfter('.', "").ifEmpty { null } }
}

internal enum class MappingSource(val displayName: String) {
    UPSTREAM("Upstream Symbols mapping"),
    CUSTOM("Custom Emberkai mapping"),
    PROJECT_OVERRIDE("Project icon override"),
}

/** A mapping match; [icon] is loaded on first use so discarded contextual matches stay cheap. */
internal class MappedIcon(
    val key: String,
    val source: MappingSource,
    val resourcePath: String,
) {
    val icon: Icon by lazy(LazyThreadSafetyMode.PUBLICATION) {
        IconLoader.getIcon(resourcePath, SymbolsIconProvider::class.java)
    }
}

internal data class IconCatalogEntry(
    val key: String,
    val source: MappingSource,
    val resourcePath: String,
) {
    val icon: Icon?
        get() = try {
            IconLoader.findIcon(resourcePath, SymbolsIconProvider::class.java)
        } catch (_: RuntimeException) {
            null
        }
}
