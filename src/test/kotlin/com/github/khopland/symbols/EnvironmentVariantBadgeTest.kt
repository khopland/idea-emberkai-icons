package com.github.khopland.symbols

import javax.swing.Icon

internal class EnvironmentVariantBadgeTest : IconProviderTestCase() {
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

    fun testKnownConfigurationNamesReceiveEnvironmentBadges() {
        val compose = myFixture.addFileToProject("docker-compose.production.yaml", "services: {}")
        assertEnvironmentBadge(
            "symbols/icons/files/docker-pink.svg", EnvironmentVariant.PRODUCTION,
            provider.getIcon(compose, 0),
        )

        val values = myFixture.addFileToProject("values-staging.yml", "replicas: 2")
        assertEnvironmentBadge(
            "symbols/icons/files/yaml.svg", EnvironmentVariant.STAGING,
            provider.getIcon(values, 0),
        )

        val spring = myFixture.addFileToProject("application-test.yaml", "server: {}")
        assertEnvironmentBadge(
            "symbols/icons/files/yaml.svg", EnvironmentVariant.TEST,
            provider.getIcon(spring, 0),
        )

        val dotenv = myFixture.addFileToProject(".env.local", "PORT=8080")
        val dotenvBadge = provider.getIcon(dotenv, 0) as? EnvironmentVariantBadgeIcon
        assertNotNull("The .env base icon receives the local badge", dotenvBadge)
        assertEquals(EnvironmentVariant.LOCAL, dotenvBadge!!.variant)
        assertResourceIcon("symbols/icons/files/gear.svg", dotenvBadge.baseIcon)
    }

    fun testUnknownEnvironmentNamesKeepTheirBaseIcons() {
        val spring = myFixture.addFileToProject("application-qa.yml", "server: {}")
        assertYamlIcon(provider.getIcon(spring, 0))

        val values = myFixture.addFileToProject("values-preview.yaml", "replicas: 2")
        assertYamlIcon(provider.getIcon(values, 0))

        val compose = myFixture.addFileToProject("docker-compose.integration.yml", "services: {}")
        assertYamlIcon(provider.getIcon(compose, 0))
    }

    fun testEnvironmentBadgeCanBeDisabledWithoutChangingTheBaseIcon() {
        val file = myFixture.addFileToProject("docker-compose.prod.yml", "services: {}")
        val badge = provider.getIcon(file, 0) as? EnvironmentVariantBadgeIcon
        assertNotNull(badge)
        assertResourceIcon("symbols/icons/files/docker-pink.svg", badge!!.baseIcon)

        settings.setEnabled(IconRuleCategory.ENVIRONMENT_VARIANTS, false)
        assertResourceIcon("symbols/icons/files/docker-pink.svg", provider.getIcon(file, 0))
    }

    private fun assertYamlIcon(icon: Icon?) {
        assertNotNull(icon)
        assertFalse("Unknown environment names should not be decorated", icon is EnvironmentVariantBadgeIcon)
        assertResourceIcon("symbols/icons/files/yaml.svg", icon)
    }
}
