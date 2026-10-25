package com.github.khopland.symbols;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.regex.Pattern;

/** Detects Spring Boot configuration using cached declarations in nearby build files. */
final class SpringBootIconRule implements ContextIconRule {
    private static final Pattern CONFIG_FILE = Pattern.compile("application(?:-.+)?\\.(?:yaml|yml|properties)");
    private static final String[] BUILD_FILES = {"pom.xml", "build.gradle", "build.gradle.kts",
            "settings.gradle", "settings.gradle.kts"};
    private static final Pattern BUILD_COMMENTS = Pattern.compile(
            "(?s:<!--.*?-->|/\\*.*?\\*/)|(?m:^[\\t ]*(?://|#)[^\\r\\n]*$)");
    private static final Pattern SPRING_BOOT = Pattern.compile(
            "[\"']org\\.springframework\\.boot(?::[^\"']*)?[\"']"
                    + "|<groupId>\\s*org\\.springframework\\.boot\\s*</groupId>");
    private static final Key<FileContentCache.Inspection<Boolean>> BOOT_MARKER = Key.create("emberkai.springBootMarker");

    @Override
    public String fileIconKey(VirtualFile file, String name, Project project) {
        return CONFIG_FILE.matcher(name).matches() && isSpringBootProject(file, project) ? "context.spring" : null;
    }

    private static boolean isSpringBootProject(VirtualFile file, Project project) {
        for (VirtualFile dir = file.getParent(); dir != null; dir = dir.getParent()) {
            for (String name : BUILD_FILES) {
                if (hasSpringBootDeclaration(dir.findChild(name))) return true;
            }
            VirtualFile gradle = dir.findChild("gradle");
            if (gradle != null && gradle.isDirectory()) {
                for (VirtualFile catalog : gradle.getChildren()) {
                    if (catalog.getName().endsWith(".versions.toml") && hasSpringBootDeclaration(catalog)) return true;
                }
            }
            if (project != null && dir.getPath().equals(project.getBasePath())) break;
        }
        return false;
    }

    private static boolean hasSpringBootDeclaration(VirtualFile file) {
        return FileContentCache.inspect(file, BOOT_MARKER, false,
                text -> SPRING_BOOT.matcher(BUILD_COMMENTS.matcher(text).replaceAll("")).find());
    }
}
