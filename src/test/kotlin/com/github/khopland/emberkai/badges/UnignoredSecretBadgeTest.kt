package com.github.khopland.emberkai.badges

import com.github.khopland.emberkai.IconProviderTestCase
import com.github.khopland.emberkai.core.BadgedIcon
import com.github.khopland.emberkai.core.IconDescriptions
import com.github.khopland.emberkai.core.IconResolver
import com.github.khopland.emberkai.core.IconRuleCategory
import com.github.khopland.emberkai.core.fact
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.github.khopland.emberkai.settings.setEnabled
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.registerOrReplaceServiceInstance
import javax.swing.Icon

internal class UnignoredSecretBadgeTest : IconProviderTestCase() {
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

    fun testSecretNamesAreMatchedPrecisely() {
        for (name in listOf(
            ".env", ".env.local", ".env.production", ".ENV.Staging", "server.pem", "tls.key", "client.p12",
            "cert.pfx", "release.jks", "debug.keystore", "id_rsa", "id_ed25519", "id_ecdsa", "id_dsa",
            ".netrc", ".pypirc", "credentials.json", "service-account.json", "service-account-prod.json",
        )) {
            assertTrue(name, SecretFileNames.isCandidate(name))
        }
        for (name in listOf(
            ".env.example", ".env.sample", ".env.template", ".env.dist", ".env.defaults", ".env.local.example",
            ".envrc", "env", "id_rsa.pub", "id_ed25519.pub", ".npmrc", "secrets.yaml", "credentials.yml",
            "service-account.yaml", "keys.json", "monkey", ".pem", "keystore.txt",
        )) {
            assertFalse(name, SecretFileNames.isCandidate(name))
        }
    }

    fun testVcsServiceIsRegisteredAndFilesOutsideVcsKeepTheirIcons() {
        assertTrue(SecretFileExposure.getInstance(project) is VcsSecretFileExposure)

        val key = myFixture.addFileToProject("id_rsa", "-----BEGIN OPENSSH PRIVATE KEY-----")
        assertFalse(provider.getIcon(key, 0) is UnignoredSecretBadgeIcon)
        assertFalse(provider.getIcon(key.virtualFile, 0, project) is UnignoredSecretBadgeIcon)

        val dotenv = myFixture.addFileToProject(".env.local", "TOKEN=abc")
        assertEquals(EnvironmentVariant.LOCAL, (provider.getIcon(dotenv, 0) as? EnvironmentVariantBadgeIcon)?.variant)
    }

    fun testExposedSecretsReceiveWarningBadgeOverTheirBaseIcon() {
        val exposed = useExposure { true }

        val dotenv = myFixture.addFileToProject(".env", "TOKEN=abc")
        val badge = provider.getIcon(dotenv, 0) as? UnignoredSecretBadgeIcon
        assertNotNull("An exposed .env file is badged", badge)
        assertResourceIcon("symbols/icons/files/gear.svg", badge!!.baseIcon)
        assertFalse(badge.highContrast)
        assertTrue(exposed.contains(dotenv.virtualFile))

        val local = myFixture.addFileToProject(".env.local", "TOKEN=abc")
        val layered = provider.getIcon(local.virtualFile, 0, project) as? UnignoredSecretBadgeIcon
        assertNotNull("Secret and environment badges stack", layered)
        assertEquals(EnvironmentVariant.LOCAL, (layered!!.baseIcon as? EnvironmentVariantBadgeIcon)?.variant)

        val template = myFixture.addFileToProject(".env.example", "TOKEN=")
        assertFalse(provider.getIcon(template, 0) is UnignoredSecretBadgeIcon)
        assertFalse("Templates are never asked about VCS state", exposed.contains(template.virtualFile))

        val key = myFixture.addFileToProject("deploy/id_ed25519", "key")
        assertNotNull(
            "Files without a mapping fall back to their file-type icon",
            (provider.getIcon(key, 0) as? UnignoredSecretBadgeIcon)?.baseIcon,
        )
    }

    fun testAnExposedSecretOutranksOtherWarningsButStillReportsThem() {
        useExposure { true }
        myFixture.addFileToProject(".env.staging", "DB_URL=staging\nFEATURE_FLAG=1\n")
        myFixture.addFileToProject(".env.test", "DB_URL=test\nFEATURE_FLAG=1\n")
        val production = myFixture.addFileToProject(".env.production", "DB_URL=prod\n").virtualFile

        val resolution = IconResolver.resolveWithDetails(production, project)
        val secret = resolution.icon as? UnignoredSecretBadgeIcon
        assertNotNull("The exposed secret holds the attention corner", secret)
        assertEquals(EnvironmentVariant.PRODUCTION, (secret!!.baseIcon as? EnvironmentVariantBadgeIcon)?.variant)
        assertFalse(
            "The drift warning is not drawn under the secret badge",
            generateSequence<Icon>(secret) { (it as? BadgedIcon)?.baseIcon }.any { it is ConfigDriftBadgeIcon },
        )
        assertNotNull("The drift is still reported", resolution.fact<ConfigDriftFact>())
        assertTrue(resolution.detail, resolution.detail.contains("A more urgent badge is drawn in its place."))
        assertEquals(
            "Environment file · production · missing FEATURE_FLAG · not ignored by VCS — may contain secrets",
            IconDescriptions.describe(production, resolution),
        )
    }

    fun testIgnoredOrUnknownSecretsKeepTheirIcons() {
        useExposure { false }
        val dotenv = myFixture.addFileToProject(".env", "TOKEN=abc")
        assertResourceIcon("symbols/icons/files/gear.svg", provider.getIcon(dotenv, 0))
    }

    fun testWarningBadgeFollowsSettingsAndExplainsItself() {
        useExposure { true }
        val keystore = myFixture.addFileToProject("release.jks", "data")

        val resolution = IconResolver.resolveWithDetails(keystore.virtualFile, project)
        assertTrue(resolution.icon is UnignoredSecretBadgeIcon)
        assertTrue(resolution.detail, resolution.detail.contains("without being ignored"))
        assertEquals(
            "Not ignored by VCS — may contain secrets",
            IconDescriptions.describe(keystore.virtualFile, resolution),
        )
        val local = myFixture.addFileToProject(".env.local", "TOKEN=abc").virtualFile
        assertEquals(
            "Environment file · local · not ignored by VCS — may contain secrets",
            IconDescriptions.describe(local, IconResolver.resolveWithDetails(local, project)),
        )

        settings.highContrastFolderIcons = true
        assertTrue((provider.getIcon(keystore, 0) as UnignoredSecretBadgeIcon).highContrast)

        settings.setEnabled(IconRuleCategory.UNIGNORED_SECRETS, false)
        assertFalse(provider.getIcon(keystore, 0) is UnignoredSecretBadgeIcon)
        assertFalse(IconResolver.resolveWithDetails(keystore.virtualFile, project).detail.contains("warning badge"))
    }

    private fun useExposure(answer: (VirtualFile) -> Boolean): MutableSet<VirtualFile> {
        val asked = mutableSetOf<VirtualFile>()
        project.registerOrReplaceServiceInstance(
            SecretFileExposure::class.java,
            object : SecretFileExposure {
                override fun isExposed(file: VirtualFile): Boolean {
                    asked += file
                    return answer(file)
                }
            },
            testRootDisposable,
        )
        return asked
    }
}
