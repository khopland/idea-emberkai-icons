package com.github.khopland.symbols

import com.github.khopland.symbols.badges.UnpinnedReference
import com.github.khopland.symbols.badges.UnpinnedReferenceBadgeIcon
import com.github.khopland.symbols.badges.UnpinnedReferenceFact
import com.github.khopland.symbols.badges.UnpinnedReferences
import com.github.khopland.symbols.badges.UnpinnedReferences.Kind
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.fact
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.setEnabled

internal class UnpinnedReferenceBadgeTest : IconProviderTestCase() {
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

    fun testActionsMustBePinnedToACommitUnlessGitHubPublishesThem() {
        assertNull(UnpinnedReferences.actionProblem("actions/checkout@v4"))
        assertNull(UnpinnedReferences.actionProblem("github/codeql-action/init@v3"))
        assertNull(UnpinnedReferences.actionProblem("./.github/actions/setup"))
        assertNull(UnpinnedReferences.actionProblem("docker://alpine:3.20"))
        assertNull(UnpinnedReferences.actionProblem("gradle/actions/setup-gradle@" + "a".repeat(40)))
        assertEquals("uses @v4, not a commit SHA", UnpinnedReferences.actionProblem("gradle/actions/setup-gradle@v4"))
        assertEquals("uses @main, not a commit SHA", UnpinnedReferences.actionProblem("some/action@main"))
        assertEquals("has no version", UnpinnedReferences.actionProblem("some/action"))
    }

    fun testImagesNeedATagOtherThanLatest() {
        assertEquals("has no tag, so it means latest", UnpinnedReferences.imageProblem("nginx"))
        assertEquals("has no tag, so it means latest", UnpinnedReferences.imageProblem("localhost:5000/team/app"))
        assertEquals("uses latest", UnpinnedReferences.imageProblem("ghcr.io/org/app:latest"))
        assertNull(UnpinnedReferences.imageProblem("nginx:1.27"))
        assertNull(UnpinnedReferences.imageProblem("localhost:5000/team/app:2.0"))
        assertNull(UnpinnedReferences.imageProblem("nginx@sha256:abc"))
        assertNull(UnpinnedReferences.imageProblem("\${REGISTRY}/app"))
        assertNull(UnpinnedReferences.imageProblem("{{ .Values.image }}"))
        assertNull(UnpinnedReferences.imageProblem("scratch"))
    }

    fun testDockerfileSkipsEarlierStages() {
        val dockerfile = """
            FROM --platform=linux/amd64 gradle:8.10-jdk21 AS build
            RUN gradle build
            FROM build AS test
            FROM eclipse-temurin
            COPY --from=build /app.jar /app.jar
        """.trimIndent()
        assertEquals(
            listOf(UnpinnedReference(4, "eclipse-temurin", "has no tag, so it means latest")),
            UnpinnedReferences.find(dockerfile, Kind.DOCKERFILE),
        )
    }

    fun testWorkflowIsBadgedAndExplained() {
        val workflow = myFixture.addFileToProject(
            ".github/workflows/build.yml",
            """
            jobs:
              build:
                steps:
                  - uses: actions/checkout@v4
                  - uses: "gradle/actions/setup-gradle@v4"
                  - name: Release
                    uses: softprops/action-gh-release@${"c".repeat(40)} # v2
            """.trimIndent(),
        )
        val fine = myFixture.addFileToProject("docs/build.yml", "steps:\n  - uses: some/action@main\n")

        assertTrue(provider.getIcon(workflow, 0) is UnpinnedReferenceBadgeIcon)
        assertFalse("Only workflow folders are read", provider.getIcon(fine, 0) is UnpinnedReferenceBadgeIcon)

        val resolution = IconResolver.resolveWithDetails(workflow.virtualFile, project)
        assertEquals(
            listOf(UnpinnedReference(5, "gradle/actions/setup-gradle@v4", "uses @v4, not a commit SHA")),
            resolution.fact<UnpinnedReferenceFact>()?.references,
        )
        assertTrue(resolution.detail, "line 5: gradle/actions/setup-gradle@v4 uses @v4, not a commit SHA" in resolution.detail)

        settings.setEnabled(IconRuleCategory.UNPINNED_REFERENCES, false)
        assertFalse(provider.getIcon(workflow, 0) is UnpinnedReferenceBadgeIcon)
    }

    fun testDockerfilesManifestsAndComposeFilesAreBadged() {
        val dockerfile = myFixture.addFileToProject("Dockerfile", "FROM node:latest\n")
        val pinned = myFixture.addFileToProject("api/Dockerfile.prod", "FROM node:22-alpine\n")
        val deployment = myFixture.addFileToProject(
            "k8s/deployment.yaml",
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: api
            spec:
              template:
                spec:
                  containers:
                    - name: api
                      image: ghcr.io/org/api
            """.trimIndent(),
        )
        val compose = myFixture.addFileToProject("compose.dev.yaml", "services:\n  db:\n    image: postgres\n")
        val plain = myFixture.addFileToProject("config/settings.yaml", "image: postgres\n")

        assertTrue(provider.getIcon(dockerfile, 0) is UnpinnedReferenceBadgeIcon)
        assertFalse(provider.getIcon(pinned, 0) is UnpinnedReferenceBadgeIcon)
        assertTrue(provider.getIcon(deployment, 0) is UnpinnedReferenceBadgeIcon)
        assertTrue(provider.getIcon(compose, 0) is UnpinnedReferenceBadgeIcon)
        assertFalse("Other YAML is not read", provider.getIcon(plain, 0) is UnpinnedReferenceBadgeIcon)
    }
}
