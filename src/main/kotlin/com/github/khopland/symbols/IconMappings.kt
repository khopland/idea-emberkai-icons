package com.github.khopland.symbols

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import java.util.ResourceBundle
import javax.swing.Icon

/** Loads artwork and gives local mappings precedence over the upstream bundle. */
internal class IconMappings {
    private val upstream = ResourceBundle.getBundle(
        "symbols.icons", Locale.ROOT, SymbolsIconProvider::class.java.classLoader,
    )
    private val custom = ResourceBundle.getBundle(
        "emberkai.icons", Locale.ROOT, SymbolsIconProvider::class.java.classLoader,
    )

    fun get(key: String?): Icon? = resolve(key)?.icon

    fun resolve(key: String?): MappedIcon? {
        if (key == null) return null
        val source: MappingSource
        val path: String
        when {
            custom.containsKey(key) -> {
                source = MappingSource.CUSTOM
                path = custom.getString(key)
            }
            upstream.containsKey(key) -> {
                source = MappingSource.UPSTREAM
                path = "/symbols/icons/" + upstream.getString(key)
            }
            else -> return null
        }
        val icon = IconLoader.getIcon(path, SymbolsIconProvider::class.java)
        return MappedIcon(key, source, path, icon)
    }

    fun contextualMatch(file: VirtualFile, prefix: String, name: String): MappedIcon? =
        MappingKeys.contextual(file.parent, prefix, name, ::resolve)

    fun contextualExtensionMatch(file: VirtualFile, name: String): MappedIcon? =
        MappingKeys.contextualExtension(file.parent, name, ::resolve)

    fun extensionMatch(name: String): MappedIcon? = MappingKeys.extension(name, ::resolve)

    /** Every bundled mapping key and artwork path, excluding project-local overrides. */
    fun catalog(): List<IconCatalogEntry> {
        val entries = linkedMapOf<String, IconCatalogEntry>()
        upstream.keys.toList().forEach { key ->
            entries[key] = IconCatalogEntry(key, MappingSource.UPSTREAM, "/symbols/icons/" + upstream.getString(key))
        }
        custom.keys.toList().forEach { key ->
            entries[key] = IconCatalogEntry(key, MappingSource.CUSTOM, custom.getString(key))
        }
        return entries.values.sortedBy { it.key.lowercase(Locale.ROOT) }
    }
}

/**
 * The key lookup order shared by bundled mappings and project overrides. Contextual keys prefix a
 * name with parent folders, such as `file.deploy/prod/values.yaml`; the longest matching suffix wins
 * without depending on the checkout path. Compound extensions are tried before shorter ones, so
 * `extension.gradle.kts` wins over `extension.kts`.
 */
internal object MappingKeys {
    fun <T : Any> contextual(parent: VirtualFile?, prefix: String, name: String, lookup: (String) -> T?): T? {
        var current = parent
        var context = name
        var longest: T? = null
        while (current != null) {
            context = current.name.lowercase(Locale.ROOT) + "/" + context
            longest = lookup(prefix + context) ?: longest
            current = current.parent
        }
        return longest
    }

    fun <T : Any> contextualExtension(parent: VirtualFile?, name: String, lookup: (String) -> T?): T? =
        extensionSuffixes(name).firstNotNullOfOrNull { contextual(parent, "extension.", it, lookup) }

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

internal data class MappedIcon(
    val key: String,
    val source: MappingSource,
    val resourcePath: String,
    val icon: Icon,
)

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
