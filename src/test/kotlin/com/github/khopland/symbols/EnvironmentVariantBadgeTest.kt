package com.github.khopland.symbols

import com.github.khopland.symbols.badges.EnvironmentVariant
import com.github.khopland.symbols.badges.EnvironmentVariantBadgeIcon
import com.github.khopland.symbols.core.IconDescriptions
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.EmberkaiScope
import com.github.khopland.symbols.settings.setEnabled
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

    fun testEnvironmentFoldersAreRecognizedOnlyInsideEnvironmentContainers() {
        fun variant(path: String) = EnvironmentVariant.fromDirectory(myFixture.tempDirFixture.findOrCreateDir(path))

        assertEquals(EnvironmentVariant.PRODUCTION, variant("deploy/overlays/production"))
        assertEquals(EnvironmentVariant.PRODUCTION, variant("clusters/prod-eu-west-1"))
        assertEquals(EnvironmentVariant.STAGING, variant("infra/envs/stage"))
        assertEquals(EnvironmentVariant.LOCAL, variant("environments/dev"))
        assertEquals(EnvironmentVariant.TEST, variant("stages/test_us"))
        assertNull("src/test/ is a source folder, not an environment", variant("src/test"))
        assertNull("Production outside a container is just a name", variant("docs/production"))
        assertNull("Mixed environments are ambiguous", variant("overlays/prod-to-staging"))
        assertNull("Unknown environments are ignored", variant("overlays/preview"))
        assertNull("The container itself is not an environment", variant("overlays"))
    }

    fun testConfigurationFilesInheritTheNearestEnvironmentFolder() {
        fun file(path: String) = myFixture.addFileToProject(path, "data").virtualFile

        val deployment = file("k8s/overlays/production/apps/deployment.yaml")
        assertEquals(EnvironmentVariant.PRODUCTION, EnvironmentVariant.fromFile(deployment))
        assertTrue(EnvironmentVariant.isInherited(deployment))
        assertEquals(EnvironmentVariant.PRODUCTION, EnvironmentVariant.fromFile(file("k8s/overlays/production/kustomization")))
        assertEquals(EnvironmentVariant.STAGING, EnvironmentVariant.fromFile(file("infra/envs/staging/main.tfvars")))

        assertNull("Docs do not inherit", EnvironmentVariant.fromFile(file("k8s/overlays/production/README.md")))
        assertNull("Sources do not inherit", EnvironmentVariant.fromFile(file("k8s/overlays/production/hook.kt")))
        assertNull("Too deep to inherit", EnvironmentVariant.fromFile(file("envs/prod/a/b/c/d/e/f/g.yaml")))

        val ownName = file("envs/prod/values-staging.yaml")
        assertEquals("A file's own name wins", EnvironmentVariant.STAGING, EnvironmentVariant.fromFile(ownName))
        assertFalse(EnvironmentVariant.isInherited(ownName))

        val nested = file("clusters/prod/overlays/test/patch.yaml")
        assertEquals("The nearest folder wins", EnvironmentVariant.TEST, EnvironmentVariant.fromFile(nested))
    }

    fun testEnvironmentFoldersAndInheritingFilesAreBadged() {
        val folder = myFixture.tempDirFixture.findOrCreateDir("deploy/overlays/production")
        val folderBadge = provider.getIcon(folder, 0, project) as? EnvironmentVariantBadgeIcon
        assertNotNull("The environment folder is badged", folderBadge)
        assertEquals(EnvironmentVariant.PRODUCTION, folderBadge!!.variant)

        val patch = myFixture.addFileToProject("deploy/overlays/production/replicas.yaml", "replicas: 3")
        assertEnvironmentBadge("symbols/icons/files/yaml.svg", EnvironmentVariant.PRODUCTION, provider.getIcon(patch, 0))

        val resolution = IconResolver.resolveWithDetails(patch.virtualFile, project)
        assertTrue(resolution.detail, "inherits it from the overlays/production/ environment folder" in resolution.detail)
        assertEquals("Environment configuration · production", IconDescriptions.describe(patch.virtualFile, resolution))
        assertEquals(
            "Environment folder · production",
            IconDescriptions.describe(folder, IconResolver.resolveWithDetails(folder, project)),
        )
        assertTrue(EmberkaiScope.PRODUCTION_CONFIG.contains(patch.virtualFile, project))

        settings.setEnabled(IconRuleCategory.ENVIRONMENT_VARIANTS, false)
        assertFalse(provider.getIcon(folder, 0, project) is EnvironmentVariantBadgeIcon)
        assertResourceIcon("symbols/icons/files/yaml.svg", provider.getIcon(patch, 0))
    }

    private fun assertYamlIcon(icon: Icon?) {
        assertNotNull(icon)
        assertFalse("Unknown environment names should not be decorated", icon is EnvironmentVariantBadgeIcon)
        assertResourceIcon("symbols/icons/files/yaml.svg", icon)
    }
}
