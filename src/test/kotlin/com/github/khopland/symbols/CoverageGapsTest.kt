package com.github.khopland.symbols

import com.github.khopland.symbols.badges.EnvironmentVariant
import com.github.khopland.symbols.core.IconDescriptions
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.EmberkaiScope
import com.github.khopland.symbols.settings.setEnabled

internal class CoverageGapsTest : IconProviderTestCase() {
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

    fun testJvmFrameworkConfigurationFollowsBuildDeclarations() {
        myFixture.addFileToProject("quarkus/pom.xml", "<project><dependencyManagement><dependency><groupId>io.quarkus.platform</groupId></dependency></dependencyManagement></project>")
        assertCustomPathIcon("quarkus/src/main/resources/application.properties", "frameworks/quarkus.svg")
        val prod = myFixture.addFileToProject("quarkus/src/main/resources/application-prod.yaml", "a: 1")
        assertEnvironmentBadge("emberkai/icons/frameworks/quarkus.svg", EnvironmentVariant.PRODUCTION, provider.getIcon(prod, 0))

        myFixture.addFileToProject("micronaut/build.gradle.kts", "plugins { id(\"io.micronaut.application\") version \"4.4.0\" }")
        assertCustomPathIcon("micronaut/src/main/resources/application.yml", "frameworks/micronaut.svg")
        assertCustomPathIcon("micronaut/src/main/resources/bootstrap.yml", "frameworks/micronaut.svg")

        myFixture.addFileToProject("ktor/build.gradle.kts", "dependencies { implementation(\"io.ktor:ktor-server-core:3.0.0\") }")
        assertCustomPathIcon("ktor/src/main/resources/application.conf", "frameworks/ktor.svg")

        myFixture.addFileToProject("catalog/gradle/libs.versions.toml", "[plugins]\nquarkus = { id = \"io.quarkus\", version = \"3.15.0\" }")
        assertCustomPathIcon("catalog/app/src/main/resources/application.yml", "frameworks/quarkus.svg")
    }

    fun testSpringBootWinsAndUndeclaredFrameworksAreIgnored() {
        myFixture.addFileToProject("both/build.gradle.kts", "plugins { id(\"org.springframework.boot\"); id(\"io.quarkus\") }")
        assertCustomPathIcon("both/src/main/resources/application.yml", "spring.svg")

        myFixture.addFileToProject("plain/build.gradle.kts", "// id(\"io.quarkus\") is commented out\nplugins { java }")
        val plain = myFixture.addFileToProject("plain/src/main/resources/application.yml", "a: 1").virtualFile
        assertNull(IconResolver.resolveWithDetails(plain, project).contextRule)

        myFixture.addFileToProject("lookalike/build.gradle.kts", "dependencies { implementation(\"io.quarkusx:tool:1\") }")
        val lookalike = myFixture.addFileToProject("lookalike/application.properties", "a=1").virtualFile
        assertNull(IconResolver.resolveWithDetails(lookalike, project).contextRule)
    }

    fun testNearestFrameworkDeclarationWinsOverASharedCatalog() {
        // A monorepo catalog declares Spring Boot for some services; the service's own build file decides.
        myFixture.addFileToProject("gradle/libs.versions.toml", "[plugins]\nspring-boot = { id = \"org.springframework.boot\", version = \"3.4.0\" }")
        myFixture.addFileToProject("services/orders/build.gradle.kts", "plugins { id(\"io.quarkus\") }")
        myFixture.addFileToProject("services/catalog/build.gradle.kts", "plugins { id(\"io.micronaut.application\") }")
        myFixture.addFileToProject("services/payments/build.gradle.kts", "plugins { java }")

        assertCustomPathIcon("services/orders/src/main/resources/application.properties", "frameworks/quarkus.svg")
        assertCustomPathIcon("services/catalog/src/main/resources/application.yml", "frameworks/micronaut.svg")
        assertCustomPathIcon("services/payments/src/main/resources/application.yml", "spring.svg")
    }

    fun testJvmFrameworkIconsCanBeDisabled() {
        myFixture.addFileToProject("svc/build.gradle.kts", "plugins { id(\"io.ktor.plugin\") }")
        val conf = myFixture.addFileToProject("svc/src/main/resources/application.conf", "ktor {}").virtualFile
        val resolution = IconResolver.resolveWithDetails(conf, project)
        assertEquals(IconRuleCategory.JVM_FRAMEWORKS, resolution.contextRule)
        assertEquals("Ktor configuration", IconDescriptions.describe(conf, resolution, project))

        settings.setEnabled(IconRuleCategory.JVM_FRAMEWORKS, false)
        assertNull(IconResolver.resolveWithDetails(conf, project).contextRule)
    }

    fun testQualityToolingFiles() {
        assertCustomPathIcon("config/detekt/detekt.yml", "quality/detekt.svg")
        assertCustomPathIcon("detekt-baseline.xml", "quality/detekt.svg")
        assertCustomPathIcon("rewrite.yml", "quality/openrewrite.svg")
        assertCustomPathIcon("sonar-project.properties", "quality/sonar.svg")
        assertPathIcon("jib.yaml", "files/docker.svg")
    }

    fun testEventContracts() {
        assertCustomPathIcon("contracts/asyncapi.yaml", "contracts/asyncapi.svg")
        assertContent("events.yaml", "asyncapi: 3.0.0\ninfo: {title: Orders}", "emberkai/icons/contracts/asyncapi.svg")
        assertContent("legacy.json", "{\"asyncapi\": \"2.6.0\", \"channels\": {}}", "emberkai/icons/contracts/asyncapi.svg")
        assertPathIcon("not-async.yaml", "files/yaml.svg")
        for (name in arrayOf("user.avsc", "orders.avdl", "protocol.avpr")) {
            assertCustomPathIcon("schemas/$name", "contracts/avro.svg")
        }
        for (name in arrayOf("avro", "asyncapi", "schema-registry")) {
            assertCustomFolderIcon(name, "folders/folder-api.svg")
        }
        myFixture.addFileToProject("events/asyncapi.yml", "")
        assertCustomFolderIcon("events", "folders/folder-api.svg")

        for (path in arrayOf("events.yaml", "schemas/user.avsc", "contracts/asyncapi.yaml")) {
            assertTrue(path, EmberkaiScope.API_CONTRACTS.contains(myFixture.findFileInTempDir(path)!!, project))
        }
    }

    fun testCiAndBuildFiles() {
        assertCustomPathIcon(".circleci/config.yml", "ci/circleci.svg")
        assertPathIcon("other/config.yml", "files/yaml.svg")
        assertFolderIcon(".circleci", "folders/folder-config.svg")
        assertContent("pipeline.yaml", "apiVersion: tekton.dev/v1\nkind: Pipeline\n", "emberkai/icons/ci/tekton.svg")
        assertContent("listener.yaml", "apiVersion: triggers.tekton.dev/v1beta1\nkind: EventListener\n", "emberkai/icons/ci/tekton.svg")
        for (name in arrayOf("MODULE.bazel", "MODULE.bazel.lock", "BUILD.bazel", "WORKSPACE.bazel", ".bazelrc", ".bazelversion", "defs.bzl")) {
            assertCustomPathIcon("bazel/$name", "ci/bazel.svg")
        }
        assertTrue(EmberkaiScope.CI_PIPELINES.contains(myFixture.findFileInTempDir(".circleci/config.yml")!!, project))
        assertTrue(EmberkaiScope.KUBERNETES.contains(myFixture.findFileInTempDir("pipeline.yaml")!!, project))
    }

    fun testInnerDevLoopFiles() {
        assertCustomPathIcon("skaffold.yaml", "devloop/skaffold.svg")
        assertContent("deploy/dev.yaml", "apiVersion: skaffold/v4beta11\nkind: Config\n", "emberkai/icons/devloop/skaffold.svg")
        assertCustomPathIcon("Tiltfile", "devloop/tilt.svg")
        assertCustomPathIcon("dagger.json", "devloop/dagger.svg")
        assertPathIcon(".testcontainers.properties", "files/docker-green.svg")
        assertPathIcon("src/test/resources/testcontainers.properties", "files/docker-green.svg")
        assertEquals(
            "Skaffold configuration",
            IconDescriptions.describe(
                myFixture.findFileInTempDir("deploy/dev.yaml")!!,
                IconResolver.resolveWithDetails(myFixture.findFileInTempDir("deploy/dev.yaml")!!, project),
                project,
            ),
        )
    }

    fun testHashiCorpInfrastructureFiles() {
        for (name in arrayOf("image.pkr.hcl", "image.pkr.json", "vars.pkrvars.hcl")) {
            assertCustomPathIcon("packer/$name", "infra/packer.svg")
        }
        assertCustomPathIcon("Vagrantfile", "infra/vagrant.svg")
        assertCustomPathIcon("jobs/api.nomad.hcl", "infra/nomad.svg")
        assertCustomPathIcon("jobs/web.nomad", "infra/nomad.svg")
        for (path in arrayOf("packer/image.pkr.hcl", "Vagrantfile", "jobs/api.nomad.hcl")) {
            assertTrue(path, EmberkaiScope.INFRASTRUCTURE.contains(myFixture.findFileInTempDir(path)!!, project))
        }
    }

    private fun assertContent(path: String, content: String, icon: String) {
        val file = myFixture.addFileToProject(path, content)
        assertResourceIcon(icon, provider.getIcon(file, 0))
        assertResourceIcon(icon, provider.getIcon(file.virtualFile, 0, project))
    }
}
