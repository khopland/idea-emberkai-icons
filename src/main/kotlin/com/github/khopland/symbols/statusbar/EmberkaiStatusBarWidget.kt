package com.github.khopland.symbols.statusbar

import com.github.khopland.symbols.lens.IconLensService
import com.github.khopland.symbols.spring.ActiveSpringProfiles
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WindowManager

/**
 * Shows the active lens and the Spring profiles of the selected run configuration. Clicking it
 * offers every lens and, with Java support, the run configurations that activate profiles.
 */
internal class EmberkaiStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {
    override fun ID(): String = ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getSelectedValue(): String = text(
        IconLensService.getIfCreated(project)?.active?.displayName,
        ActiveSpringProfiles.getIfCreated(project)?.snapshot?.profiles,
    )

    override fun getTooltipText(): String {
        val configuration = ActiveSpringProfiles.getIfCreated(project)?.snapshot?.configurationName
        return "Choose an Emberkai lens" + (configuration?.let { " or run configuration; profiles come from $it" } ?: "")
    }

    override fun getPopup(): JBPopup = JBPopupFactory.getInstance().createActionGroupPopup(
        null,
        popupActions(),
        SimpleDataContext.getProjectContext(project),
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
    )

    private fun popupActions(): ActionGroup {
        val actions = ActionManager.getInstance()
        val group = DefaultActionGroup()
        (actions.getAction(LENS_GROUP) as? ActionGroup)?.let { group.add(Inlined(it)) }
        (actions.getAction(PROFILE_GROUP) as? ActionGroup)?.let {
            group.add(Separator.create("Spring Profiles"))
            group.add(Inlined(it))
        }
        return group
    }

    /** Shows a popup group's entries in place instead of as a submenu. */
    private class Inlined(private val group: ActionGroup) : ActionGroup(), DumbAware {
        override fun getChildren(event: AnActionEvent?): Array<AnAction> = group.getChildren(event)

        override fun getActionUpdateThread() = group.actionUpdateThread
    }

    override fun dispose() = Unit

    companion object {
        const val ID = "com.github.khopland.symbols.StatusBar"
        private const val LENS_GROUP = "com.github.khopland.symbols.IconLens"
        private const val PROFILE_GROUP = "com.github.khopland.symbols.SpringProfileRunConfigurations"

        fun text(lens: String?, profiles: Set<String>?): String {
            val parts = listOfNotNull(
                lens?.let { "Lens: $it" },
                profiles?.takeIf { it.isNotEmpty() }?.let { "Profile: ${it.sorted().joinToString(", ")}" },
            )
            return parts.joinToString(" · ").ifEmpty { "No lens" }
        }

        /** Redraws the widget after the lens or the active profiles change. Call on the EDT. */
        fun update(project: Project) {
            if (!project.isDisposed) WindowManager.getInstance().getStatusBar(project)?.updateWidget(ID)
        }
    }
}

internal class EmberkaiStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = EmberkaiStatusBarWidget.ID

    override fun getDisplayName(): String = "Emberkai Lens and Spring Profile"

    override fun createWidget(project: Project): StatusBarWidget = EmberkaiStatusBarWidget(project)

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
