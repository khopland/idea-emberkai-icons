package com.github.khopland.symbols.lens

import com.github.khopland.symbols.ownership.CodeOwners
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir

/**
 * View > Emberkai Icons > Lens: one radio-style entry per lens, plus No Lens, and an Owned by
 * submenu with every owner named in the project's CODEOWNERS file.
 */
class IconLensActionGroup : ActionGroup(), DumbAware {
    private val fixed: List<AnAction> = listOf(
        SelectIconLensAction(null),
        Separator.create(),
        *IconLens.entries.map(::SelectIconLensAction).toTypedArray(),
    )

    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        val owners = event?.project?.let(::projectOwners).orEmpty()
        if (owners.isEmpty()) return fixed.toTypedArray()
        val ownerGroup = DefaultActionGroup("Owned By", owners.map { SelectIconLensAction(OwnerLens(it)) }).apply {
            isPopup = true
        }
        return (fixed + Separator.create() + ownerGroup).toTypedArray()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun projectOwners(project: Project): List<String> {
        val codeOwners = project.guessProjectDir()?.let(CodeOwners::fileAt) ?: return emptyList()
        return CodeOwners.owners(codeOwners).sortedBy { it.lowercase() }
    }
}

internal class SelectIconLensAction(private val lens: Lens?) : ToggleAction(
    when (lens) {
        null -> "No Lens"
        is OwnerLens -> lens.owner
        else -> lens.displayName
    },
    when (lens) {
        null -> "Show every file in full color"
        is OwnerLens -> "Keep files owned by ${lens.owner} in full color and dim the rest of the Project view"
        else -> "Keep ${lens.displayName.lowercase()} files in full color and dim the rest of the Project view"
    },
    null,
), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isEnabled = event.project != null
    }

    override fun isSelected(event: AnActionEvent): Boolean {
        val project = event.project ?: return lens == null
        return IconLensService.getIfCreated(project)?.active == lens
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
        val project = event.project ?: return
        // Unchecking the active lens turns lenses off; unchecking No Lens does nothing.
        IconLensService.getInstance(project).select(if (state) lens else null)
    }
}
