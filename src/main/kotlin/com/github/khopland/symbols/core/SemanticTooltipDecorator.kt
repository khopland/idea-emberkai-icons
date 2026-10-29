package com.github.khopland.symbols.core

import com.github.khopland.symbols.ownership.CodeOwners
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.stereotypes.ClassStereotypes
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.util.text.StringUtil.escapeXmlEntities
import com.intellij.psi.PsiElement

/**
 * Adds a short description of what Emberkai recognized to Project view tooltips. Swing exposes a
 * tree cell's tooltip as its accessible description, so screen readers announce the same text.
 * Native tooltips are kept, and the description is appended after them. Class and file nodes whose
 * native icon carries a Spring stereotype badge also name the stereotype. Files and folders governed
 * by a CODEOWNERS file name their owners, under a separate setting.
 */
class SemanticTooltipDecorator : ProjectViewNodeDecorator {
    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val settings = EmberkaiIconsSettings.getInstance()
        val description = listOfNotNull(
            if (settings.semanticTooltips) fileDescription(node) else null,
            if (settings.semanticTooltips) (node.value as? PsiElement)?.let(ClassStereotypes::describe) else null,
            if (settings.codeOwnerTooltips) ownerDescription(node) else null,
        ).takeIf { it.isNotEmpty() } ?: return
        data.tooltip = appendTooltip(data.tooltip, description.joinToString(" · "))
    }

    private fun ownerDescription(node: ProjectViewNode<*>): String? {
        if (node !is PsiFileNode && node !is PsiDirectoryNode) return null
        val file = node.virtualFile?.takeIf { it.isValid } ?: return null
        val ownership = CodeOwners.ownership(file) ?: return null
        return describeOwners(ownership.owners)
    }

    private fun fileDescription(node: ProjectViewNode<*>): String? {
        if (node !is PsiFileNode && node !is PsiDirectoryNode) return null
        val project = node.project ?: return null
        val file = node.virtualFile?.takeIf { it.isValid } ?: return null
        return IconDescriptions.describe(file, IconResolver.resolveWithDetails(file, project))
    }

    internal companion object {
        fun describeOwners(owners: List<String>): String =
            if (owners.isEmpty()) "No code owners" else "Owned by ${owners.joinToString(", ")}"

        fun appendTooltip(existing: String?, description: String): String {
            if (existing.isNullOrBlank()) return description
            if (description in existing) return existing
            val closing = existing.lastIndexOf("</html>", ignoreCase = true)
            if (closing >= 0) {
                return existing.substring(0, closing) + "<br>" + escapeXmlEntities(description) + existing.substring(closing)
            }
            return "$existing · $description"
        }
    }
}
