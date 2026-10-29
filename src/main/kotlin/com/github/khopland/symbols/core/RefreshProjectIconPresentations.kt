package com.github.khopland.symbols.core

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

internal object RefreshProjectIconPresentations {
    fun refresh() {
        ProjectManager.getInstance().openProjects.forEach(::refresh)
    }

    fun refresh(project: Project) {
        if (project.isDisposed) return
        ProjectView.getInstance(project).refresh()
        val editors = FileEditorManager.getInstance(project)
        editors.openFiles.forEach(editors::updateFilePresentation)
    }

    /** Repaints only [directories]' Project view nodes, falling back to a full refresh without a pane. */
    fun refresh(project: Project, directories: Collection<VirtualFile>) {
        if (project.isDisposed || directories.isEmpty()) return
        val pane = ProjectView.getInstance(project).currentProjectViewPane ?: return refresh(project)
        val psiManager = PsiManager.getInstance(project)
        for (directory in directories) {
            if (!directory.isValid) continue
            psiManager.findDirectory(directory)?.let { pane.updateFrom(it, false, false) }
        }
    }
}
