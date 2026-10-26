package com.github.khopland.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import javax.swing.Icon

/** Defines icon precedence; add new tool detectors to contextRules in priority order. */
internal class IconResolver {
    private val mappings = IconMappings()
    private val projectFolders = ProjectFolderIcons()
    private val sourceSets = SourceSetFolders()
    private val serviceFolders = ServiceFolderIcons(mappings)
    private val contextRules = listOf(
        CiConfigIconRule(),
        LiquibaseIconRule(),
        HelmIconRule(),
        AnsibleIconRule(),
        InfrastructureIconRule(),
        GitOpsIconRule(),
        MigrationIconRule(),
        ApiSpecificationIconRule(),
        KubernetesIconRule(),
        SpringBootIconRule(),
    )

    fun resolve(file: VirtualFile, project: Project?): Icon? {
        val name = file.name.lowercase(Locale.ROOT)
        return if (file.isDirectory) folderIcon(file, name, project) else fileIcon(file, name, project)
    }

    private fun folderIcon(directory: VirtualFile, name: String, project: Project?): Icon? {
        if (NativeIconPolicy.usesFolderIcon(directory, name, project)) return null

        val projectRole = projectFolders.role(directory, project)
        if (projectRole == ProjectFolderIcons.Role.NATIVE) return null
        if (projectRole != null && projectRole != ProjectFolderIcons.Role.CONTENT_ROOT) {
            return mappings.get(projectRole.iconKey)
        }
        if (projectRole == ProjectFolderIcons.Role.CONTENT_ROOT &&
            EmberkaiIconsSettings.getInstance().colorServiceFolders
        ) {
            serviceFolders.icon(directory, project)?.let { return it }
        }
        val role = projectRole ?: sourceSets.role(directory)
        if (role != null) return mappings.get(role.iconKey)

        var icon = mappings.contextual(directory, "folder.", name)
        val settings = EmberkaiIconsSettings.getInstance()
        for (rule in contextRules) {
            if (!settings.isEnabled(rule.category)) continue
            if (icon != null) break
            val key = rule.folderIconKey(directory)
            if (key != null) icon = mappings.get(key)
        }
        if (icon == null && EmberkaiIconsSettings.getInstance().colorServiceFolders) {
            icon = serviceFolders.icon(directory, project)
        }
        if (icon == null) icon = mappings.get("folder.$name")
        return icon ?: mappings.get("folder")
    }

    private fun fileIcon(file: VirtualFile, name: String, project: Project?): Icon? {
        if (NativeIconPolicy.usesFileIcon(file, name)) return null

        var icon = mappings.contextual(file, "file.", name)
        if (icon == null) icon = mappings.get("file.$name")
        if (icon == null) icon = mappings.contextualExtension(file, name)
        val settings = EmberkaiIconsSettings.getInstance()
        for (rule in contextRules) {
            if (!settings.isEnabled(rule.category)) continue
            if (icon != null) break
            val key = rule.fileIconKey(file, name, project)
            if (key != null) icon = mappings.get(key)
        }
        return icon ?: mappings.extension(name)
    }
}
