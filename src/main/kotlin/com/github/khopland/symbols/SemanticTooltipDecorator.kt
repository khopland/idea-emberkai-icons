package com.github.khopland.symbols

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode

/**
 * Adds a short description of what Emberkai recognized to Project view tooltips. Swing exposes a
 * tree cell's tooltip as its accessible description, so screen readers announce the same text.
 * Native tooltips are kept, and the description is appended after them.
 */
class SemanticTooltipDecorator internal constructor(
    private val resolver: IconResolver,
) : ProjectViewNodeDecorator {
    constructor() : this(IconResolver())

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        if (node !is PsiFileNode && node !is PsiDirectoryNode) return
        if (!EmberkaiIconsSettings.getInstance().semanticTooltips) return
        val project = node.project ?: return
        val file = node.virtualFile?.takeIf { it.isValid } ?: return
        val description = IconDescriptions.describe(file, resolver.resolveWithDetails(file, project)) ?: return
        data.tooltip = appendTooltip(data.tooltip, description)
    }

    internal companion object {
        fun appendTooltip(existing: String?, description: String): String {
            if (existing.isNullOrBlank()) return description
            if (description in existing) return existing
            val closing = existing.lastIndexOf("</html>", ignoreCase = true)
            if (closing >= 0) {
                return existing.substring(0, closing) + "<br>" + escape(description) + existing.substring(closing)
            }
            return "$existing · $description"
        }

        private fun escape(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
