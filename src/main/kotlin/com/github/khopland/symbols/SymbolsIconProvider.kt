package com.github.khopland.symbols

import com.intellij.ide.FileIconProvider
import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiUtilCore
import javax.swing.Icon

/** IntelliJ entry points; matching and tool-specific rules live in focused helpers. */
class SymbolsIconProvider : IconProvider(), FileIconProvider, DumbAware {
    private val resolver = IconResolver()

    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        val file = PsiUtilCore.getVirtualFile(element) ?: return null
        // Gradle scripts get file icons; declarations still use IDE type icons.
        if (element !is PsiFileSystemItem && NativeIconPolicy.isSource(file)) return null
        return resolver.resolve(file, element.project)
    }

    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? =
        resolver.resolve(file, project)
}
