package com.github.khopland.symbols

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.psi.PsiElement

/**
 * Adds a short description of what Emberkai recognized to Project view tooltips. Swing exposes a
 * tree cell's tooltip as its accessible description, so screen readers announce the same text.
 * Native tooltips are kept, and the description is appended after them. Class and file nodes whose
 * native icon carries a Spring stereotype badge also name the stereotype.
 */
class SemanticTooltipDecorator internal constructor(
    private val resolver: IconResolver,
) : ProjectViewNodeDecorator {
    constructor() : this(IconResolver())

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        if (!EmberkaiIconsSettings.getInstance().semanticTooltips) return
        val description = listOfNotNull(
            fileDescription(node),
            (node.value as? PsiElement)?.let(ClassStereotypes::describe),
        ).takeIf { it.isNotEmpty() } ?: return
        data.tooltip = appendTooltip(data.tooltip, description.joinToString(" · "))
    }

    private fun fileDescription(node: ProjectViewNode<*>): String? {
        if (node !is PsiFileNode && node !is PsiDirectoryNode) return null
        val project = node.project ?: return null
        val file = node.virtualFile?.takeIf { it.isValid } ?: return null
        return IconDescriptions.describe(file, resolver.resolveWithDetails(file, project))
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
