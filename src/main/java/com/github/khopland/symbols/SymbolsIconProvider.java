package com.github.khopland.symbols;

import com.intellij.ide.FileIconProvider;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiUtilCore;
import org.jspecify.annotations.NonNull;

import javax.swing.Icon;

/** IntelliJ entry points; matching and tool-specific rules live in focused helpers. */
public final class SymbolsIconProvider extends IconProvider implements FileIconProvider, DumbAware {
    private final IconResolver resolver = new IconResolver();

    @Override
    public Icon getIcon(@NonNull PsiElement element, int flags) {
        var file = PsiUtilCore.getVirtualFile(element);
        if (file == null) return null;
        // Gradle scripts get file icons; declarations still use IDE type icons.
        if (!(element instanceof PsiFileSystemItem) && NativeIconPolicy.isSource(file)) return null;
        return resolver.resolve(file, element.getProject());
    }

    @Override
    public Icon getIcon(@NonNull VirtualFile file, int flags, Project project) {
        return resolver.resolve(file, project);
    }
}
