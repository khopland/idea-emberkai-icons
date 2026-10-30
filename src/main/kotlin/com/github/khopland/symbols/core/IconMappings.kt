package com.github.khopland.symbols.core

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import java.util.ResourceBundle
import javax.swing.Icon

/**
 * Loads artwork and gives local mappings precedence over the upstream bundle.
 *
 * Both bundles are read into one map of [MappedIcon]s once, shared by the resolver, the settings
 * preview and the catalog. A [ResourceBundle] lookup is a hash lookup plus fallback work, and the
 * Project view asks for an icon for every node it draws, so the hot path stays on a plain map that
 * allocates nothing. Each icon loads on first use and is then reused.
 */
internal object IconMappings {
    private val icons: Map<String, MappedIcon> = buildMap {
        load("symbols.icons").forEach { (key, path) ->
            put(key, MappedIcon(key, MappingSource.UPSTREAM, "/symbols/icons/$path"))
        }
        load("emberkai.icons").forEach { (key, path) -> put(key, MappedIcon(key, MappingSource.CUSTOM, path)) }
    }

    private val contextDepths = MappingKeys.contextDepths(icons.keys)

    fun get(key: String?): Icon? = resolve(key)?.icon

    fun resolve(key: String?): MappedIcon? = key?.let(icons::get)

    fun contextualMatch(file: VirtualFile, prefix: String, name: String): MappedIcon? =
        MappingKeys.contextual(file.parent, prefix, name, contextDepths[prefix] ?: 0, ::resolve)

    fun contextualExtensionMatch(file: VirtualFile, name: String): MappedIcon? =
        MappingKeys.contextualExtension(file.parent, name, contextDepths["extension."] ?: 0, ::resolve)

    fun extensionMatch(name: String): MappedIcon? = MappingKeys.extension(name, ::resolve)

    /** Every bundled mapping key and artwork path, excluding project-local overrides. */
    fun catalog(): List<IconCatalogEntry> = icons.values
        .map { IconCatalogEntry(it.key, it.source, it.resourcePath) }
        .sortedBy { it.key.lowercase(Locale.ROOT) }

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
 * `maxDepth` bounds the parent walk, so a file in a deep tree does not build and look up a key for
 * every ancestor up to the filesystem root. Callers derive it from their keys with [contextDepths].
 */
internal object MappingKeys {
    /** Folder depth of the deepest key per prefix, such as `file.` or `extension.`. */
    fun contextDepths(keys: Iterable<String>): Map<String, Int> = keys
        .groupingBy { it.substringBefore('.') + "." }
        .fold(0) { depth, key -> maxOf(depth, key.count { it == '/' }) }

    fun <T : Any> contextual(
        parent: VirtualFile?,
        prefix: String,
        name: String,
        maxDepth: Int,
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
        maxDepth: Int,
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

/** A mapping match; [icon] is loaded on first use and then kept. */
internal data class MappedIcon(
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
