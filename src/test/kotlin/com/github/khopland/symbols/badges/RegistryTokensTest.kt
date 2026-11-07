package com.github.khopland.symbols.badges

import com.github.khopland.symbols.IconProviderTestCase
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.fact
import com.github.khopland.symbols.lens.IconLens
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.registerOrReplaceServiceInstance

internal class RegistryTokensTest : IconProviderTestCase() {
    fun testLiteralNpmCredentialsAreFound() {
        val tokens = RegistryTokens.npmrc(
            """
            registry=https://registry.npmjs.org/
            //registry.npmjs.org/:_authToken=npm_abc123
            # _authToken=commented-out
            //npm.pkg.github.com/:_authToken=${'$'}{GITHUB_TOKEN}
            @acme:registry=https://npm.acme.dev/
            //npm.acme.dev/:_password="c2VjcmV0"
            _auth = dXNlcjpwYXNz
            always-auth=true
            """.trimIndent(),
        )

        assertEquals(
            listOf(RegistryToken(2, "_authToken"), RegistryToken(6, "_password"), RegistryToken(7, "_auth")),
            tokens,
        )
    }

    fun testLiteralYarnCredentialsAreFound() {
        val tokens = RegistryTokens.yarnrc(
            """
            npmRegistryServer: "https://registry.npmjs.org"
            npmAuthToken: "npm_abc123"
            npmScopes:
              acme:
                npmAuthToken: ${'$'}{ACME_TOKEN-}
                npmAuthIdent: "user:pass"
            """.trimIndent(),
        )

        assertEquals(listOf(RegistryToken(2, "npmAuthToken"), RegistryToken(6, "npmAuthIdent")), tokens)
    }

    fun testCommentsEmptyValuesAndVariablesAreNotTokens() {
        assertEquals(
            emptyList<RegistryToken>(),
            RegistryTokens.yarnrc(
                listOf(
                    "npmAuthToken: \"\"  # set in CI", "npmAuthToken: # set in CI", "npmAuthToken: null",
                    "npmAuthToken: ~", "# npmAuthToken: npm_abc123", "npmAuthToken: \"${'$'}{NPM_TOKEN}\"",
                ).joinToString("\n"),
            ),
        )
        assertEquals(
            "A literal value keeps its trailing comment out of the check",
            listOf(RegistryToken(1, "npmAuthToken")),
            RegistryTokens.yarnrc("npmAuthToken: npm_abc123 # rotated monthly"),
        )
        assertEquals(
            emptyList<RegistryToken>(),
            RegistryTokens.npmrc(
                listOf(
                    "#//registry.npmjs.org/:_authToken=npm_abc123", ";//registry.npmjs.org/:_authToken=npm_abc123",
                    "//registry.npmjs.org/:_authToken=${'$'}NPM_TOKEN", "//registry.npmjs.org/:_authToken=",
                ).joinToString("\r\n"),
            ),
        )
        assertEquals(listOf(RegistryToken(2, "_authToken")), RegistryTokens.npmrc("# token\r\n//r.dev/:_authToken=abc\r\n"))
    }

    fun testExposedNpmrcWithATokenIsBadgedAndJoinsTheBlastRadius() {
        useExposure { true }
        val exposed = myFixture.addFileToProject("web/.npmrc", "//registry.npmjs.org/:_authToken=npm_abc123\n").virtualFile
        val safe = myFixture.addFileToProject("api/.npmrc", "//registry.npmjs.org/:_authToken=\${NPM_TOKEN}\n").virtualFile

        val resolution = IconResolver.resolveWithDetails(exposed, project)
        assertTrue(resolution.icon is UnignoredSecretBadgeIcon)
        assertEquals("registry token not ignored by VCS", resolution.fact<RegistryTokenFact>()?.label)
        assertTrue(resolution.detail, resolution.detail.contains("line 1: _authToken"))
        assertTrue(IconLens.PRODUCTION_BLAST_RADIUS.contains(exposed, project))

        assertFalse(IconResolver.resolve(safe, project) is UnignoredSecretBadgeIcon)
        assertFalse(IconLens.PRODUCTION_BLAST_RADIUS.contains(safe, project))
    }

    private fun useExposure(answer: (VirtualFile) -> Boolean) {
        project.registerOrReplaceServiceInstance(
            SecretFileExposure::class.java,
            object : SecretFileExposure {
                override fun isExposed(file: VirtualFile): Boolean = answer(file)
            },
            testRootDisposable,
        )
    }
}
