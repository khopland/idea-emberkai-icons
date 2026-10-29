package com.github.khopland.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Returns mapping keys so tool detection stays independent of artwork paths. */
internal interface ContextIconRule {
    val category: IconRuleCategory

    fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? = null

    /** Scope membership; also covers canonical names whose artwork comes from static mappings. */
    fun recognizesFile(file: VirtualFile, name: String, project: Project?): Boolean =
        fileIconKey(file, name, project) != null

    fun folderIconKey(directory: VirtualFile, project: Project?): String? = null
}
