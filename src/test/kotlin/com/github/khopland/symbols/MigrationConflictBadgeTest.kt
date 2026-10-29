package com.github.khopland.symbols

import com.github.khopland.symbols.badges.FlywayMigrations
import com.github.khopland.symbols.badges.MigrationConflictBadgeIcon
import com.github.khopland.symbols.core.IconDescriptions
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.setEnabled
import com.intellij.openapi.application.runWriteAction

internal class MigrationConflictBadgeTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null

    override fun setUp() {
        super.setUp()
        settings = EmberkaiIconsSettings.getInstance()
        savedState = settings.state
    }

    override fun tearDown() {
        try {
            savedState?.let(settings::loadState)
        } finally {
            super.tearDown()
        }
    }

    fun testVersionsAreNormalizedTheWayFlywayComparesThem() {
        assertEquals("1", FlywayMigrations.version("V1__init.sql"))
        assertEquals("1", FlywayMigrations.version("V01__init.sql"))
        assertEquals("1", FlywayMigrations.version("V1_0__init.sql"))
        assertEquals("1.2", FlywayMigrations.version("V1.2__add_index.sql"))
        assertEquals("1.2", FlywayMigrations.version("v1_2__add_index.SQL"))
        assertEquals("2026.10.29", FlywayMigrations.version("V2026.10.29__seed.sql"))
        assertNull("Repeatable migrations have no version", FlywayMigrations.version("R__views.sql"))
        assertNull(FlywayMigrations.version("V1_init.sql"))
        assertNull(FlywayMigrations.version("V1__init.java"))
    }

    fun testSiblingsSharingAVersionAreBadged() {
        val first = myFixture.addFileToProject("db/migration/V3__add_users.sql", "select 1")
        val second = myFixture.addFileToProject("db/migration/V3_0__add_orders.sql", "select 1")
        val fine = myFixture.addFileToProject("db/migration/V2__init.sql", "select 1")
        myFixture.addFileToProject("other/V2__init.sql", "select 1")

        for (file in listOf(first, second)) {
            val badge = provider.getIcon(file, 0) as? MigrationConflictBadgeIcon
            assertNotNull("${file.name} shares V3", badge)
            assertResourceIcon("emberkai/icons/migration.svg", badge!!.baseIcon)
        }
        assertFalse("Another folder is a separate location", provider.getIcon(fine, 0) is MigrationConflictBadgeIcon)

        val resolution = IconResolver.resolveWithDetails(first.virtualFile, project)
        assertEquals("3", resolution.migrationConflict)
        assertTrue(resolution.detail, "V3 is also used by V3_0__add_orders.sql" in resolution.detail)
        assertEquals(
            "Flyway versioned migration · duplicate version V3",
            IconDescriptions.describe(first.virtualFile, resolution),
        )
    }

    fun testMigrationFolderCarriesTheWarningAndASummary() {
        myFixture.addFileToProject("db/migration/V1__init.sql", "select 1")
        myFixture.addFileToProject("db/migration/V2__users.sql", "select 1")
        myFixture.addFileToProject("db/migration/V2__orders.sql", "select 1")
        myFixture.addFileToProject("db/migration/V10__index.sql", "select 1")
        myFixture.addFileToProject("db/migration/R__views.sql", "select 1")
        val folder = myFixture.findFileInTempDir("db/migration")!!

        assertTrue(provider.getIcon(folder, 0, project) is MigrationConflictBadgeIcon)
        assertEquals(
            "Database migrations folder · 4 versioned · latest V10 · 1 version conflict",
            IconDescriptions.describe(folder, IconResolver.resolveWithDetails(folder, project)),
        )
    }

    fun testRenamingAMigrationClearsTheConflict() {
        val first = myFixture.addFileToProject("db/migration/V4__a.sql", "select 1").virtualFile
        myFixture.addFileToProject("db/migration/V4__b.sql", "select 1")
        assertNotNull(FlywayMigrations.conflict(first))

        runWriteAction { first.rename(this, "V5__a.sql") }

        assertNull(FlywayMigrations.conflict(first))
        assertFalse(provider.getIcon(first.parent, 0, project) is MigrationConflictBadgeIcon)
        assertEquals(
            "Database migrations folder · 2 versioned · latest V5",
            IconDescriptions.describe(first.parent, IconResolver.resolveWithDetails(first.parent, project)),
        )
    }

    fun testConflictBadgeCanBeDisabled() {
        val file = myFixture.addFileToProject("db/migration/V7__a.sql", "select 1")
        myFixture.addFileToProject("db/migration/V7__b.sql", "select 1")
        settings.setEnabled(IconRuleCategory.MIGRATION_CONFLICTS, false)

        assertResourceIcon("emberkai/icons/migration.svg", provider.getIcon(file, 0))
        assertFalse(provider.getIcon(file.virtualFile.parent, 0, project) is MigrationConflictBadgeIcon)
    }
}
