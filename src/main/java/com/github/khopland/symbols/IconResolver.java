package com.github.khopland.symbols;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.Icon;
import java.util.List;
import java.util.Locale;

/** Defines icon precedence; add new tool detectors to contextRules in priority order. */
final class IconResolver {
    private final IconMappings mappings = new IconMappings();
    private final ProjectFolderIcons projectFolders = new ProjectFolderIcons();
    private final SourceSetFolders sourceSets = new SourceSetFolders();
    private final List<ContextIconRule> contextRules = List.of(new HelmIconRule(), new MigrationIconRule(),
            new ApiSpecificationIconRule(), new KubernetesIconRule(), new SpringBootIconRule());

    Icon resolve(VirtualFile file, Project project) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return file.isDirectory() ? folderIcon(file, name, project) : fileIcon(file, name, project);
    }

    private Icon folderIcon(VirtualFile directory, String name, Project project) {
        if (NativeIconPolicy.usesFolderIcon(directory, name, project)) return null;

        ProjectFolderIcons.Role role = projectFolders.role(directory, project);
        if (role == ProjectFolderIcons.Role.NATIVE) return null;
        if (role == null) role = sourceSets.role(directory);
        if (role != null) return mappings.get(role.iconKey);

        Icon icon = mappings.contextual(directory, "folder.", name);
        for (ContextIconRule rule : contextRules) {
            if (icon != null) break;
            String key = rule.folderIconKey(directory);
            if (key != null) icon = mappings.get(key);
        }
        if (icon == null) icon = mappings.get("folder." + name);
        return icon != null ? icon : mappings.get("folder");
    }

    private Icon fileIcon(VirtualFile file, String name, Project project) {
        if (NativeIconPolicy.usesFileIcon(file, name)) return null;

        Icon icon = mappings.contextual(file, "file.", name);
        if (icon == null) icon = mappings.get("file." + name);
        if (icon == null) icon = mappings.contextualExtension(file, name);
        for (ContextIconRule rule : contextRules) {
            if (icon != null) break;
            String key = rule.fileIconKey(file, name, project);
            if (key != null) icon = mappings.get(key);
        }
        return icon != null ? icon : mappings.extension(name);
    }
}
