package com.github.khopland.symbols.rules

import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.StructuredFileKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/**
 * Recognizes Kubernetes objects and resource kinds from root metadata, Kustomize trees from their
 * marker, and Kubernetes-shaped tool files: Tekton resources and Skaffold configuration.
 */
internal class KubernetesIconRule : ContextIconRule {
    override val category = IconRuleCategory.KUBERNETES

    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? {
        if (name == "kustomization.yaml" || name == "kustomization.yml" || name == "kustomization") {
            return "context.kustomize"
        }
        if (name == "skaffold.yaml" || name == "skaffold.yml") return "context.skaffold"
        return when (StructuredFileKind.of(file)) {
            StructuredFileKind.KUBERNETES -> StructuredFileKind.kubernetesResource(file)
                ?.let { "context.kubernetes.$it" } ?: "context.kubernetes"
            StructuredFileKind.KUSTOMIZE -> "context.kustomize"
            StructuredFileKind.TEKTON -> "context.tekton"
            StructuredFileKind.SKAFFOLD -> "context.skaffold"
            else -> null
        }
    }

    override fun folderIconKey(directory: VirtualFile, project: Project?): String? {
        if (hasKustomization(directory)) return "context.kustomize-folder"
        if (directory.name.lowercase(Locale.ROOT) !in LAYERS) return null
        var parent = directory.parent
        while (parent != null) {
            if (hasKustomization(parent)) return "context.kustomize-folder"
            parent = parent.parent
        }
        return null
    }

    private fun hasKustomization(directory: VirtualFile): Boolean {
        for (name in MARKERS) {
            val marker = directory.findChild(name)
            if (marker != null && !marker.isDirectory) return true
        }
        return false
    }

    private companion object {
        val LAYERS = setOf("base", "bases", "overlays", "components")
        val MARKERS = arrayOf("kustomization.yaml", "kustomization.yml", "Kustomization")
    }
}
