package com.github.khopland.symbols

import com.github.khopland.symbols.badges.ConfigVariants
import com.github.khopland.symbols.badges.ConfigDriftBadgeIcon
import com.github.khopland.symbols.badges.ConfigDriftFact
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.fact
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.setEnabled

internal class ConfigDriftBadgeTest : IconProviderTestCase() {
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

    fun testOnlyKeysEveryOtherVariantDefinesCount() {
        val drift = ConfigVariants.compare(
            setOf("a", "b"),
            mapOf("prod" to setOf("a", "b", "c", "debug"), "staging" to setOf("a", "b", "c")),
        )
        assertEquals(mapOf("c" to listOf("prod", "staging")), drift?.missing)
        assertNull(ConfigVariants.compare(setOf("a"), mapOf("prod" to setOf("a"))))
        assertNull("Relaxed binding", ConfigVariants.compare(setOf("server.maxHttpHeaderSize"), mapOf("prod" to setOf("server.max-http-header-size"))))
    }

    fun testYamlKeysAreDottedLeavesAcrossDocuments() {
        val yaml = "spring:\n  datasource:\n    url: x\n    pool: {}\nfeatures: [a, b]\n---\nmanagement.port: 9000\n"
        assertEquals(
            setOf("spring.datasource.url", "spring.datasource.pool", "features", "management.port"),
            ConfigVariants.yamlKeys(yaml),
        )
        assertNull(ConfigVariants.yamlKeys("a: [unclosed"))
        assertEquals(setOf("a.b", "c"), ConfigVariants.propertiesKeys("a.b=1\n# c=0\nc: 2\n"))
    }

    fun testSpringProfileMissingAKeyIsBadgedAcrossFormats() {
        val prod = myFixture.addFileToProject(
            "src/main/resources/application-prod.yml",
            "spring:\n  datasource:\n    url: jdbc:prod\napp:\n  feature-flags:\n    checkout: true\n",
        )
        val staging = myFixture.addFileToProject(
            "src/main/resources/application-staging.properties",
            "spring.datasource.url=jdbc:staging\napp.featureFlags.checkout=true\nlogging.level.root=debug\n",
        )
        val qa = myFixture.addFileToProject(
            "src/main/resources/application-qa.yml",
            "spring.datasource.url: jdbc:qa\n",
        )
        val local = myFixture.addFileToProject("src/main/resources/application-local.yml", "other: 1\n")
        myFixture.addFileToProject("src/main/resources/application.yml", "server.port: 8080\n")

        assertFalse(provider.getIcon(prod, 0) is ConfigDriftBadgeIcon)
        assertFalse("logging.level.root is only in staging", provider.getIcon(staging, 0) is ConfigDriftBadgeIcon)
        assertTrue(provider.getIcon(qa, 0) is ConfigDriftBadgeIcon)
        assertFalse("Local variants take no part", provider.getIcon(local, 0) is ConfigDriftBadgeIcon)

        val resolution = IconResolver.resolveWithDetails(qa.virtualFile, project)
        val fact = resolution.fact<ConfigDriftFact>()
        assertEquals(listOf("app.feature-flags.checkout"), fact?.drift?.missing?.keys?.toList())
        assertEquals("missing app.feature-flags.checkout", fact?.label)
        assertTrue(
            resolution.detail,
            "app.feature-flags.checkout (in application-prod.yml, application-staging.properties)" in resolution.detail,
        )

        settings.setEnabled(IconRuleCategory.CONFIG_DRIFT, false)
        assertFalse(provider.getIcon(qa, 0) is ConfigDriftBadgeIcon)
    }

    fun testHelmValuesAndDotenvVariants() {
        val prod = myFixture.addFileToProject("chart/values-prod.yaml", "image:\n  tag: 1.0\nreplicas: 3\n")
        val dev = myFixture.addFileToProject("chart/values-dev.yaml", "image:\n  tag: dev\n")
        myFixture.addFileToProject("chart/values.yaml", "image:\n  repository: app\n")
        assertTrue(provider.getIcon(dev, 0) is ConfigDriftBadgeIcon)
        assertFalse(provider.getIcon(prod, 0) is ConfigDriftBadgeIcon)

        val production = myFixture.addFileToProject("web/.env.production", "API_URL=https://api\nexport SENTRY_DSN=x\n")
        val staging = myFixture.addFileToProject("web/.env.staging", "API_URL=https://staging\n")
        myFixture.addFileToProject("web/.env.local", "ONLY_HERE=1\n")
        assertEquals(listOf("SENTRY_DSN"), ConfigVariants.of(staging.virtualFile)?.missing?.keys?.toList())
        assertNull(ConfigVariants.of(production.virtualFile))
    }
}
