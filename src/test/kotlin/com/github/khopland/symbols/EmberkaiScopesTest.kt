package com.github.khopland.symbols

import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.EmberkaiScope
import com.github.khopland.symbols.settings.EmberkaiScopesProvider
import com.github.khopland.symbols.settings.setEnabled
import com.intellij.psi.search.scope.packageSet.CustomScopesProvider
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.PackageSetBase

internal class EmberkaiScopesTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null

    override fun setUp() {
        super.setUp()
        settings = EmberkaiIconsSettings.getInstance()
        savedState = settings.state
    }

    override fun tearDown() {
        savedState?.let(settings::loadState)
        super.tearDown()
    }

    fun testProviderIsRegisteredWithEveryScope() {
        val names = CustomScopesProvider.CUSTOM_SCOPES_PROVIDER.getExtensions(project)
            .flatMap { it.customScopes }
            .map(NamedScope::getScopeId)
        for (scope in EmberkaiScope.entries) {
            assertTrue(scope.scopeName, scope.scopeName in names)
        }
    }

    fun testFilesBelongToTheScopeOfTheirRecognizingRule() {
        assertMembers(EmberkaiScope.MIGRATIONS, "db/migration/V1__init.sql", "flyway.conf", "db.changelog-master.yaml")
        assertMembers(EmberkaiScope.CI_PIPELINES, ".github/workflows/build.yml", "azure-pipelines.yml", "Jenkinsfile")
        assertMembers(EmberkaiScope.INFRASTRUCTURE, "cdk.json", "infra/main.tf", "ansible.cfg")
        assertMembers(EmberkaiScope.API_CONTRACTS, "contracts/openapi.yaml")
        assertMembers(EmberkaiScope.KUBERNETES, "kustomization.yaml", "chart/Chart.yaml")
        assertMembers(EmberkaiScope.PRODUCTION_CONFIG, "docker-compose.prod.yml", ".env.production")

        myFixture.addFileToProject("k8s/deployment.yaml", "apiVersion: apps/v1\nkind: Deployment\n")
        assertTrue(contains(EmberkaiScope.KUBERNETES, "k8s/deployment.yaml"))
        assertFalse(contains(EmberkaiScope.API_CONTRACTS, "k8s/deployment.yaml"))

        myFixture.addFileToProject("service/build.gradle.kts", "plugins { id(\"org.springframework.boot\") }")
        myFixture.addFileToProject("service/src/main/resources/application.yml", "server: {}")
        assertTrue(contains(EmberkaiScope.SPRING_CONFIGURATION, "service/src/main/resources/application.yml"))
    }

    fun testUnrelatedFilesAndFoldersAreOutsideEveryScope() {
        myFixture.addFileToProject("queries/find_users.sql", "select 1")
        myFixture.addFileToProject("application-qa.yml", "server: {}")
        myFixture.tempDirFixture.findOrCreateDir("db/migrations")
        for (path in arrayOf("queries/find_users.sql", "application-qa.yml", "db/migrations")) {
            for (scope in EmberkaiScope.entries) {
                assertFalse("$path in ${scope.scopeName}", contains(scope, path))
            }
        }
    }

    fun testScopesIgnoreIconCategoryToggles() {
        myFixture.addFileToProject("R__refresh_views.sql", "select 1")
        settings.setEnabled(IconRuleCategory.MIGRATION, false)
        settings.setEnabled(IconRuleCategory.ENVIRONMENT_VARIANTS, false)
        myFixture.addFileToProject("values-prod.yaml", "replicas: 3")
        assertTrue(contains(EmberkaiScope.MIGRATIONS, "R__refresh_views.sql"))
        assertTrue(contains(EmberkaiScope.PRODUCTION_CONFIG, "values-prod.yaml"))
    }

    private fun assertMembers(scope: EmberkaiScope, vararg paths: String) {
        for (path in paths) {
            myFixture.addFileToProject(path, "data")
            assertTrue("$path in ${scope.scopeName}", contains(scope, path))
        }
    }

    private fun contains(scope: EmberkaiScope, path: String): Boolean {
        val file = myFixture.findFileInTempDir(path)
        assertNotNull(path, file)
        val named = EmberkaiScopesProvider().customScopes.single { it.scopeId == scope.scopeName }
        return (named.value as PackageSetBase).contains(file!!, project, null)
    }
}
