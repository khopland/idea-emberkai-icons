package com.github.khopland.symbols;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.jps.model.java.JavaResourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;

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
            return source == null ? Role.NATIVE : sourceRole(source.getJpsElement(), directory.equals(sourceRoot));
        }
        return directory.equals(index.getContentRootForFile(directory)) ? Role.CONTENT_ROOT : null;
    }

    static Role sourceRole(JpsModuleSourceRoot source, boolean root) {
        // Java and Kotlin's JVM/Multiplatform root types share these public
        // property contracts, so no Kotlin-plugin dependency is necessary.
        var properties = source.getProperties();
        boolean test = source.getRootType().isForTests();
        if (properties instanceof JavaSourceRootProperties java) {
            if (java.isForGeneratedSources()) return Role.NATIVE;
            if (root) return test ? Role.TEST_SOURCE : Role.SOURCE;
            return test ? Role.TEST_PACKAGE : Role.PACKAGE;
        }
        if (properties instanceof JavaResourceRootProperties resources) {
            if (resources.isForGeneratedSources()) return Role.NATIVE;
            if (!root) return null;
            return test ? Role.TEST_RESOURCES : Role.RESOURCES;
        }
        // Other language plugins retain ownership of their own root types.
        return Role.NATIVE;
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

}
