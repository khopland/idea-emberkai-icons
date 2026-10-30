package com.github.khopland.symbols

import com.github.khopland.symbols.badges.HelmDependency
import com.github.khopland.symbols.badges.HelmLockDrift
import com.github.khopland.symbols.badges.HelmVersionRange
import com.github.khopland.symbols.badges.LockDriftBadgeIcon
import com.github.khopland.symbols.core.IconDescriptions
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.setEnabled

internal class HelmLockDriftTest : IconProviderTestCase() {
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

    fun testVersionRangesFollowHelm() {
        val cases = listOf(
            Triple("12.1.2", "12.1.2", true),
            Triple("12.1.2", "12.1.3", false),
            Triple("=12.1.2", "12.1.2", true),
            Triple("12.x", "12.9.0", true),
            Triple("12.x", "13.0.0", false),
            Triple("12.1.*", "12.1.7", true),
            Triple("12", "12.4.0", true),
            Triple("*", "99.0.0", true),
            Triple("", "1.0.0", true),
            Triple("^12.1.0", "12.9.9", true),
            Triple("^12.1.0", "13.0.0", false),
            Triple("^12.1.0", "12.0.9", false),
            Triple("^0.4.1", "0.4.9", true),
            Triple("^0.4.1", "0.5.0", false),
            Triple("~1.2.3", "1.2.9", true),
            Triple("~1.2.3", "1.3.0", false),
            Triple("~1", "1.9.0", true),
            Triple(">=1.2 <2.0.0", "1.9.0", true),
            Triple(">=1.2, <2.0.0", "2.0.0", false),
            Triple(">= 1.2", "1.2.0", true),
            Triple("<=1.2.x", "1.2.9", true),
            Triple("<=1.2.3", "1.2.4", false),
            Triple("!=1.2.3", "1.2.3", false),
            Triple("1.2 - 1.4.5", "1.4.5", true),
            Triple("1.2 - 1.4.5", "1.4.6", false),
            Triple("1.2 - 1.4", "1.4.9", true),
            Triple("^1.0.0 || ^2.0.0", "2.3.0", true),
            Triple("^1.0.0 || ^2.0.0", "3.0.0", false),
            Triple("v1.2.3", "1.2.3", true),
            Triple("1.2.3", "v1.2.3", true),
        )
        for ((range, version, expected) in cases) {
            assertEquals("$version in '$range'", expected, HelmVersionRange.satisfies(range, version))
        }
        assertNull("Prerelease versions are not judged", HelmVersionRange.satisfies("^1.0.0", "1.2.0-rc.1"))
        assertNull("Prerelease ranges are not judged", HelmVersionRange.satisfies("^1.0.0-beta", "1.2.0"))
        assertNull(HelmVersionRange.satisfies("latest", "1.2.0"))
        assertNull(HelmVersionRange.satisfies("^1.0.0", "main"))
        assertNull("Unknown alternatives keep the answer open", HelmVersionRange.satisfies("^1.0.0 || latest", "2.0.0"))
        assertEquals(true, HelmVersionRange.satisfies("^1.0.0 || latest", "1.5.0"))
    }

    fun testComparisonNamesEveryProblem() {
        val bitnami = "https://charts.bitnami.com/bitnami"
        val declared = listOf(
            HelmDependency("postgresql", bitnami, "^13.0.0"),
            HelmDependency("redis", bitnami, "18.x"),
            HelmDependency("common", "oci://registry.example.com/charts", "1.x"),
            HelmDependency("kafka", bitnami, "26.x"),
        )
        val locked = listOf(
            HelmDependency("postgresql", "$bitnami/", "12.12.10"),
            HelmDependency("redis", bitnami, "18.6.1"),
            HelmDependency("common", "https://old.example.com/charts", "1.2.0"),
            HelmDependency("mongodb", bitnami, "14.4.0"),
        )
        assertEquals(
            listOf(
                "postgresql is locked at 12.12.10, outside ^13.0.0",
                "common moved to oci://registry.example.com/charts",
                "kafka is not locked",
                "mongodb is locked but no longer declared",
            ),
            HelmLockDrift.compare(declared, locked),
        )
        assertEquals(emptyList<String>(), HelmLockDrift.compare(declared.subList(1, 2), locked.subList(1, 2)))
    }

    fun testRepositoryAliasesAndUnreadableRangesStayQuiet() {
        val declared = listOf(
            HelmDependency("postgresql", "@bitnami", "^13.0.0"),
            HelmDependency("redis", "https://charts.bitnami.com/bitnami", "latest"),
            HelmDependency("local", "file://../local", ""),
        )
        val locked = listOf(
            HelmDependency("postgresql", "https://charts.bitnami.com/bitnami", "13.2.0"),
            HelmDependency("redis", "https://charts.bitnami.com/bitnami", "18.6.1"),
            HelmDependency("local", "file://../local", "0.1.0"),
        )
        assertEquals(emptyList<String>(), HelmLockDrift.compare(declared, locked))
    }

    fun testAliasedCopiesOfOneChartEachNeedALockEntry() {
        val repo = "https://charts.example.com"
        val declared = listOf(HelmDependency("redis", repo, "18.x"), HelmDependency("redis", repo, "18.x"))
        assertEquals(listOf("redis is not locked"), HelmLockDrift.compare(declared, listOf(HelmDependency("redis", repo, "18.6.1"))))
    }

    fun testDependencyListsAreReadFromYaml() {
        assertEquals(
            listOf(HelmDependency("postgresql", "https://charts.bitnami.com/bitnami", "12.1.2")),
            HelmLockDrift.dependencies(
                "apiVersion: v2\nname: orders\ndependencies:\n" +
                    "  - name: postgresql\n    version: \"12.1.2\"\n    repository: https://charts.bitnami.com/bitnami\n" +
                    "    condition: postgresql.enabled\n    tags: [db]\n  - version: 1.0.0\n",
            ),
        )
        assertEquals(emptyList<HelmDependency>(), HelmLockDrift.dependencies("apiVersion: v2\nname: orders\n"))
        assertEquals(emptyList<HelmDependency>(), HelmLockDrift.dependencies(""))
        assertNull(HelmLockDrift.dependencies("dependencies: [unclosed"))
        assertNull(HelmLockDrift.dependencies("dependencies: postgres"))
        assertNull(HelmLockDrift.dependencies("- just\n- a list\n"))
    }

    fun testStaleLockIsBadgedAndExplained() {
        myFixture.addFileToProject("charts/orders/Chart.yaml", CHART)
        val lock = myFixture.addFileToProject(
            "charts/orders/Chart.lock",
            "dependencies:\n- name: postgresql\n  repository: $REPO\n  version: 12.12.10\n" +
                "digest: sha256:abc\ngenerated: \"2026-10-01T00:00:00Z\"\n",
        )

        val badge = provider.getIcon(lock, 0) as? LockDriftBadgeIcon
        assertNotNull(badge)
        assertResourceIcon("emberkai/icons/helm.svg", badge!!.baseIcon)

        val resolution = IconResolver.resolveWithDetails(lock.virtualFile, project)
        assertEquals(listOf("postgresql is locked at 12.12.10, outside ^13.0.0", "redis is not locked"), resolution.lockDrift)
        assertTrue(resolution.detail, "Run helm dependency update" in resolution.detail)
        assertEquals("Helm chart lock · out of date with Chart.yaml", IconDescriptions.describe(lock.virtualFile, resolution, project))

        settings.setEnabled(IconRuleCategory.LOCKFILE_DRIFT, false)
        assertFalse(provider.getIcon(lock, 0) is LockDriftBadgeIcon)
    }

    fun testMatchingLockAndLooseLockFilesKeepTheirIcons() {
        myFixture.addFileToProject("charts/orders/Chart.yaml", CHART)
        val lock = myFixture.addFileToProject(
            "charts/orders/Chart.lock",
            "dependencies:\n- name: postgresql\n  repository: $REPO\n  version: 13.2.0\n" +
                "- name: redis\n  repository: $REPO\n  version: 18.6.1\ndigest: sha256:abc\n",
        )
        assertFalse(provider.getIcon(lock, 0) is LockDriftBadgeIcon)
        assertTrue(IconResolver.resolveWithDetails(lock.virtualFile, project).lockDrift.isEmpty())

        val loose = myFixture.addFileToProject("misc/Chart.lock", "dependencies:\n- name: redis\n  version: 1.0.0\n")
        assertFalse("No Chart.yaml beside it", provider.getIcon(loose, 0) is LockDriftBadgeIcon)
    }

    private companion object {
        const val REPO = "https://charts.bitnami.com/bitnami"
        const val CHART = "apiVersion: v2\nname: orders\nversion: 1.0.0\ndependencies:\n" +
            "  - name: postgresql\n    version: ^13.0.0\n    repository: $REPO\n" +
            "  - name: redis\n    version: 18.x\n    repository: $REPO\n"
    }
}
