package com.github.khopland.symbols;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;

import javax.swing.Icon;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

public final class SymbolsIconProvider extends IconProvider {
    private static final Set<String> NATIVE_EXTENSIONS = Set.of("java", "class", "kt", "kts");
    private static final ResourceBundle ICONS = ResourceBundle.getBundle(
            "symbols.icons", Locale.ROOT, SymbolsIconProvider.class.getClassLoader());

    @Override
    public Icon getIcon(PsiElement element, int flags) {
        var file = PsiUtilCore.getVirtualFile(element);
        if (file == null) return null;

        String name = file.getName().toLowerCase(Locale.ROOT);
        if (file.isDirectory()) {
            Icon icon = icon("folder." + name);
            return icon != null ? icon : icon("folder");
        }

        // Defer to the IDE so Java/Kotlin type icons and modifier badges survive.
        String extension = file.getExtension();
        if (extension != null && NATIVE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            return null;
        }

        Icon icon = icon("file." + name);
        while (icon == null) {
            icon = icon("extension." + name);
            int dot = name.indexOf('.');
            if (dot < 0) break;
            name = name.substring(dot + 1);
        }
        return icon;
    }

    private static Icon icon(String key) {
        return ICONS.containsKey(key)
                ? IconLoader.getIcon("/symbols/icons/" + ICONS.getString(key), SymbolsIconProvider.class)
                : null;
    }
}
