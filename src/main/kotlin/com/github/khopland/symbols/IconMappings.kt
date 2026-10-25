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

    fun get(key: String?): Icon? {
        val path = when {
            key == null -> null
            custom.containsKey(key) -> custom.getString(key)
            upstream.containsKey(key) -> "/symbols/icons/" + upstream.getString(key)
            else -> null
        }
        return path?.let { IconLoader.getIcon(it, SymbolsIconProvider::class.java) }
    }

    fun contextual(file: VirtualFile, prefix: String, name: String): Icon? {
        // A longer parent suffix wins without depending on the checkout path.
        var parent = file.parent ?: return null
        var context = parent.name.lowercase(Locale.ROOT) + "/" + name
        var closest = get(prefix + context)
        while (true) {
            parent = parent.parent ?: break
            context = parent.name.lowercase(Locale.ROOT) + "/" + context
            val icon = get(prefix + context) ?: continue
            closest = icon
        }
        return closest
    }

    fun contextualExtension(file: VirtualFile, name: String): Icon? {
        var current = name
        while (true) {
            val icon = contextual(file, "extension.", current)
            if (icon != null) return icon
            val dot = current.indexOf('.')
            if (dot < 0) return null
            current = current.substring(dot + 1)
        }
    }

    fun extension(name: String): Icon? {
        var current = name
        while (true) {
            val icon = get("extension." + current)
            if (icon != null) return icon
            val dot = current.indexOf('.')
            if (dot < 0) return null
            current = current.substring(dot + 1)
        }
    }
}
