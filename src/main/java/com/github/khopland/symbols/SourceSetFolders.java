package com.github.khopland.symbols;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Locale;
import java.util.regex.Pattern;

/** Standard JVM and Multiplatform layout fallback, used only when no imported root role applies. */
final class SourceSetFolders {
    private static final Pattern SOURCE_SET = Pattern.compile("(?:[a-z0-9_]+)?(?:main|test)");

    ProjectFolderIcons.Role role(VirtualFile directory) {
        if (isSourceSet(directory)) return ProjectFolderIcons.Role.CONTENT_ROOT;
        for (VirtualFile root = directory; root != null; root = root.getParent()) {
            String name = root.getName().toLowerCase(Locale.ROOT);
            if (!name.equals("kotlin") && !name.equals("java") && !name.equals("resources")) continue;
            VirtualFile sourceSet = root.getParent();
            if (sourceSet == null || !isSourceSet(sourceSet)) continue;
            boolean test = sourceSet.getName().toLowerCase(Locale.ROOT).endsWith("test");
            if (name.equals("resources")) {
                if (!root.equals(directory)) return null;
                return test ? ProjectFolderIcons.Role.TEST_RESOURCES : ProjectFolderIcons.Role.RESOURCES;
            }
            if (root.equals(directory)) return test ? ProjectFolderIcons.Role.TEST_SOURCE : ProjectFolderIcons.Role.SOURCE;
            return test ? ProjectFolderIcons.Role.TEST_PACKAGE : ProjectFolderIcons.Role.PACKAGE;
        }
        return null;
    }

    private static boolean isSourceSet(VirtualFile directory) {
        VirtualFile parent = directory.getParent();
        return parent != null && parent.getName().equalsIgnoreCase("src")
                && SOURCE_SET.matcher(directory.getName().toLowerCase(Locale.ROOT)).matches();
    }
}
