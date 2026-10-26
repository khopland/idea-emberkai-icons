package com.github.khopland.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/** Recognizes Liquibase changelogs using their common db.changelog filename prefix. */
internal class LiquibaseIconRule : ContextIconRule {
    override val category = IconRuleCategory.MIGRATION

    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? {
        val extension = file.extension?.lowercase(Locale.ROOT) ?: return null
        return if (name.startsWith("db.changelog") && extension in CHANGELOG_EXTENSIONS) {
            "context.migration"
        } else {
            null
        }
    }

    private companion object {
        val CHANGELOG_EXTENSIONS = setOf("xml", "yaml", "yml", "json", "sql")
    }
}
