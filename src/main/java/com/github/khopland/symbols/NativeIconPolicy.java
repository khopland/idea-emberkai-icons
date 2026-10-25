package com.github.khopland.symbols;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Arrays;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

/** Keeps IntelliJ's type icons, root markings, package icons, and badges. */
final class NativeIconPolicy {
    private static final ResourceBundle CONFIG = ResourceBundle.getBundle(
            "emberkai.native", Locale.ROOT, SymbolsIconProvider.class.getClassLoader());
    private static final Set<String> SOURCE_EXTENSIONS = names("file.extensions");
    private static final Set<String> TOOL_SCRIPT_SUFFIXES = names("file.toolScriptSuffixes");
    private static final Set<String> FOLDER_NAMES = names("folder.names");
    private static final Set<String> FOLDER_TREES = names("folder.trees");

    private NativeIconPolicy() {}

    static boolean isSource(VirtualFile file) {
        String extension = file.getExtension();
        return extension != null && SOURCE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    static boolean usesFileIcon(VirtualFile file, String name) {
        return isSource(file) && TOOL_SCRIPT_SUFFIXES.stream().noneMatch(name::endsWith);
    }

    static boolean usesFolderIcon(VirtualFile directory, String name, Project project) {
        if (FOLDER_NAMES.contains(name)) return true;
        for (VirtualFile parent = directory.getParent(); parent != null; parent = parent.getParent()) {
            if (FOLDER_TREES.contains(parent.getName().toLowerCase(Locale.ROOT))) return true;
        }
        if (project == null || project.isDisposed()) return false;
        if (directory.getPath().equals(project.getBasePath())) return true;
        // Root-model queries work during indexing and honor changes to root markings.
        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        return index.isInSourceContent(directory) || index.isInLibrary(directory)
                || index.isExcluded(directory) || directory.equals(index.getContentRootForFile(directory));
    }

    private static Set<String> names(String key) {
        return Arrays.stream(CONFIG.getString(key).split(","))
                .map(String::strip).map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
}
