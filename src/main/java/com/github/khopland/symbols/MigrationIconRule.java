package com.github.khopland.symbols;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Locale;
import java.util.regex.Pattern;

/** Recognizes Flyway's versioned/repeatable SQL names, including custom locations. */
final class MigrationIconRule implements ContextIconRule {
    private static final Pattern SQL_MIGRATION = Pattern.compile("(?:v\\d+(?:[._]\\d+)*|r)__.+\\.sql");

    @Override
    public String fileIconKey(VirtualFile file, String name, Project project) {
        return SQL_MIGRATION.matcher(name).matches() ? "context.migration" : null;
    }

    @Override
    public String folderIconKey(VirtualFile directory) {
        String name = directory.getName().toLowerCase(Locale.ROOT);
        return name.equals("migration") || name.equals("migrations") ? "context.migration-folder" : null;
    }
}
