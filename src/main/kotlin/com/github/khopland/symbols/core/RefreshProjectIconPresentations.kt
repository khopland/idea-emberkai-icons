package com.github.khopland.symbols.core

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

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
}
