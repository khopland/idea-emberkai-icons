package com.github.khopland.symbols;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.jps.model.java.JavaResourceRootProperties;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;

/** Applies IntelliJ's root and package roles to Symbols-style folder artwork. */
final class ProjectFolderIcons {
    enum Role {
        CONTENT_ROOT("folder.role.content-root"),
        SOURCE("folder.role.source"),
        TEST_SOURCE("folder.role.test-source"),
        RESOURCES("folder.role.resources"),
        TEST_RESOURCES("folder.role.test-resources"),
        PACKAGE("folder.role.package"),
        TEST_PACKAGE("folder.role.test-package"),
        NATIVE(null);

        final String iconKey;

        Role(String iconKey) {
            this.iconKey = iconKey;
        }
    }

    Role role(VirtualFile directory, Project project) {
        if (project == null || project.isDisposed()) return null;
        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        VirtualFile sourceRoot = index.getSourceRootForFile(directory);
        if (sourceRoot != null) {
            SourceFolder source = sourceFolder(directory, sourceRoot, index);
            if (source == null || isGenerated(source)) return Role.NATIVE;
            boolean root = directory.equals(sourceRoot);
            var type = source.getRootType();
            if (type.equals(JavaSourceRootType.SOURCE)) return root ? Role.SOURCE : Role.PACKAGE;
            if (type.equals(JavaSourceRootType.TEST_SOURCE)) return root ? Role.TEST_SOURCE : Role.TEST_PACKAGE;
            if (type.equals(JavaResourceRootType.RESOURCE)) return root ? Role.RESOURCES : null;
            if (type.equals(JavaResourceRootType.TEST_RESOURCE)) return root ? Role.TEST_RESOURCES : null;
            // Other language plugins retain ownership of their own root types.
            return Role.NATIVE;
        }
        return directory.equals(index.getContentRootForFile(directory)) ? Role.CONTENT_ROOT : null;
    }

    private static SourceFolder sourceFolder(VirtualFile directory, VirtualFile root, ProjectFileIndex index) {
        Module module = index.getModuleForFile(directory);
        if (module == null) return null;
        for (var entry : ModuleRootManager.getInstance(module).getContentEntries()) {
            for (SourceFolder source : entry.getSourceFolders()) {
                if (root.equals(source.getFile())) return source;
            }
        }
        return null;
    }

    private static boolean isGenerated(SourceFolder source) {
        var properties = source.getJpsElement().getProperties();
        return properties instanceof JavaSourceRootProperties java && java.isForGeneratedSources()
                || properties instanceof JavaResourceRootProperties resources && resources.isForGeneratedSources();
    }
}
