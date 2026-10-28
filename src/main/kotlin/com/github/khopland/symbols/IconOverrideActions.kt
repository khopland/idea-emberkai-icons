package com.github.khopland.symbols

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

/** Opens the project-local override file, creating it on first use. */
internal class OpenIconOverridesAction : AnAction("Open Icon Overrides File"), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = ProjectIconOverrideStore.getInstance(project).ensurePropertiesFile()
        if (file == null) {
            Messages.showErrorDialog(project, "Could not create the project overrides file.", "Emberkai Icons")
            return
        }
        OpenFileDescriptor(project, file).navigate(true)
    }
}

/** Rereads override files from disk; the store repaints icons once the refresh lands. */
internal class ReloadIconOverridesAction : AnAction("Reload Icon Overrides"), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        ProjectIconOverrideStore.getInstance(project).reload()
    }
}

/** Opens the read-only icon browser. */
internal class BrowseIconCatalogAction : AnAction("Browse Bundled Icons"), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        IconCatalogDialog(project, selectable = false).show()
    }
}

/** Creates a project override for the selected file or folder. */
internal class OverrideIconAction : AnAction("Override This Icon…"), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null &&
            event.getData(CommonDataKeys.VIRTUAL_FILE)?.isValid == true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf(VirtualFile::isValid) ?: return
        val resolution = computeReadAction { IconResolver().resolveWithDetails(file, project) }
        if (IconOverrideKeySuggestions.keepsNativeIcon(resolution)) {
            Messages.showInfoMessage(
                project,
                "${file.name} keeps its native IDE icon, which takes precedence over project overrides.",
                "Override This Icon",
            )
            return
        }
        val key = IconOverrideKeySuggestions.forFile(file, resolution)
        val existing = ProjectIconOverrideStore.getInstance(project).overrides()[key]
        IconOverrideDialog(project, file, key, existing).show()
    }
}
