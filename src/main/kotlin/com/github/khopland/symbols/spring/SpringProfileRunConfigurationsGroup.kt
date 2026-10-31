package com.github.khopland.symbols.spring

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

/**
 * One entry per run configuration that activates Spring profiles. Selecting one makes it the
 * selected run configuration, which is where the active profile comes from. Shown in the
 * Emberkai status bar widget; registered from `java-support.xml`.
 */
internal class SpringProfileRunConfigurationsGroup : ActionGroup(), DumbAware {
    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        val project = event?.project ?: return emptyArray()
        return RunManager.getInstance(project).allSettings
            .mapNotNull { settings -> ActiveSpringProfileReader.read(settings)?.let { SelectAction(settings, it) } }
            .sortedBy { it.templateText.lowercase() }
            .toTypedArray()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private class SelectAction(
        private val settings: RunnerAndConfigurationSettings,
        snapshot: ActiveSpringProfiles.Snapshot,
    ) : ToggleAction(
        "${snapshot.profiles.sorted().joinToString(", ")} (${settings.name})",
        "Select the ${settings.name} run configuration, which activates ${snapshot.profiles.sorted().joinToString(", ")}",
        null,
    ), DumbAware {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun isSelected(event: AnActionEvent): Boolean =
            event.project?.let { RunManager.getInstance(it).selectedConfiguration } == settings

        override fun setSelected(event: AnActionEvent, state: Boolean) {
            val project = event.project ?: return
            if (state) RunManager.getInstance(project).selectedConfiguration = settings
        }
    }
}
