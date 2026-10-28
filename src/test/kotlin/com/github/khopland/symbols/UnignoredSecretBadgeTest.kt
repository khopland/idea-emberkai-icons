package com.github.khopland.symbols

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.registerOrReplaceServiceInstance

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

    fun testIgnoredOrUnknownSecretsKeepTheirIcons() {
        useExposure { false }
        val dotenv = myFixture.addFileToProject(".env", "TOKEN=abc")
        assertResourceIcon("symbols/icons/files/gear.svg", provider.getIcon(dotenv, 0))
    }

    fun testWarningBadgeFollowsSettingsAndExplainsItself() {
        useExposure { true }
        val keystore = myFixture.addFileToProject("release.jks", "data")

        val resolution = IconResolver().resolveWithDetails(keystore.virtualFile, project)
        assertTrue(resolution.icon is UnignoredSecretBadgeIcon)
        assertTrue(resolution.detail, resolution.detail.contains("without being ignored"))

        settings.highContrastFolderIcons = true
        assertTrue((provider.getIcon(keystore, 0) as UnignoredSecretBadgeIcon).highContrast)

        settings.setEnabled(IconRuleCategory.UNIGNORED_SECRETS, false)
        assertFalse(provider.getIcon(keystore, 0) is UnignoredSecretBadgeIcon)
        assertFalse(IconResolver().resolveWithDetails(keystore.virtualFile, project).detail.contains("warning badge"))
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
