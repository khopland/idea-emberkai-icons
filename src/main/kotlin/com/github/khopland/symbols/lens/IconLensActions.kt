package com.github.khopland.symbols.lens

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

/** View > Emberkai Icons > Lens: one radio-style entry per lens, plus No Lens. */
class IconLensActionGroup : ActionGroup(), DumbAware {
    private val children: Array<AnAction> = arrayOf(
        SelectIconLensAction(null),
        Separator.create(),
        *IconLens.entries.map(::SelectIconLensAction).toTypedArray(),
    )

    override fun getChildren(event: AnActionEvent?): Array<AnAction> = children

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

internal class SelectIconLensAction(private val lens: IconLens?) : ToggleAction(
    lens?.displayName ?: "No Lens",
    lens?.let { "Keep ${it.displayName.lowercase()} files in full color and dim the rest of the Project view" }
        ?: "Show every file in full color",
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
