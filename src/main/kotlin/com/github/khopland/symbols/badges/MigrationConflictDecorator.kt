package com.github.khopland.symbols.badges

import com.github.khopland.symbols.core.IconDecorator
import com.github.khopland.symbols.core.IconFact
import com.github.khopland.symbols.core.IconResolution
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.decorated
import com.github.khopland.symbols.core.iconOrFileTypeIcon
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.isEnabled
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** The Flyway version this migration shares with a sibling. */
internal data class MigrationConflictFact(val version: String) : IconFact {
    override val label: String get() = "duplicate version V$version"
    override val tooltipOrder: Int get() = 40
}

/** The changeSet identifiers this Liquibase changelog repeats, as `id::author`. */
internal data class DuplicateChangeSetFact(val identifiers: List<String>) : IconFact {
    override val label: String
        get() = if (identifiers.size == 1) "duplicate changeSet ${identifiers.single()}" else "${identifiers.size} duplicate changeSets"
    override val tooltipOrder: Int get() = 45
}

/** This branch's migration whose version is not after the default branch's latest. */
internal data class OutOfOrderMigrationFact(val migration: OutOfOrderMigration) : IconFact {
    override val label: String get() = "out of order: ${migration.baseRef} is at V${migration.baseLatest}"
    override val tooltipOrder: Int get() = 50
}

/** Migration folders summarize their Flyway versions, such as "12 versioned · latest V12 · 1 version conflict". */
internal data class MigrationFolderSummaryFact(val summary: MigrationFolderSummary, val outOfOrder: Int) : IconFact {
    override val label: String
        get() = listOfNotNull(
            "${summary.versionedCount} versioned",
            summary.latestVersion?.let { "latest V$it" },
            when (val conflicts = summary.conflicts.size) {
                0 -> null
                1 -> "1 version conflict"
                else -> "$conflicts version conflicts"
            },
            when (outOfOrder) {
                0 -> null
                1 -> "1 out of order"
                else -> "$outOfOrder out of order"
            },
        ).joinToString(" · ")
    override val tooltipOrder: Int get() = 80
}

/**
 * Warns about a Flyway version that a sibling migration also uses, which Flyway refuses to run,
 * about a migration this branch added below the default branch's latest version, which Flyway
 * rejects as out of order once that branch's migrations have run, and about a Liquibase changelog
 * that repeats a changeSet identifier, which Liquibase refuses to validate.
 */
internal class MigrationConflictDecorator : IconDecorator {
    override fun decorateFile(resolution: IconResolution, file: VirtualFile, project: Project?): IconResolution {
        val settings = EmberkaiIconsSettings.getInstance()
        if (!settings.isEnabled(IconRuleCategory.MIGRATION_CONFLICTS)) return resolution
        val conflict = FlywayMigrations.conflict(file)
        val outOfOrder = if (conflict == null) FlywayMigrations.outOfOrder(file, project) else null
        val duplicates = if (conflict == null && outOfOrder == null) LiquibaseChangeSets.duplicates(file) else emptyList()
        if (conflict == null && outOfOrder == null && duplicates.isEmpty()) return resolution
        val baseIcon = resolution.iconOrFileTypeIcon(file) ?: return resolution
        val icon = MigrationConflictBadgeIcon(baseIcon, settings.highContrastFolderIcons || settings.monochromeFolderIcons)
        if (duplicates.isNotEmpty()) {
            return resolution.decorated(
                icon,
                DuplicateChangeSetFact(duplicates),
                "A warning badge marks a Liquibase changelog that repeats changeSet " +
                    "${duplicates.joinToString()} (id::author). Liquibase fails validation until each " +
                    "changeSet in the file has its own id and author.",
            )
        }
        if (conflict != null) {
            val (version, others) = conflict
            return resolution.decorated(
                icon,
                MigrationConflictFact(version),
                "A warning badge marks a duplicate Flyway version: V$version is also used by " +
                    "${others.joinToString()}. Flyway fails validation until one of them is renumbered.",
            )
        }
        val migration = outOfOrder!!
        return resolution.decorated(
            icon,
            OutOfOrderMigrationFact(migration),
            "A warning badge marks a migration this branch added at V${migration.version}, " +
                "but ${migration.baseRef} already has migrations up to V${migration.baseLatest} in this folder. " +
                "Once those have run, Flyway rejects it as out of order; renumber it above V${migration.baseLatest}.",
        )
    }

    /** A folder with duplicate or out-of-order Flyway versions carries the warning so it is visible while collapsed. */
    override fun decorateFolder(resolution: IconResolution, directory: VirtualFile, project: Project?): IconResolution {
        val settings = EmberkaiIconsSettings.getInstance()
        // The summary describes migration folders whether or not the warning badge is enabled.
        val summarized = resolution.mappingKey == "context.migration-folder"
        val badged = resolution.icon != null && settings.isEnabled(IconRuleCategory.MIGRATION_CONFLICTS)
        if (!summarized && !badged) return resolution
        val summary = FlywayMigrations.summary(directory) ?: return resolution
        val outOfOrder = FlywayMigrations.outOfOrderIn(directory, project)
        val described = if (summarized) {
            resolution.copy(facts = resolution.facts + MigrationFolderSummaryFact(summary, outOfOrder.size))
        } else {
            resolution
        }
        val icon = described.icon ?: return described
        if (!badged || (summary.conflicts.isEmpty() && outOfOrder.isEmpty())) return described
        val problems = listOfNotNull(
            summary.conflicts.takeIf { it.isNotEmpty() }
                ?.let { "duplicate Flyway versions in this folder: " + it.keys.joinToString { version -> "V$version" } },
            outOfOrder.takeIf { it.isNotEmpty() }?.let {
                "migrations this branch added at or below V${it.values.first().baseLatest}, the latest on " +
                    "${it.values.first().baseRef}: ${it.keys.sorted().joinToString()}"
            },
        )
        return described.copy(
            icon = MigrationConflictBadgeIcon(icon, settings.highContrastFolderIcons || settings.monochromeFolderIcons),
            detail = "${described.detail} A warning badge marks ${problems.joinToString("; and ")}.",
        )
    }
}
