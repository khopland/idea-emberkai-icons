package com.github.khopland.symbols.rules

import com.github.khopland.symbols.core.IconRuleCategory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/** Recognizes CI configuration only by each provider's conventional path or filename. */
internal class CiConfigIconRule : ContextIconRule {
    override val category = IconRuleCategory.CI

    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? {
        val extension = file.extension?.lowercase(Locale.ROOT) ?: return null
        if (extension !in YAML_EXTENSIONS) return null

        return when {
            name == "bitbucket-pipelines.yml" || name == "bitbucket-pipelines.yaml" -> "context.ci.bitbucket"
            name.startsWith("azure-pipelines") -> "context.ci.azurepipelines"
            else -> null
        }
    }

    override fun recognizesFile(file: VirtualFile, name: String, project: Project?): Boolean {
        if (name == "jenkinsfile" || name == ".gitlab-ci.yml" || name == ".gitlab-ci.yaml") return true
        if ((name == "config.yml" || name == "config.yaml") && file.parent?.name == ".circleci") return true
        if (file.extension?.lowercase(Locale.ROOT) in YAML_EXTENSIONS &&
            file.parent?.name == "workflows" && file.parent?.parent?.name == ".github"
        ) {
            return true
        }
        return fileIconKey(file, name, project) != null
    }

    private companion object {
        val YAML_EXTENSIONS = setOf("yml", "yaml")
    }
}
