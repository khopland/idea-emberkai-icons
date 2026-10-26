package com.github.khopland.symbols

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager

internal object RefreshProjectIconPresentations {
    fun refresh() {
        ProjectManager.getInstance().openProjects
            .filterNot { it.isDisposed }
            .forEach { project ->
                ProjectView.getInstance(project).refresh()
                val editors = FileEditorManager.getInstance(project)
                editors.openFiles.forEach(editors::updateFilePresentation)
            }
    }
}
