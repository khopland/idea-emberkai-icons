package com.github.khopland.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/** Recognizes Kubernetes objects from root metadata and Kustomize trees from their marker file. */
internal class KubernetesIconRule : ContextIconRule {
    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? {
        if (name == "kustomization.yaml" || name == "kustomization.yml" || name == "kustomization") {
            return "context.kustomize"
        }
        return when (StructuredFileKind.of(file)) {
            StructuredFileKind.KUBERNETES -> "context.kubernetes"
            StructuredFileKind.KUSTOMIZE -> "context.kustomize"
            else -> null
        }
    }

    override fun folderIconKey(directory: VirtualFile): String? {
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
