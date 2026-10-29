package com.github.khopland.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import java.util.regex.Pattern

/** Recognizes chart metadata, templates, and folders using a Chart.yaml marker. */
internal class HelmIconRule : ContextIconRule {
    override val category = IconRuleCategory.HELM

    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? {
        if (name == "chart.yaml") return "context.helm"
        if (!isMetadata(name) && !isTemplate(name)) return null

        var relative = file.name
        var dir = file.parent
        while (dir != null) {
            if (hasChart(dir)) {
                val lower = relative.lowercase(Locale.ROOT)
                val metadata = lower == name && isMetadata(name)
                val template = lower.startsWith("templates/") && isTemplate(name)
                val crd = lower.startsWith("crds/") && isYaml(name)
                return if (metadata || template || crd) "context.helm" else null
            }
            relative = dir.name + "/" + relative
            if (project != null && ProjectPathUtil.isProjectBase(dir, project)) break
            dir = dir.parent
        }
        return null
    }

    override fun folderIconKey(directory: VirtualFile, project: Project?): String? {
        if (hasChart(directory)) return "context.helm-folder"
        val parent = directory.parent
        return if (parent != null && directory.name.lowercase(Locale.ROOT) in CHART_DIRECTORIES && hasChart(parent)) {
            "context.helm-folder"
        } else {
            null
        }
    }

    private fun isMetadata(name: String): Boolean =
        name == "chart.lock" || name == "values.schema.json" || VALUES.matcher(name).matches()

    private fun isTemplate(name: String): Boolean =
        isYaml(name) || name.endsWith(".tpl") || name == "notes.txt"

    private fun isYaml(name: String): Boolean = name.endsWith(".yaml") || name.endsWith(".yml")

    private fun hasChart(directory: VirtualFile): Boolean {
        val chart = directory.findChild("Chart.yaml")
        return chart != null && !chart.isDirectory
    }

    private companion object {
        val VALUES: Pattern = Pattern.compile("values(?:-.+)?\\.ya?ml")
        val CHART_DIRECTORIES = setOf("templates", "charts", "crds")
    }
}
