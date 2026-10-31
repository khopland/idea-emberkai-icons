package com.github.khopland.symbols

import com.github.khopland.symbols.badges.DuplicateChangeSetFact
import com.github.khopland.symbols.badges.LiquibaseChangeSets
import com.github.khopland.symbols.badges.MigrationConflictBadgeIcon
import com.github.khopland.symbols.core.IconDescriptions
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.fact
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.setEnabled
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager

internal class LiquibaseDuplicateChangeSetTest : IconProviderTestCase() {
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

    fun testXmlIdentifiersIgnoreCommentsAndAttributeOrder() {
        val xml = """
            <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog">
                <changeSet id="1" author="alice"><createTable tableName="a"/></changeSet>
                <changeSet author='bob' id='1'/>
                <!-- <changeSet id="1" author="alice"/> -->
                <changeSet id="2" author="alice" runOnChange="true"></changeSet>
            </databaseChangeLog>
        """.trimIndent()
        assertEquals(listOf("1::alice", "1::bob", "2::alice"), LiquibaseChangeSets.identifiers(xml, "xml"))
        assertEquals(emptyList<String>(), LiquibaseChangeSets.identifiers("<beans><changeSet id=\"1\"/></beans>", "xml"))
    }

    fun testYamlJsonAndSqlIdentifiers() {
        val yaml = """
            databaseChangeLog:
              - changeSet:
                  id: 1
                  author: alice
                  changes: []
              - include:
                  file: other.yaml
              - changeSet:
                  id: 1
                  author: alice
        """.trimIndent()
        assertEquals(listOf("1::alice", "1::alice"), LiquibaseChangeSets.identifiers(yaml, "yaml"))

        val json = """{"databaseChangeLog": [{"changeSet": {"id": "7", "author": "carol"}}]}"""
        assertEquals(listOf("7::carol"), LiquibaseChangeSets.identifiers(json, "json"))

        val sql = """
            --liquibase formatted sql
            --changeset alice:1
            create table a (id int);
            -- changeset "Bob Smith":2 runOnChange:true
            create table b (id int);
            --changeset alice:1
            create table c (id int);
        """.trimIndent()
        assertEquals(listOf("1::alice", "2::Bob Smith", "1::alice"), LiquibaseChangeSets.identifiers(sql, "sql"))
        assertEquals("Plain SQL is not a changelog", emptyList<String>(), LiquibaseChangeSets.identifiers("--changeset alice:1", "sql"))
        assertEquals(listOf("1::alice"), LiquibaseChangeSets.duplicates(listOf("1::alice", "2::alice", "1::alice")))
    }

    fun testChangelogRepeatingAChangeSetIsBadgedAndExplained() {
        val changelog = myFixture.addFileToProject(
            "src/main/resources/db/changelog/db.changelog-master.yaml",
            "databaseChangeLog:\n  - changeSet:\n      id: 1\n      author: alice\n  - changeSet:\n      id: 1\n      author: alice\n",
        )
        val nested = myFixture.addFileToProject(
            "src/main/resources/db/changelog/changes/002-orders.xml",
            "<databaseChangeLog><changeSet id=\"2\" author=\"bob\"/><changeSet id=\"2\" author=\"bob\"/></databaseChangeLog>",
        )
        val fine = myFixture.addFileToProject(
            "src/main/resources/db/changelog/changes/003-index.xml",
            "<databaseChangeLog><changeSet id=\"3\" author=\"bob\"/><changeSet id=\"3\" author=\"carol\"/></databaseChangeLog>",
        )
        val elsewhere = myFixture.addFileToProject(
            "config/orders.xml",
            "<databaseChangeLog><changeSet id=\"2\" author=\"bob\"/><changeSet id=\"2\" author=\"bob\"/></databaseChangeLog>",
        )

        val badge = provider.getIcon(changelog, 0) as? MigrationConflictBadgeIcon
        assertNotNull(badge)
        assertResourceIcon("emberkai/icons/migration.svg", badge!!.baseIcon)
        assertTrue("Changelogs under changelog/ count", provider.getIcon(nested, 0) is MigrationConflictBadgeIcon)
        assertFalse("A different author is a different changeSet", provider.getIcon(fine, 0) is MigrationConflictBadgeIcon)
        assertFalse("Only changelog folders are read", provider.getIcon(elsewhere, 0) is MigrationConflictBadgeIcon)

        val resolution = IconResolver.resolveWithDetails(changelog.virtualFile, project)
        assertEquals(listOf("1::alice"), resolution.fact<DuplicateChangeSetFact>()?.identifiers)
        assertTrue(resolution.detail, "repeats changeSet 1::alice" in resolution.detail)
        assertTrue(
            IconDescriptions.describe(changelog.virtualFile, resolution)!!.endsWith("duplicate changeSet 1::alice"),
        )

        settings.setEnabled(IconRuleCategory.MIGRATION_CONFLICTS, false)
        assertFalse(provider.getIcon(changelog, 0) is MigrationConflictBadgeIcon)
    }

    fun testFixingTheChangelogClearsTheBadge() {
        val changelog = myFixture.addFileToProject(
            "db/changelog/db.changelog-1.sql",
            "--liquibase formatted sql\n--changeset alice:1\nselect 1;\n--changeset alice:1\nselect 2;\n",
        )
        assertTrue(provider.getIcon(changelog, 0) is MigrationConflictBadgeIcon)

        val document = FileDocumentManager.getInstance().getDocument(changelog.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText("--liquibase formatted sql\n--changeset alice:1\nselect 1;\n--changeset alice:2\nselect 2;\n")
        }
        assertFalse("Unsaved edits count", provider.getIcon(changelog, 0) is MigrationConflictBadgeIcon)
    }
}
