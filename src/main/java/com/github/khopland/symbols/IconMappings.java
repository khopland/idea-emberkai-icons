package com.github.khopland.symbols;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.Icon;
import java.util.Locale;
import java.util.ResourceBundle;

/** Loads artwork and gives local mappings precedence over the upstream bundle. */
final class IconMappings {
    private final ResourceBundle upstream = ResourceBundle.getBundle(
            "symbols.icons", Locale.ROOT, SymbolsIconProvider.class.getClassLoader());
    private final ResourceBundle custom = ResourceBundle.getBundle(
            "emberkai.icons", Locale.ROOT, SymbolsIconProvider.class.getClassLoader());

    Icon get(String key) {
        String path = custom.containsKey(key) ? custom.getString(key)
                : upstream.containsKey(key) ? "/symbols/icons/" + upstream.getString(key) : null;
        return path == null ? null : IconLoader.getIcon(path, SymbolsIconProvider.class);
    }

    Icon contextual(VirtualFile file, String prefix, String name) {
        // A longer parent suffix wins without depending on the checkout path.
        VirtualFile parent = file.getParent();
        if (parent == null) return null;
        String context = parent.getName().toLowerCase(Locale.ROOT) + "/" + name;
        Icon closest = get(prefix + context);
        while ((parent = parent.getParent()) != null) {
            context = parent.getName().toLowerCase(Locale.ROOT) + "/" + context;
            Icon icon = get(prefix + context);
            if (icon != null) closest = icon;
        }
        return closest;
    }

    Icon contextualExtension(VirtualFile file, String name) {
        while (true) {
            Icon icon = contextual(file, "extension.", name);
            if (icon != null) return icon;
            int dot = name.indexOf('.');
            if (dot < 0) return null;
            name = name.substring(dot + 1);
        }
    }

    Icon extension(String name) {
        while (true) {
            Icon icon = get("extension." + name);
            if (icon != null) return icon;
            int dot = name.indexOf('.');
            if (dot < 0) return null;
            name = name.substring(dot + 1);
        }
    }
}
