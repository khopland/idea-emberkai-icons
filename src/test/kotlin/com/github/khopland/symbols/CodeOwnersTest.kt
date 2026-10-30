package com.github.khopland.symbols

import com.github.khopland.symbols.lens.LensSnapshot
import com.github.khopland.symbols.lens.OwnerLens
import com.github.khopland.symbols.lens.UnownedLens
import com.github.khopland.symbols.ownership.CodeOwners
import com.github.khopland.symbols.ownership.CodeOwnersRule
import com.github.khopland.symbols.ownership.UnmatchedCodeOwnersPatternInspection
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.EmberkaiScopesProvider
import com.github.khopland.symbols.tooltips.SemanticTooltipDecorator
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.PackageSetBase

internal class CodeOwnersTest : IconProviderTestCase() {
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

    /** Examples from GitHub's CODEOWNERS documentation. */
    fun testPatternsFollowGitHubSemantics() {
        val everything = rule("*")
        assertTrue(everything.matches("README.md", false))
        assertTrue(everything.matches("src/deep/App.kt", false))

        val js = rule("*.js")
        assertTrue(js.matches("app.js", false))
        assertTrue(js.matches("src/web/app.js", false))
        assertFalse(js.matches("app.jsx", false))

        val logs = rule("/build/logs/")
        assertTrue(logs.matches("build/logs/out.log", false))
        assertTrue(logs.matches("build/logs/deep/out.log", false))
        assertTrue(logs.matches("build/logs", true))
        assertFalse("A file named like the directory is not the directory", logs.matches("build/logs", false))
        assertFalse(logs.matches("src/build/logs/out.log", false))

        val docsLevel = rule("docs/*")
        assertTrue(docsLevel.matches("docs/getting-started.md", false))
        assertFalse(docsLevel.matches("docs/build-app/troubleshooting.md", false))

        val apps = rule("apps/")
        assertTrue(apps.matches("apps/web/main.ts", false))
        assertTrue("Unanchored directories match at any depth", apps.matches("packages/apps/a.ts", false))

        val rootDocs = rule("/docs/")
        assertTrue(rootDocs.matches("docs/a.md", false))
        assertFalse(rootDocs.matches("site/docs/a.md", false))

        val anyLogs = rule("**/logs")
        assertTrue(anyLogs.matches("logs/a.log", false))
        assertTrue(anyLogs.matches("build/deep/logs/a.log", false))

        val name = rule("Makefile")
        assertTrue(name.matches("Makefile", false))
        assertTrue(name.matches("tools/Makefile", false))
        assertFalse(name.matches("Makefile.old", false))
    }

    fun testParsingKeepsLineNumbersOwnersAndSkipsCommentsAndSections() {
        val rules = CodeOwners.parse(
            """
            # Default owners
            *       @org/platform

            [Payments]
            /services/payments/ @org/payments alice@example.com # inline comment
            /services/payments/generated/
            docs/My\ Guide.md @writer
            """.trimIndent(),
        )
        assertEquals(listOf("*", "/services/payments/", "/services/payments/generated/", "docs/My Guide.md"), rules.map { it.pattern })
        assertEquals(listOf(2, 5, 6, 7), rules.map { it.line })
        assertEquals(listOf("@org/payments", "alice@example.com"), rules[1].owners)
        assertTrue("A pattern without owners is explicitly unowned", rules[2].owners.isEmpty())
        assertTrue(rules[3].matches("docs/My Guide.md", false))
    }

    fun testLastMatchingLineWinsAndGitHubLocationIsUsed() {
        myFixture.addFileToProject(
            ".github/CODEOWNERS",
            "* @org/platform\n*.sql @org/data\n/services/payments/ @org/payments\n/services/payments/generated/\n",
        )
        val payments = myFixture.addFileToProject("services/payments/Api.kt", "").virtualFile
        val migration = myFixture.addFileToProject("services/payments/db/V1__init.sql", "").virtualFile
        val generated = myFixture.addFileToProject("services/payments/generated/Client.kt", "").virtualFile
        val readme = myFixture.addFileToProject("README.md", "").virtualFile
        val workflow = myFixture.addFileToProject(".github/workflows/ci.yml", "").virtualFile

        assertEquals(listOf("@org/payments"), CodeOwners.ownership(payments)!!.owners)
        assertEquals("The later directory rule wins over *.sql", listOf("@org/payments"), CodeOwners.ownership(migration)!!.owners)
        assertEquals(emptyList<String>(), CodeOwners.ownership(generated)!!.owners)
        assertEquals(listOf(4), CodeOwners.ownership(generated)!!.rules.map { it.line })
        assertEquals(listOf("@org/platform"), CodeOwners.ownership(readme)!!.owners)
        assertEquals("Paths inside .github are relative to the repository", listOf("@org/platform"), CodeOwners.ownership(workflow)!!.owners)
        assertEquals(listOf("@org/payments"), CodeOwners.ownership(payments.parent)!!.owners)
    }

    fun testNestedRepositoriesAndMissingFilesHaveNoOwners() {
        val loose = myFixture.addFileToProject("README.md", "").virtualFile
        assertNull("No CODEOWNERS anywhere", CodeOwners.ownership(loose))

        myFixture.addFileToProject("CODEOWNERS", "* @org/platform\n")
        myFixture.tempDirFixture.findOrCreateDir("vendor/lib/.git")
        val vendored = myFixture.addFileToProject("vendor/lib/src/Lib.kt", "").virtualFile
        assertNull("A nested Git repository stops the search", CodeOwners.ownership(vendored))
        assertEquals(listOf("@org/platform"), CodeOwners.ownership(loose)!!.owners)
    }

    fun testUnsavedCodeOwnersEditsApply() {
        val codeOwners = myFixture.addFileToProject("CODEOWNERS", "* @old\n").virtualFile
        val file = myFixture.addFileToProject("App.kt", "").virtualFile
        assertEquals(listOf("@old"), CodeOwners.ownership(file)!!.owners)

        val document = FileDocumentManager.getInstance().getDocument(codeOwners)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("* @new\n") }
        assertEquals(listOf("@new"), CodeOwners.ownership(file)!!.owners)
    }

    fun testTooltipsNameOwnersUnderTheirOwnSetting() {
        myFixture.addFileToProject("CODEOWNERS", "* @org/platform\n/k8s/ @org/sre @bob\n")
        val manifest = myFixture.addFileToProject("k8s/service.yaml", "apiVersion: v1\nkind: Service\n")
        val node = PsiFileNode(project, manifest, ViewSettings.DEFAULT)

        assertEquals("Kubernetes manifest · Owned by @org/sre, @bob", tooltip(node))

        settings.semanticTooltips = false
        assertEquals("Owned by @org/sre, @bob", tooltip(node))

        settings.codeOwnerTooltips = false
        assertNull(tooltip(node))
    }

    fun testOwnerScopesAndLens() {
        myFixture.addFileToProject("CODEOWNERS", "* @org/platform\n/services/payments/ @org/payments\n")
        val payments = myFixture.addFileToProject("services/payments/Api.kt", "").virtualFile
        val readme = myFixture.addFileToProject("README.md", "").virtualFile

        val scopes = EmberkaiScopesProvider(project).customScopes.associateBy(NamedScope::getScopeId)
        val paymentsScope = scopes[EmberkaiScopesProvider.ownerScopeName("@org/payments")]
        assertNotNull(paymentsScope)
        assertNotNull(scopes[EmberkaiScopesProvider.ownerScopeName("@org/platform")])
        val set = paymentsScope!!.value as PackageSetBase
        assertTrue(set.contains(payments, project, null))
        assertFalse(set.contains(readme, project, null))

        val snapshot = runReadAction { LensSnapshot.collect(project, OwnerLens("@org/payments")) }
        assertEquals(1, snapshot.memberCount)
        assertTrue(snapshot.highlights(payments))
        assertTrue(snapshot.highlights(payments.parent))
        assertFalse(snapshot.highlights(readme))
    }

    fun testGitLabSectionsLendDefaultOwnersAndCombine() {
        val rules = CodeOwners.parse(
            """
            * @org/platform

            [Backend] @org/backend
            /services/
            /services/legacy/ @org/legacy

            ^[Docs][2] @org/writers
            *.md

            [Security]
            /services/auth/ @org/security
            /services/auth/generated/
            """.trimIndent(),
        )
        assertEquals(listOf(null, "Backend", "Backend", "Docs", "Security", "Security"), rules.map { it.section })
        assertEquals(listOf("@org/backend"), rules[1].owners)
        assertEquals(listOf("@org/legacy"), rules[2].owners)
        assertEquals(listOf("@org/writers"), rules[3].owners)
        assertTrue("No default owners: explicitly unowned", rules[5].owners.isEmpty())

        myFixture.addFileToProject(
            ".gitlab/CODEOWNERS",
            "* @org/platform\n[Backend] @org/backend\n/services/\n[Docs] @org/writers\n*.md\n" +
                "[Security]\n/services/auth/ @org/security\n",
        )
        val readme = myFixture.addFileToProject("services/auth/README.md", "").virtualFile
        val ownership = CodeOwners.ownership(readme)!!
        assertEquals(listOf("@org/platform", "@org/backend", "@org/writers", "@org/security"), ownership.owners)
        assertEquals(listOf(1, 3, 5, 7), ownership.rules.map { it.line })
    }

    fun testUnownedFilesLensAndScope() {
        myFixture.addFileToProject("CODEOWNERS", "/services/ @org/backend\n/services/generated/\n")
        val owned = myFixture.addFileToProject("services/Api.kt", "").virtualFile
        val explicitlyUnowned = myFixture.addFileToProject("services/generated/Client.kt", "").virtualFile
        val unmatched = myFixture.addFileToProject("tools/release.sh", "").virtualFile

        assertFalse(CodeOwners.isUnowned(owned))
        assertTrue(CodeOwners.isUnowned(explicitlyUnowned))
        assertTrue(CodeOwners.isUnowned(unmatched))

        val snapshot = runReadAction { LensSnapshot.collect(project, UnownedLens) }
        assertTrue(snapshot.highlights(unmatched))
        assertTrue(snapshot.highlights(explicitlyUnowned))
        assertFalse(snapshot.highlights(owned))

        val scope = EmberkaiScopesProvider(project).customScopes.single { it.scopeId == EmberkaiScopesProvider.UNOWNED_SCOPE }
        val set = scope.value as PackageSetBase
        assertTrue(set.contains(unmatched, project, null))
        assertFalse(set.contains(owned, project, null))
    }

    fun testFilesWithoutCodeOwnersAreNotUnowned() {
        val file = myFixture.addFileToProject("app/Main.kt", "").virtualFile
        assertFalse(CodeOwners.isUnowned(file))
        assertTrue(EmberkaiScopesProvider(project).customScopes.none { it.scopeId == EmberkaiScopesProvider.UNOWNED_SCOPE })
    }

    fun testPatternsThatMatchNothingAreReported() {
        myFixture.enableInspections(UnmatchedCodeOwnersPatternInspection())
        myFixture.addFileToProject("services/payments/Api.kt", "")
        myFixture.addFileToProject("docs/guide.md", "")
        val codeOwners = myFixture.addFileToProject(
            ".github/CODEOWNERS",
            "* @org/platform\n/services/payments/ @org/payments\n/services/billing/ @org/billing\n*.md @org/writers\n*.rst @org/writers\n",
        )
        myFixture.configureFromExistingVirtualFile(codeOwners.virtualFile)

        val problems = myFixture.doHighlighting()
            .filter { it.description == "Pattern matches no file or folder in the repository" }
            .map { codeOwners.text.substring(it.startOffset, it.endOffset) }
        assertEquals(listOf("/services/billing/", "*.rst"), problems)
    }

    fun testShadowedCodeOwnersFileIsReported() {
        myFixture.enableInspections(UnmatchedCodeOwnersPatternInspection())
        myFixture.addFileToProject(".github/CODEOWNERS", "* @org/platform\n")
        val shadowed = myFixture.addFileToProject("CODEOWNERS", "* @org/other\n")
        myFixture.configureFromExistingVirtualFile(shadowed.virtualFile)
        assertTrue(myFixture.doHighlighting().any { it.description == "Ignored: .github/CODEOWNERS takes precedence" })
    }

    private fun tooltip(node: PsiFileNode): String? =
        PresentationData().also { SemanticTooltipDecorator().decorate(node, it) }.tooltip

    private fun rule(pattern: String): CodeOwnersRule = CodeOwnersRule.compile(pattern, listOf("@owner"), 1)!!
}
