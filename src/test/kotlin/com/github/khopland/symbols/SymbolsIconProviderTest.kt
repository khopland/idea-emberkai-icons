package com.github.khopland.symbols

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.util.Locale
import java.util.ResourceBundle

internal class SymbolsIconProviderTest : IconProviderTestCase() {
    fun testExactFileNameTakesPriority() {
        assertFileIcon("tsconfig.json", "files/tsconfig.svg")
    }

    fun testCompositeExtensionTakesPriority() {
        assertFileIcon("feature.test.tsx", "files/react-test.svg")
    }

    fun testNamedFolder() {
        assertFolderIcon("assets", "folders/folder-assets.svg")
    }

    fun testUnknownFolder() {
        assertFolderIcon("random-folder", "folders/folder.svg")
    }

    fun testUnknownFileTypeUsesNativeIcon() {
        val file = myFixture.configureByText("file.unknownext", "data")
        assertNull(provider.getIcon(file, 0))
    }

    fun testJavaFilesAndTheirElementsUseNativeIcons() {
        assertNativeIcons("Example.java", "public class Example { class Nested {} }")
    }

    fun testKotlinFilesAndTheirElementsUseNativeIcons() {
        assertNativeIcons("Example.kt", "data class Example(val name: String)")
    }

    fun testKotlinScriptsUseNativeIcons() {
        assertNativeIcons("example.kts", "println(\"Hello\")")
    }

    fun testCompiledClassesUseNativeIcons() {
        val virtualFile = myFixture.tempDirFixture.createFile("Example.class")
        val file = PsiManager.getInstance(project).findFile(virtualFile)
        assertNotNull(file)
        assertNull(provider.getIcon(file!!, 0))
    }

    fun testCaseInsensitiveFileNames() {
        assertNativeIcons("Example.JAVA", "class Example {}")
        assertNativeIcons("Example.KT", "class Example")
        assertFileIcon("TSCONFIG.JSON", "files/tsconfig.svg")
    }

    fun testFoldersWithSourceExtensionsStillUseSymbols() {
        assertFolderIcon("examples.kt", "folders/folder.svg")
    }

    fun testOtherJavaRelatedFilesStillUseSymbols() {
        assertFileIcon("index.jsp", "files/java.svg")
    }

    fun testGitHubWorkflowsUseTheirPath() {
        assertPathIcon(".github/workflows/build.yml", "files/github.svg")
        assertPathIcon("nested/.github/workflows/release.yaml", "files/github.svg")
        assertPathIcon(".GitHub/Workflows/Check.YAML", "files/github.svg")
        assertPathIcon("config/build.yml", "files/yaml.svg")
        assertPathIcon("workflows/build.yml", "files/yaml.svg")
        assertPathIcon(".github/workflows/nested/config.yml", "files/yaml.svg")
        assertPathIcon(".github/workflows/README.md", "files/readme.svg")
    }

    fun testGitHubMetadata() {
        assertFileIcon("action.yml", "files/github.svg")
        assertFileIcon("action.yaml", "files/github.svg")
        assertFileIcon("CODEOWNERS", "files/github.svg")
        assertPathIcon(".github/dependabot.yml", "files/github.svg")
        assertPathIcon(".github/dependabot.yaml", "files/github.svg")
        assertPathIcon(".github/FUNDING.yml", "files/github.svg")
        assertPathIcon(".github/FUNDING.yaml", "files/github.svg")
        assertPathIcon("other/dependabot.yml", "files/yaml.svg")
    }

    fun testContextualFolders() {
        assertFolderIcon(".github/workflows", "folders/folder-github.svg")
        assertFolderIcon(".github/actions", "folders/folder-github.svg")
        assertFolderIcon("workflows", "folders/folder.svg")
        assertFolderIcon(".devcontainer", "folders/folder-docker.svg")
        assertFolderIcon(".mvn", "folders/folder-maven.svg")
    }

    fun testAdditionalEcosystemManifests() {
        for (name in arrayOf("Gemfile", "Gemfile.lock", "Rakefile")) {
            assertPathIcon(name, "files/ruby.svg")
        }
        for (name in arrayOf("pubspec.yaml", "pubspec.lock", "analysis_options.yaml")) {
            assertPathIcon(name, "files/dart.svg")
        }
        for (name in arrayOf("Package.swift", "Package.resolved", "Package.default.resolved")) {
            assertPathIcon(name, "files/swift.svg")
        }
        for (name in arrayOf(
            "Cargo.toml", "Cargo.lock", "rust-toolchain", "rust-toolchain.toml", "rustfmt.toml", "clippy.toml",
        )) {
            assertPathIcon(name, "files/rust.svg")
        }

        assertPathIcon("flake.lock", "files/nix.svg")
        assertPathIcon("flake.nix", "files/nix.svg")
        assertPathIcon("shell.nix", "files/nix.svg")
        for (name in arrayOf("buf.yaml", "buf.gen.yaml", "buf.work.yaml")) {
            assertPathIcon(name, "files/proto.svg")
        }
        assertCustomFolderIcon("proto", "folders/folder-api.svg")
        assertCustomFolderIcon("protobuf", "folders/folder-api.svg")
        assertPathIcon("lefthook.yml", "files/git.svg")
        assertPathIcon("Jenkinsfile", "files/jenkins.svg")
    }

    fun testTerraformAndOpenTofuMappings() {
        assertCustomPathIcon("terragrunt.hcl", "infrastructure.svg")
        assertFileIcon(".terraform-version", "files/terraform.svg")
        assertFileIcon(".terraform.lock.hcl", "files/terraform.svg")
        assertFileIcon("terraform.rc", "files/terraform.svg")
        assertFileIcon("main.tofu", "files/terraform.svg")
        assertCustomFolderIcon("terraform", "folders/folder-terraform.svg")
        assertCustomFolderIcon("terragrunt", "folders/folder-terraform.svg")

        val unrelatedHcl = myFixture.addFileToProject("other.hcl", "configuration")
        assertNull(provider.getIcon(unrelatedHcl, 0))
        assertNull(provider.getIcon(unrelatedHcl.virtualFile, 0, project))
    }

    fun testIdeAndGeneratedFoldersKeepNativeIcons() {
        for (path in arrayOf(
            ".idea", ".idea/runConfigurations", ".idea/inspectionProfiles",
            "generated", "generated-sources", "generated-test-sources",
        )) {
            assertNativeFolder(path)
        }
    }

    fun testConventionalJvmLayoutUsesThemedRolesBeforeImport() {
        assertFolderIcon("src", "folders/folder.svg")
        assertCustomFolderIcon("src/main", "folders/folder-content.svg")
        assertCustomFolderIcon("src/test", "folders/folder-content.svg")
        assertCustomFolderIcon("src/main/java", "folders/folder-source.svg")
        assertCustomFolderIcon("src/main/kotlin", "folders/folder-source.svg")
        assertCustomFolderIcon("src/test/java", "folders/folder-test-source.svg")
        assertCustomFolderIcon("src/test/kotlin", "folders/folder-test-source.svg")
        assertCustomFolderIcon("src/main/resources", "folders/folder-resources.svg")
        assertCustomFolderIcon("src/test/resources", "folders/folder-test-resources.svg")
        assertFolderIcon("other/main", "folders/folder.svg")
    }

    fun testSourceAndPackageFoldersFollowRootModelChanges() {
        val source = myFixture.tempDirFixture.findOrCreateDir("module/assets")
        val directory = PsiManager.getInstance(project).findDirectory(source)
        assertNotNull(directory)
        assertIcon("folders/folder-assets.svg", provider.getIcon(directory!!, 0))
        PsiTestUtil.addSourceRoot(module, source)
        assertEquals(source, ProjectFileIndex.getInstance(project).getSourceRootForFile(source))
        assertCustomFolderIcon("module/assets", "folders/folder-source.svg")
        assertCustomFolderIcon("module/assets/com/example", "folders/folder-package.svg")
        // Actual package roles take precedence over generic folder-name mappings.
        assertCustomFolderIcon("module/assets/config", "folders/folder-package.svg")
        PsiTestUtil.removeSourceRoot(module, source)
        assertIcon("folders/folder-assets.svg", provider.getIcon(directory, 0))
    }

    fun testTestAndResourceRootsUseThemedRoles() {
        val testRoot = myFixture.tempDirFixture.findOrCreateDir("module/test-code")
        val resources = myFixture.tempDirFixture.findOrCreateDir("module/data-files")
        val testResources = myFixture.tempDirFixture.findOrCreateDir("module/test-data-files")
        PsiTestUtil.addSourceRoot(module, testRoot, JavaSourceRootType.TEST_SOURCE)
        PsiTestUtil.addSourceRoot(module, resources, JavaResourceRootType.RESOURCE)
        PsiTestUtil.addSourceRoot(module, testResources, JavaResourceRootType.TEST_RESOURCE)
        assertCustomFolderIcon("module/test-code", "folders/folder-test-source.svg")
        assertCustomFolderIcon("module/test-code/com/example", "folders/folder-test-package.svg")
        assertCustomFolderIcon("module/data-files", "folders/folder-resources.svg")
        assertFolderIcon("module/data-files/assets", "folders/folder-assets.svg")
        assertCustomFolderIcon("module/test-data-files", "folders/folder-test-resources.svg")
    }

    fun testRootModelOverridesConventionalPathColors() {
        val source = myFixture.tempDirFixture.findOrCreateDir("src/test/java")
        assertCustomFolderIcon("src/test/java", "folders/folder-test-source.svg")
        PsiTestUtil.addSourceRoot(module, source, JavaSourceRootType.SOURCE)
        assertCustomFolderIcon("src/test/java", "folders/folder-source.svg")
        PsiTestUtil.removeSourceRoot(module, source)
        PsiTestUtil.addSourceRoot(module, source, JavaSourceRootType.TEST_SOURCE)
        assertCustomFolderIcon("src/test/java", "folders/folder-test-source.svg")
        assertCustomFolderIcon("src/test/java/com/example", "folders/folder-test-package.svg")
    }

    fun testModuleContentRootUsesThemedSquareBadge() {
        val content = myFixture.tempDirFixture.findOrCreateDir("module-root")
        PsiTestUtil.addContentRoot(module, content)
        assertEquals(content, ProjectFileIndex.getInstance(project).getContentRootForFile(content))
        assertCustomFolderIcon("module-root", "folders/folder-content.svg")
    }

    fun testGeneratedRootsRetainSpecializedNativeBadges() {
        val source = myFixture.tempDirFixture.findOrCreateDir("module/codegen-output")
        val sourceProperties = JavaSourceRootType.SOURCE.createDefaultProperties()
        sourceProperties.isForGeneratedSources = true
        PsiTestUtil.addSourceRoot(module, source, JavaSourceRootType.SOURCE, sourceProperties)
        assertNativeFolder("module/codegen-output")
        assertNativeFolder("module/codegen-output/com/example")

        val resources = myFixture.tempDirFixture.findOrCreateDir("module/resource-output")
        val resourceProperties = JavaResourceRootType.RESOURCE.createDefaultProperties()
        resourceProperties.isForGeneratedSources = true
        PsiTestUtil.addSourceRoot(module, resources, JavaResourceRootType.RESOURCE, resourceProperties)
        assertNativeFolder("module/resource-output")
    }

    fun testExcludedFoldersUseNativeIcons() {
        val output = myFixture.tempDirFixture.findOrCreateDir("module/output-cache")
        PsiTestUtil.addExcludedRoot(module, output)
        assertTrue(ProjectFileIndex.getInstance(project).isExcluded(output))
        assertNativeFolder("module/output-cache")
        assertNativeFolder("module/output-cache/assets")
    }

    fun testProjectlessFolderLookup() {
        val idea = myFixture.tempDirFixture.findOrCreateDir(".idea")
        val main = myFixture.tempDirFixture.findOrCreateDir("src/main")
        val maven = myFixture.tempDirFixture.findOrCreateDir(".mvn")
        assertNull(provider.getIcon(idea, 0, null))
        assertResourceIcon("emberkai/icons/folders/folder-content.svg", provider.getIcon(main, 0, null))
        val icon = provider.getIcon(maven, 0, null)
        assertIcon("folders/folder-maven.svg", icon)
        assertIconAtIdeSize("symbols/icons/folders/folder-maven.svg", icon!!)
    }

    fun testDevelopmentContainerFiles() {
        assertFileIcon(".devcontainer.json", "files/docker.svg")
        assertPathIcon(".devcontainer/devcontainer.json", "files/docker.svg")
    }

    fun testRenovateConfigurations() {
        for (name in arrayOf(
            "renovate.json", "renovate.json5", "renovate.jsonc",
            ".renovaterc", ".renovaterc.json", ".renovaterc.json5", ".renovaterc.jsonc",
        )) {
            assertFileIcon(name, "files/renovate.svg")
        }
    }

    fun testAdditionalLanguageIcons() {
        assertFileIcon("Component.vue", "files/vue.svg")
        assertFileIcon("index.php", "files/php.svg")
        assertFileIcon("index.phtml", "files/php.svg")
        assertFileIcon("core.clj", "files/clojure.svg")
        assertFileIcon("core.cljs", "files/clojure.svg")
        assertFileIcon("core.cljc", "files/clojure.svg")
        assertFileIcon("app.coffee", "files/coffeescript.svg")
    }

    fun testBuildToolConsistency() {
        assertFileIcon("gradlew.bat", "files/gradle.svg")
        assertFileIcon(".gitlab-ci.yaml", "files/gitlab.svg")
        assertFileIcon(".gitlab-ci.yml", "files/gitlab.svg")
    }

    fun testGradleKotlinScriptsAndCatalogs() {
        assertFileIcon("build.gradle.kts", "files/gradle.svg")
        assertFileIcon("settings.gradle.kts", "files/gradle.svg")
        assertFileIcon("project.java-conventions.gradle.kts", "files/gradle.svg")
        assertFileIcon("BUILD.GRADLE.KTS", "files/gradle.svg")
        assertPathIcon("gradle/libs.versions.toml", "files/gradle.svg")
        assertPathIcon("gradle/tools.versions.toml", "files/gradle.svg")
        assertPathIcon("config/settings.toml", "files/gear.svg")
        assertNativeIcons("build.kts", "class BuildHelper {}")
    }

    fun testGradleScriptDeclarationsKeepNativeTypeIcons() {
        val file = myFixture.configureByText("build.gradle.kts", "class BuildHelper {}")
        assertIcon("files/gradle.svg", provider.getIcon(file, 0))
        assertNull(provider.getIcon(file.findElementAt(0)!!, 0))
    }

    fun testGradleConventionFolders() {
        assertFolderIcon("buildSrc", "folders/folder-gradle.svg")
        assertFolderIcon("build-logic", "folders/folder-gradle.svg")
    }

    fun testSpringBootGradleConfigurations() {
        myFixture.addFileToProject(
            "service/build.gradle.kts",
            "// Build plugins\nplugins { id(\"org.springframework.boot\") version \"4.0.0\" }",
        )
        assertCustomPathIcon("service/src/main/resources/application.yml", "spring.svg")
        val production = myFixture.addFileToProject("service/src/main/resources/application-production.yaml", "server: {}")
        assertEnvironmentBadge("emberkai/icons/spring.svg", EnvironmentVariant.PRODUCTION, provider.getIcon(production, 0))
        assertEnvironmentBadge(
            "emberkai/icons/spring.svg", EnvironmentVariant.PRODUCTION,
            provider.getIcon(production.virtualFile, 0, project),
        )
        assertCustomPathIcon("service/src/test/resources/application-test.properties", "spring.svg")
        assertPathIcon("service/config/other.yml", "files/yaml.svg")
        assertPathIcon("other/application.yml", "files/yaml.svg")
    }

    fun testSpringBootMavenConfigurations() {
        myFixture.addFileToProject(
            "maven-service/pom.xml",
            """
            <project><parent><groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-parent</artifactId></parent></project>
            """.trimIndent(),
        )
        assertCustomPathIcon("maven-service/module/config/application-local.properties", "spring.svg")
        assertCustomPathIcon("maven-service/application.yaml", "spring.svg")
    }

    fun testSpringBootVersionCatalog() {
        myFixture.addFileToProject(
            "catalog-service/gradle/libs.versions.toml",
            """
            # Plugin aliases
            [plugins]
            spring-boot = { id = "org.springframework.boot", version = "4.0.0" }
            """.trimIndent(),
        )
        assertCustomPathIcon("catalog-service/src/main/resources/application.yml", "spring.svg")
    }

    fun testOrdinaryApplicationFilesAndCommentedDependenciesStayGeneric() {
        myFixture.addFileToProject(
            "plain/build.gradle",
            """
            // id 'org.springframework.boot'
            /* implementation 'org.springframework.boot:spring-boot-starter-web' */
            plugins { id 'java' }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "plain/pom.xml",
            """
            <project><!-- <groupId>org.springframework.boot</groupId> --></project>
            """.trimIndent(),
        )
        assertPathIcon("plain/src/main/resources/application.yaml", "files/yaml.svg")
        val properties = myFixture.addFileToProject("plain/application.properties", "name=example")
        assertNull(provider.getIcon(properties, 0))
        assertNull(provider.getIcon(properties.virtualFile, 0, project))
    }

    fun testSpringContextFollowsUnsavedBuildEdits() {
        val build = myFixture.addFileToProject("editable/build.gradle.kts", "plugins { java }")
        val config = myFixture.addFileToProject("editable/application.yml", "server: {}")
        val document = FileDocumentManager.getInstance().getDocument(build.virtualFile)
        assertNotNull(document)
        assertIcon("files/yaml.svg", provider.getIcon(config, 0))
        WriteCommandAction.runWriteCommandAction(project) {
            document!!.setText("plugins { id(\"org.springframework.boot\") }")
        }
        assertResourceIcon("emberkai/icons/spring.svg", provider.getIcon(config, 0))
        WriteCommandAction.runWriteCommandAction(project) { document!!.setText("plugins { java }") }
        assertIcon("files/yaml.svg", provider.getIcon(config, 0))
    }

    fun testSpringContextFollowsBuildCreationAndDeletion() {
        val config = myFixture.addFileToProject("changing/application.yml", "server: {}")
        assertIcon("files/yaml.svg", provider.getIcon(config, 0))
        val build = myFixture.addFileToProject(
            "changing/build.gradle",
            "dependencies { implementation 'org.springframework.boot:spring-boot-starter-web' }",
        )
        assertResourceIcon("emberkai/icons/spring.svg", provider.getIcon(config, 0))
        WriteCommandAction.runWriteCommandAction(project) { build.delete() }
        assertIcon("files/yaml.svg", provider.getIcon(config, 0))
    }

    fun testHelmChartMetadataAndTemplates() {
        val chart = myFixture.addFileToProject(
            "deploy/store/Chart.yaml", "apiVersion: v2\nname: store\nversion: 1.0.0\n",
        )
        assertResourceIcon("emberkai/icons/helm.svg", provider.getIcon(chart, 0))
        assertResourceIcon("emberkai/icons/helm.svg", provider.getIcon(chart.virtualFile, 0, project))
        for (path in arrayOf(
            "Chart.lock", "values.yaml",
            "values.schema.json", "templates/deployment.yaml", "templates/nested/service.yml",
            "templates/_helpers.tpl", "templates/NOTES.txt", "crds/resource.yaml",
        )) {
            assertCustomPathIcon("deploy/store/$path", "helm.svg")
        }
        val productionValues = myFixture.addFileToProject("deploy/store/values-prod.yml", "replicas: 2")
        assertEnvironmentBadge(
            "emberkai/icons/helm.svg", EnvironmentVariant.PRODUCTION, provider.getIcon(productionValues, 0),
        )
        assertEnvironmentBadge(
            "emberkai/icons/helm.svg", EnvironmentVariant.PRODUCTION,
            provider.getIcon(productionValues.virtualFile, 0, project),
        )
        assertPathIcon("deploy/store/docs/values.yaml", "files/yaml.svg")
        assertPathIcon("deploy/store/templates/README.md", "files/readme.svg")
        assertPathIcon("deploy/store/config/config.yml", "files/yaml.svg")
    }

    fun testHelmFoldersRequireChartContext() {
        myFixture.addFileToProject("deploy/chart/Chart.yaml", "apiVersion: v2")
        assertCustomFolderIcon("deploy/chart", "folder-helm.svg")
        assertCustomFolderIcon("deploy/chart/templates", "folder-helm.svg")
        assertCustomFolderIcon("deploy/chart/charts", "folder-helm.svg")
        assertCustomFolderIcon("deploy/chart/crds", "folder-helm.svg")
        assertFolderIcon("other/templates", "folders/folder.svg")
        assertPathIcon("other/values.yaml", "files/yaml.svg")
        assertPathIcon("other/templates/deployment.yaml", "files/yaml.svg")
    }

    fun testHelmContextFollowsChartCreationAndDeletion() {
        val config = myFixture.addFileToProject("helm-changing/values.yaml", "replicas: 1")
        assertIcon("files/yaml.svg", provider.getIcon(config, 0))
        val chart = myFixture.addFileToProject("helm-changing/Chart.yaml", "apiVersion: v2")
        assertResourceIcon("emberkai/icons/helm.svg", provider.getIcon(config, 0))
        WriteCommandAction.runWriteCommandAction(project) { chart.delete() }
        assertIcon("files/yaml.svg", provider.getIcon(config, 0))
    }

    fun testSpecificWorkflowContextWinsOverSpringContext() {
        myFixture.addFileToProject("boot/build.gradle.kts", "plugins { id(\"org.springframework.boot\") }")
        assertPathIcon("boot/.github/workflows/application.yml", "files/github.svg")
        myFixture.addFileToProject("boot/deploy/Chart.yaml", "apiVersion: v2")
        assertCustomPathIcon("boot/deploy/templates/application.yml", "helm.svg")
    }

    fun testCustomContextIconsRenderAtIdeSize() {
        for (name in arrayOf("spring.svg", "helm.svg", "folder-helm.svg")) {
            val path = "emberkai/icons/$name"
            val icon = IconLoader.getIcon("/$path", SymbolsIconProvider::class.java)
            assertIconAtIdeSize(path, icon)
        }
    }

    fun testThemedFolderIconsRenderAtIdeSize() {
        for (name in arrayOf(
            "content", "source", "test-source", "resources", "test-resources", "package", "test-package",
        )) {
            val path = "emberkai/icons/folders/folder-$name.svg"
            val icon = IconLoader.getIcon("/$path", SymbolsIconProvider::class.java)
            assertIconAtIdeSize(path, icon)
        }
    }

    fun testMavenFilesUseSymbolsIcon() {
        for (path in arrayOf(
            "pom.xml", "nested/pom.xml", "POM.XML", "mvnw", "mvnw.cmd",
            "artifact.pom", ".mvn/maven.config", ".mvn/jvm.config", ".mvn/extensions.xml",
            ".mvn/wrapper/maven-wrapper.properties",
        )) {
            val file = myFixture.addFileToProject(path, "data")
            assertResourceIcon("symbols/icons/files/maven.svg", provider.getIcon(file, 0))
            assertResourceIcon("symbols/icons/files/maven.svg", provider.getIcon(file.virtualFile, 0, project))
        }
        assertPathIcon("config/extensions.xml", "files/xml.svg")
    }

    fun testRegisteredFileIconProviderHandlesPomWithoutPsiOrProject() {
        val registered = FileIconProvider.EP_NAME.extensionList.stream()
            .filter { extension -> extension is SymbolsIconProvider }
            .findFirst()
            .orElseThrow()
        val file = myFixture.addFileToProject("pom.xml", "<project/>").virtualFile
        val icon = registered.getIcon(file, 0, null)
        assertResourceIcon("symbols/icons/files/maven.svg", icon)
        assertNotNull(icon)
        // Force SVG loading: a resource that exists but cannot render is not enough.
        assertIconAtIdeSize("symbols/icons/files/maven.svg", icon!!)
    }

    fun testEveryMappingHasAnIconResource() {
        val icons = ResourceBundle.getBundle(
            "symbols.icons", Locale.ROOT, SymbolsIconProvider::class.java.classLoader,
        )
        for (key in icons.keys) {
            val resource = "/symbols/icons/" + icons.getString(key)
            assertNotNull(resource, SymbolsIconProvider::class.java.getResource(resource))
        }
        val overrides = ResourceBundle.getBundle(
            "emberkai.icons", Locale.ROOT, SymbolsIconProvider::class.java.classLoader,
        )
        for (key in overrides.keys) {
            val resource = overrides.getString(key)
            assertNotNull(key + " -> " + resource, SymbolsIconProvider::class.java.getResource(resource))
        }
    }
}
