package com.github.khopland.symbols.rules

import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.StructuredFileKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Handles request/response contracts (OpenAPI, Swagger) and event contracts (AsyncAPI, Avro) with
 * arbitrary filenames when their root metadata identifies them. Avro schemas are drawn by
 * extension mappings and only join the API contracts scope here.
 */
internal class ApiSpecificationIconRule : ContextIconRule {
    override val category = IconRuleCategory.API_SPECIFICATION

    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? = when {
        name in OPEN_API_NAMES -> "context.openapi"
        name in ASYNC_API_NAMES -> "context.asyncapi"
        else -> when (StructuredFileKind.of(file)) {
            StructuredFileKind.OPEN_API -> "context.openapi"
            StructuredFileKind.ASYNC_API -> "context.asyncapi"
            else -> null
        }
    }

    override fun recognizesFile(file: VirtualFile, name: String, project: Project?): Boolean =
        AVRO_EXTENSIONS.any { name.endsWith(it) } || fileIconKey(file, name, project) != null

    override fun folderIconKey(directory: VirtualFile, project: Project?): String? {
        for (name in OPEN_API_NAMES + ASYNC_API_NAMES) {
            val child = directory.findChild(name)
            if (child != null && !child.isDirectory) return "context.openapi-folder"
        }
        return null
    }

    private companion object {
        val OPEN_API_NAMES = setOf(
            "openapi.yaml", "openapi.yml", "openapi.json",
            "swagger.yaml", "swagger.yml", "swagger.json",
        )
        val ASYNC_API_NAMES = setOf("asyncapi.yaml", "asyncapi.yml", "asyncapi.json")
        val AVRO_EXTENSIONS = listOf(".avsc", ".avdl", ".avpr")
    }
}
