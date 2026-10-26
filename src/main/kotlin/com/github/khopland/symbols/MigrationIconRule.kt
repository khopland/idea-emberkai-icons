package com.github.khopland.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import java.util.regex.Pattern

/** Recognizes Flyway's versioned/repeatable SQL names, including custom locations. */
internal class MigrationIconRule : ContextIconRule {
    override val category = IconRuleCategory.MIGRATION

    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? =
        if (SQL_MIGRATION.matcher(name).matches()) "context.migration" else null

    override fun folderIconKey(directory: VirtualFile): String? {
        val name = directory.name.lowercase(Locale.ROOT)
        return if (name == "migration" || name == "migrations") "context.migration-folder" else null
    }

    private companion object {
        val SQL_MIGRATION: Pattern = Pattern.compile("(?:v\\d+(?:[._]\\d+)*|r)__.+\\.sql")
    }
}
