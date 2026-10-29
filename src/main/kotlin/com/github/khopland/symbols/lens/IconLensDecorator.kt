package com.github.khopland.symbols.lens

import com.github.khopland.symbols.core.SemanticTooltipDecorator
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.SimpleTextAttributes

/**
 * Dims Project view nodes outside the active lens. Decorating the finished presentation, rather
 * than the resolved icon, also reaches native Java and Kotlin icons and class nodes, and leaves
 * editor tabs alone. Nodes without a file, such as External Libraries, are left as they are.
 */
class IconLensDecorator : ProjectViewNodeDecorator {
    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val project = node.project?.takeUnless { it.isDisposed } ?: return
        val snapshot = IconLensService.getIfCreated(project)?.snapshot ?: return
        val file = node.virtualFile ?: return
        if (snapshot.highlights(file)) return

        data.getIcon(false)?.let { data.setIcon(IconLoader.getDisabledIcon(it)) }
        val fragments = data.coloredText.toList()
        if (fragments.isEmpty()) {
            data.forcedTextForeground = SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor
        } else {
            data.clearText()
            fragments.forEach { data.addText(it.text, SimpleTextAttributes.GRAYED_ATTRIBUTES) }
        }
        data.tooltip = SemanticTooltipDecorator.appendTooltip(
            data.tooltip,
            "Outside the ${snapshot.lens.displayName} lens",
        )
    }
}
