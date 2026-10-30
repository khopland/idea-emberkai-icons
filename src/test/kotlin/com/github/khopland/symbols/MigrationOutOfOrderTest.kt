package com.github.khopland.symbols

import com.github.khopland.symbols.badges.BaseBranchMigrations
import com.github.khopland.symbols.badges.FlywayMigrations
import com.github.khopland.symbols.badges.GitMigrationBranchBase
import com.github.khopland.symbols.badges.MigrationBranchBase
import com.github.khopland.symbols.badges.MigrationConflictBadgeIcon
import com.github.khopland.symbols.badges.OutOfOrderMigration
import com.github.khopland.symbols.core.IconDescriptions
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.setEnabled
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.registerOrReplaceServiceInstance

internal class MigrationOutOfOrderTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null
    private val base = FakeMigrationBranchBase()

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

    fun testOnlyMigrationsAddedAtOrBelowTheBaseLatestAreOutOfOrder() {
        val main = BaseBranchMigrations("origin/main", setOf("V1__init.sql", "V4__audit.sql"), "4")
        assertEquals(OutOfOrderMigration("3", "4", "origin/main"), FlywayMigrations.outOfOrder("V3__orders.sql", "3", main))
        assertNotNull("Same version as the latest", FlywayMigrations.outOfOrder("V4__orders.sql", "4", main))
        assertNotNull("1.10 sorts below 4", FlywayMigrations.outOfOrder("V1.10__x.sql", "1.10", main))
        assertNull("Above the latest", FlywayMigrations.outOfOrder("V5__orders.sql", "5", main))
        assertNull("Already on the default branch", FlywayMigrations.outOfOrder("V1__init.sql", "1", main))
        assertNull(
            "The default branch has no versioned migrations here",
            FlywayMigrations.outOfOrder("V1__init.sql", "1", BaseBranchMigrations("main", emptySet(), null)),
        )
        assertEquals("10", FlywayMigrations.latestVersion(listOf("V9__a.sql", "V10__b.sql", "R__views.sql", "README.md")))
    }

    fun testGitServiceIsRegistered() {
        assertTrue(MigrationBranchBase.getInstance(project) is GitMigrationBranchBase)
    }

    fun testMigrationAddedBelowTheDefaultBranchIsBadgedAndExplained() {
        project.registerOrReplaceServiceInstance(MigrationBranchBase::class.java, base, testRootDisposable)
        myFixture.addFileToProject("db/migration/V1__init.sql", "select 1")
        val orders = myFixture.addFileToProject("db/migration/V3__orders.sql", "select 1")
        val newest = myFixture.addFileToProject("db/migration/V5__newest.sql", "select 1")
        val folder = orders.virtualFile.parent
        base.folders[folder] = BaseBranchMigrations("origin/main", setOf("V1__init.sql", "V4__audit.sql"), "4")

        val badge = provider.getIcon(orders, 0) as? MigrationConflictBadgeIcon
        assertNotNull(badge)
        assertResourceIcon("emberkai/icons/migration.svg", badge!!.baseIcon)
        assertFalse(provider.getIcon(newest, 0) is MigrationConflictBadgeIcon)

        val resolution = IconResolver.resolveWithDetails(orders.virtualFile, project)
        assertEquals(OutOfOrderMigration("3", "4", "origin/main"), resolution.migrationOutOfOrder)
        assertNull(resolution.migrationConflict)
        assertTrue(resolution.detail, "origin/main already has migrations up to V4" in resolution.detail)
        assertEquals(
            "Flyway versioned migration · out of order: origin/main is at V4",
            IconDescriptions.describe(orders.virtualFile, resolution, project),
        )

        assertTrue(provider.getIcon(folder, 0, project) is MigrationConflictBadgeIcon)
        val folderResolution = IconResolver.resolveWithDetails(folder, project)
        assertTrue(folderResolution.detail, "the latest on origin/main: V3__orders.sql" in folderResolution.detail)
        assertEquals(
            "Database migrations folder · 3 versioned · latest V5 · 1 out of order",
            IconDescriptions.describe(folder, folderResolution, project),
        )
    }

    fun testDuplicateVersionTakesPrecedenceOverOutOfOrder() {
        project.registerOrReplaceServiceInstance(MigrationBranchBase::class.java, base, testRootDisposable)
        val first = myFixture.addFileToProject("db/migration/V2__a.sql", "select 1")
        myFixture.addFileToProject("db/migration/V2__b.sql", "select 1")
        base.folders[first.virtualFile.parent] = BaseBranchMigrations("origin/main", setOf("V3__c.sql"), "3")

        val resolution = IconResolver.resolveWithDetails(first.virtualFile, project)
        assertEquals("2", resolution.migrationConflict)
        assertNull(resolution.migrationOutOfOrder)
    }

    fun testNoBadgeUntilTheDefaultBranchIsKnownOrWhenDisabled() {
        project.registerOrReplaceServiceInstance(MigrationBranchBase::class.java, base, testRootDisposable)
        val orders = myFixture.addFileToProject("db/migration/V3__orders.sql", "select 1")
        assertFalse("Lookup still running", provider.getIcon(orders, 0) is MigrationConflictBadgeIcon)
        assertTrue(orders.virtualFile.parent in base.requested)

        base.folders[orders.virtualFile.parent] = BaseBranchMigrations("origin/main", setOf("V4__audit.sql"), "4")
        assertTrue(provider.getIcon(orders, 0) is MigrationConflictBadgeIcon)

        settings.setEnabled(IconRuleCategory.MIGRATION_CONFLICTS, false)
        assertFalse(provider.getIcon(orders, 0) is MigrationConflictBadgeIcon)
        assertFalse(provider.getIcon(orders.virtualFile.parent, 0, project) is MigrationConflictBadgeIcon)
    }

    private class FakeMigrationBranchBase : MigrationBranchBase {
        val folders = HashMap<VirtualFile, BaseBranchMigrations>()
        val requested = HashSet<VirtualFile>()

        override fun migrationsOnBase(directory: VirtualFile): BaseBranchMigrations? =
            folders[directory].also { if (it == null) requested += directory }
    }
}
