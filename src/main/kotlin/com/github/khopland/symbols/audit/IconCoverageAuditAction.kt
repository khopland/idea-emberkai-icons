package com.github.khopland.symbols.audit

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/** Starts an audit for the entire project from the Tools menu. */
internal class AuditProjectIconCoverageAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project?.isDisposed == false
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project?.takeUnless { it.isDisposed } ?: return
        val basePath = project.basePath ?: return
        val root = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return
        IconCoverageAuditRunner.start(project, root, wholeProject = true)
    }
}

/** Starts an audit for the selected Project view directory. */
internal class AuditDirectoryIconCoverageAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val directory = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible = event.project?.isDisposed == false && directory?.isDirectory == true
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project?.takeUnless { it.isDisposed } ?: return
        val directory = event.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.isValid && it.isDirectory } ?: return
        IconCoverageAuditRunner.start(project, directory)
    }
}

private object IconCoverageAuditRunner {
    fun start(project: Project, root: VirtualFile, wholeProject: Boolean = false) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Auditing icon coverage", true) {
            override fun run(indicator: ProgressIndicator) {
                val result = IconCoverageAudit.run(project, root, indicator, wholeProject)
                ApplicationManager.getApplication().invokeLater({
                    if (!project.isDisposed) IconCoverageAuditDialog(project, result).show()
                }, ModalityState.nonModal())
            }
        })
    }
}
