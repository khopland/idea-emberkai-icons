package com.github.khopland.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/** Recognizes Flux and Argo CD resources, bootstrap files, and configuration directories. */
internal class GitOpsIconRule : ContextIconRule {
    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? {
        if (isArgoConfig(name)) return "context.argocd"
        when (StructuredFileKind.of(file)) {
            StructuredFileKind.FLUX -> return "context.flux"
            StructuredFileKind.ARGOCD -> return "context.argocd"
            else -> Unit
        }
        if (name != "gotk-components.yaml" && name != "gotk-sync.yaml") return null
        return if (hasFluxSystemAncestor(file.parent, project)) "context.flux" else null
    }

    override fun folderIconKey(directory: VirtualFile): String? {
        val name = directory.name.lowercase(Locale.ROOT)
        return when (name) {
            "argocd", "argocd-apps" -> "context.argocd-folder"
            "flux-system" if hasFluxBootstrapFiles(directory) -> "context.flux-folder"
            else -> null
        }
    }

    private fun isArgoConfig(name: String): Boolean = name in ARGO_CONFIG_NAMES

    private fun hasFluxSystemAncestor(directory: VirtualFile?, project: Project?): Boolean {
        var current = directory
        while (current != null) {
            if (current.name.equals("flux-system", ignoreCase = true)) return true
            if (project != null && current.path == project.basePath) return false
            current = current.parent
        }
        return false
    }

    private fun hasFluxBootstrapFiles(directory: VirtualFile): Boolean =
        directory.findChild("gotk-components.yaml")?.isDirectory == false ||
            directory.findChild("gotk-sync.yaml")?.isDirectory == false

    private companion object {
        val ARGO_CONFIG_NAMES = setOf(
            "argocd-cm.yaml", "argocd-cm.yml", "argocd-cm.json",
            "argocd-rbac-cm.yaml", "argocd-rbac-cm.yml", "argocd-rbac-cm.json",
            "argocd-secret.yaml", "argocd-secret.yml", "argocd-secret.json",
            "argocd-cmd-params-cm.yaml", "argocd-cmd-params-cm.yml", "argocd-cmd-params-cm.json",
            "argocd-notifications-cm.yaml", "argocd-notifications-cm.yml", "argocd-notifications-cm.json",
        )
    }
}
