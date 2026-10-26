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
        val icon = IconLoader.findIcon(path, SymbolsIconProvider::class.java) ?: return null
        return MappedIcon(key, source, path, icon)
    }

    fun contextual(file: VirtualFile, prefix: String, name: String): Icon? {
        return contextualMatch(file, prefix, name)?.icon
    }

    fun contextualMatch(file: VirtualFile, prefix: String, name: String): MappedIcon? {
        // A longer parent suffix wins without depending on the checkout path.
        var parent = file.parent ?: return null
        var context = parent.name.lowercase(Locale.ROOT) + "/" + name
        var closest = resolve(prefix + context)
        while (true) {
            parent = parent.parent ?: break
            context = parent.name.lowercase(Locale.ROOT) + "/" + context
            val icon = resolve(prefix + context) ?: continue
            closest = icon
        }
        return closest
    }

    fun contextualExtension(file: VirtualFile, name: String): Icon? {
        return contextualExtensionMatch(file, name)?.icon
    }

    fun contextualExtensionMatch(file: VirtualFile, name: String): MappedIcon? {
        var current = name
        while (true) {
            val icon = contextualMatch(file, "extension.", current)
            if (icon != null) return icon
            val dot = current.indexOf('.')
            if (dot < 0) return null
            current = current.substring(dot + 1)
        }
    }

    fun extension(name: String): Icon? {
        return extensionMatch(name)?.icon
    }

    fun extensionMatch(name: String): MappedIcon? {
        var current = name
        while (true) {
            val icon = resolve("extension.$current")
            if (icon != null) return icon
            val dot = current.indexOf('.')
            if (dot < 0) return null
            current = current.substring(dot + 1)
        }
    }
}

internal enum class MappingSource(val displayName: String) {
    UPSTREAM("Upstream Symbols mapping"),
    CUSTOM("Custom Emberkai mapping"),
}

internal data class MappedIcon(
    val key: String,
    val source: MappingSource,
    val resourcePath: String,
    val icon: Icon,
)
