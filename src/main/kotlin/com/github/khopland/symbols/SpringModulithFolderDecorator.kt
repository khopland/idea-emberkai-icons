package com.github.khopland.symbols

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiDirectory
import javax.swing.Icon

/** The project-view operations needed to combine Emberkai folders with Modulith package roles. */
internal interface ModulithSupport {
    fun lockOf(icon: Icon?): ModulithLock?

    fun hasModulithLibrary(directory: PsiDirectory): Boolean
}

internal object SpringModulithProjectViewSupport : ModulithSupport {
    override fun lockOf(icon: Icon?): ModulithLock? = SpringModulithSupport.lockOf(icon)

    override fun hasModulithLibrary(directory: PsiDirectory): Boolean {
        val module = ModuleUtil.findModuleForPsiElement(directory) ?: return false
        return SpringModulithSupport.hasModulithLibrary(module)
    }
}

/**
 * Combines Spring Modulith's package semantics with Emberkai folder artwork. The extension is
 * registered with order="last" so it runs after Modulith's own decorator, which is the only way to
 * see which lock icon (or blank icon) the framework chose.
 */
class SpringModulithFolderDecorator internal constructor(
    private val support: ModulithSupport,
) : ProjectViewNodeDecorator {
    constructor() : this(SpringModulithProjectViewSupport)

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        if (node !is PsiDirectoryNode) return
        if (!EmberkaiIconsSettings.getInstance().isEnabled(IconRuleCategory.SPRING_MODULITH)) return
        val directory = node.value

        val currentIcon = data.getIcon(false)
        val lock = support.lockOf(currentIcon)
        if (lock != null) {
            val base = IconResolver.resolve(directory.virtualFile, directory.project) ?: return
            data.setIcon(ModulithFolderIcon(base, lock))
            return
        }

        // Modulith clears the icon for packages outside a module; restore Emberkai artwork only
        // when the module really uses Modulith, so unrelated decorators keep their decisions.
        if (currentIcon != null) return
        if (!support.hasModulithLibrary(directory)) return
        data.setIcon(IconResolver.resolve(directory.virtualFile, directory.project))
    }
}
