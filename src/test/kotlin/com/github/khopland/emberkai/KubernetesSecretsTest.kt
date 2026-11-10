package com.github.khopland.emberkai

import com.github.khopland.emberkai.badges.KubernetesSecrets
import com.github.khopland.emberkai.badges.PlaintextSecret
import com.github.khopland.emberkai.badges.SecretFileExposure
import com.github.khopland.emberkai.badges.UnignoredSecretBadgeIcon
import com.github.khopland.emberkai.core.IconDescriptions
import com.github.khopland.emberkai.core.IconResolver
import com.github.khopland.emberkai.settings.IconRuleCategory
import com.github.khopland.emberkai.lens.IconLens
import com.github.khopland.emberkai.rules.SopsFiles
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.github.khopland.emberkai.scopes.EmberkaiScope
import com.github.khopland.emberkai.settings.setEnabled
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.registerOrReplaceServiceInstance

internal class KubernetesSecretsTest : IconProviderTestCase() {
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

    fun testSecretsWithValuesInTheFileArePlaintext() {
        assertEquals(
            listOf(PlaintextSecret(2, "db", listOf("username", "password"))),
            KubernetesSecrets.plaintext(SECRET),
        )
        val bundle = """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: api
            ---
            apiVersion: v1
            kind: Secret
            metadata:
              name: tokens
            stringData:
              api-token: abc123
              empty: ""
              rendered: "{{ .Values.token }}"
              variable: ${'$'}{TOKEN}
        """.trimIndent()
        assertEquals(listOf(PlaintextSecret(7, "tokens", listOf("api-token"))), KubernetesSecrets.plaintext(bundle))
    }

    fun testSecretsWithoutLiteralValuesAreNotPlaintext() {
        for (text in listOf(
            "apiVersion: v1\nkind: Secret\nmetadata:\n  name: sa\ntype: kubernetes.io/service-account-token\n",
            "apiVersion: v1\nkind: Secret\ndata: {}\n",
            "apiVersion: v1\nkind: Secret\nstringData:\n  token: \"{{ .Values.token | b64enc }}\"\n",
            "apiVersion: v1\nkind: ConfigMap\ndata:\n  color: blue\n",
            "apiVersion: example.com/v1\nkind: Secret\ndata:\n  token: abc\n",
            SOPS_SECRET,
        )) {
            assertEquals(text, emptyList<PlaintextSecret>(), KubernetesSecrets.plaintext(text))
        }
    }

    fun testSopsMetadataIsRecognizedInEveryFormat() {
        assertTrue(SopsFiles.isEncrypted(SOPS_SECRET))
        assertTrue(SopsFiles.isEncrypted("{\"token\": \"ENC[AES256_GCM,data:abc,type:str]\",\n\"sops\": {\"version\": \"3.9.0\"}}"))
        assertTrue(SopsFiles.isEncrypted("TOKEN=ENC[AES256_GCM,data:abc,type:str]\nsops_mac=ENC[AES256_GCM,data:def,type:str]\n"))
        assertFalse("A value alone is not SOPS metadata", SopsFiles.isEncrypted("token: ENC[AES256_GCM,data:abc]\n"))
        assertFalse(SopsFiles.isEncrypted("sops:\n  enabled: true\n"))
    }

    fun testPlaintextSecretIsBadgedAndExplained() {
        useExposure { true }
        val file = myFixture.addFileToProject("k8s/db-secret.yaml", SECRET).virtualFile

        val resolution = IconResolver.resolveWithDetails(file, project)
        val badge = resolution.icon as? UnignoredSecretBadgeIcon
        assertNotNull("A plaintext Secret VCS would commit is badged", badge)
        assertResourceIcon("emberkai/icons/kubernetes/secret.svg", badge!!.baseIcon)
        assertTrue(resolution.detail, resolution.detail.contains("line 2: db (username, password)"))
        assertEquals(
            "Kubernetes manifest · plaintext Secret not ignored by VCS",
            IconDescriptions.describe(file, resolution),
        )
        assertTrue(IconLens.PRODUCTION_BLAST_RADIUS.contains(file, project))

        settings.setEnabled(IconRuleCategory.UNIGNORED_SECRETS, false)
        assertFalse(provider.getIcon(file, 0, project) is UnignoredSecretBadgeIcon)
    }

    fun testIgnoredSecretsKeepTheirIcons() {
        useExposure { false }
        val ignored = myFixture.addFileToProject("k8s/ignored-secret.yaml", SECRET).virtualFile
        assertFalse(provider.getIcon(ignored, 0, project) is UnignoredSecretBadgeIcon)
        assertFalse(IconLens.PRODUCTION_BLAST_RADIUS.contains(ignored, project))
    }

    fun testFilesWithoutPlaintextSecretsAreNeverAskedAboutVcs() {
        val asked = useExposure { true }
        val config = myFixture.addFileToProject("k8s/config.yaml", "apiVersion: v1\nkind: ConfigMap\ndata:\n  a: b\n").virtualFile
        val encrypted = myFixture.addFileToProject("k8s/encrypted-secret.yaml", SOPS_SECRET).virtualFile
        assertFalse(provider.getIcon(config, 0, project) is UnignoredSecretBadgeIcon)
        assertFalse(provider.getIcon(encrypted, 0, project) is UnignoredSecretBadgeIcon)
        assertTrue(asked.toString(), asked.isEmpty())
    }

    fun testSecretManagersHaveTheirOwnArtwork() {
        assertK8sIcon(
            "sealedsecret.svg",
            "apiVersion: bitnami.com/v1alpha1\nkind: SealedSecret\nmetadata:\n  name: db\nspec:\n  encryptedData:\n    password: AgB4x\n",
        )
        assertK8sIcon("externalsecret.svg", "apiVersion: external-secrets.io/v1beta1\nkind: ExternalSecret\nmetadata:\n  name: db\n")
        assertK8sIcon("externalsecret.svg", "apiVersion: external-secrets.io/v1beta1\nkind: ClusterExternalSecret\nmetadata:\n  name: db\n")
        assertK8sIcon("sops.svg", SOPS_SECRET)

        val sealed = myFixture.addFileToProject("k8s/describe-sealed.yaml", "apiVersion: bitnami.com/v1alpha1\nkind: SealedSecret\n").virtualFile
        assertEquals("Sealed Secret", IconDescriptions.describe(sealed, IconResolver.resolveWithDetails(sealed, project)))
        val encrypted = myFixture.addFileToProject("k8s/describe-sops.yaml", SOPS_SECRET).virtualFile
        assertEquals(
            "SOPS-encrypted Kubernetes manifest",
            IconDescriptions.describe(encrypted, IconResolver.resolveWithDetails(encrypted, project)),
        )
        assertTrue("Encrypted manifests stay in the Kubernetes scope", EmberkaiScope.KUBERNETES.contains(encrypted, project))
    }

    fun testSopsFilesAndConfigurationOutsideKubernetes() {
        val helmSecrets = myFixture.addFileToProject(
            "charts/api/secrets.yaml",
            "db:\n  password: ENC[AES256_GCM,data:abc,type:str]\nsops:\n  version: 3.9.0\n",
        ).virtualFile
        val dotenv = myFixture.addFileToProject(
            "deploy/prod.env",
            "TOKEN=ENC[AES256_GCM,data:abc,type:str]\nsops_mac=ENC[AES256_GCM,data:def,type:str]\n",
        ).virtualFile
        val config = myFixture.addFileToProject(".sops.yaml", "creation_rules:\n  - age: age1abc\n").virtualFile
        for (file in listOf(helmSecrets, dotenv, config)) {
            assertResourceIcon("emberkai/icons/sops.svg", provider.getIcon(file, 0, project))
        }
        assertEquals("SOPS configuration", IconDescriptions.describe(config, IconResolver.resolveWithDetails(config, project)))
        assertEquals("SOPS-encrypted file", IconDescriptions.describe(dotenv, IconResolver.resolveWithDetails(dotenv, project)))
        assertTrue(EmberkaiScope.INFRASTRUCTURE.contains(helmSecrets, project))

        settings.setEnabled(IconRuleCategory.SOPS, false)
        assertNotSame(IconResolver.resolve(dotenv, project), provider.getIcon(helmSecrets, 0, project).also { assertNotNull(it) })
        assertFalse(IconResolver.resolveWithDetails(helmSecrets, project).mappingKey == "context.sops")
    }

    private fun assertK8sIcon(icon: String, text: String) {
        val file = myFixture.addFileToProject("k8s/${icon.removeSuffix(".svg")}-${text.hashCode()}.yaml", text).virtualFile
        assertResourceIcon("emberkai/icons/kubernetes/$icon", provider.getIcon(file, 0, project))
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

    private companion object {
        val SECRET = """
            apiVersion: v1
            kind: Secret
            metadata:
              name: db
            type: Opaque
            data:
              username: YWRtaW4=
              password: c2VjcmV0
        """.trimIndent()

        val SOPS_SECRET = """
            apiVersion: v1
            kind: Secret
            metadata:
              name: db
            data:
              password: ENC[AES256_GCM,data:abc,iv:def,tag:ghi,type:str]
            sops:
              mac: ENC[AES256_GCM,data:xyz,type:str]
              version: 3.9.0
        """.trimIndent()
    }
}
