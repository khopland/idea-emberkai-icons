package com.github.khopland.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Handles API contracts with arbitrary filenames when their root metadata identifies them. */
internal class ApiSpecificationIconRule : ContextIconRule {
    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? =
        if (name in SPEC_NAMES || StructuredFileKind.of(file) == StructuredFileKind.OPEN_API) {
            "context.openapi"
        } else {
            null
        }

    override fun folderIconKey(directory: VirtualFile): String? {
        for (name in SPEC_NAMES) {
            val child = directory.findChild(name)
            if (child != null && !child.isDirectory) return "context.openapi-folder"
        }
        return null
    }

    private companion object {
        val SPEC_NAMES = setOf(
            "openapi.yaml", "openapi.yml", "openapi.json",
            "swagger.yaml", "swagger.yml", "swagger.json",
        )
    }
}
