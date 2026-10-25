package com.github.khopland.symbols;

import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.Icon;
import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;

public final class SymbolsIconProviderTest extends BasePlatformTestCase {
    private final SymbolsIconProvider provider = new SymbolsIconProvider();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Model a checkout root. Individual tests mark real source/resource roots
        // explicitly instead of treating every fixture folder as Java source.
        ModuleRootModificationUtil.updateModel(getModule(), model -> {
            for (var entry : model.getContentEntries()) {
                for (var source : entry.getSourceFolders()) entry.removeSourceFolder(source);
            }
        });
    }

    public void testExactFileNameTakesPriority() {
        assertFileIcon("tsconfig.json", "files/tsconfig.svg");
    }

    public void testCompositeExtensionTakesPriority() {
        assertFileIcon("feature.test.tsx", "files/react-test.svg");
    }

    public void testNamedFolder() throws IOException {
        assertFolderIcon("assets", "folders/folder-assets.svg");
    }

    public void testUnknownFolder() throws IOException {
        assertFolderIcon("random-folder", "folders/folder.svg");
    }

    public void testUnknownFileTypeUsesNativeIcon() {
        var file = myFixture.configureByText("file.unknownext", "data");
        assertNull(provider.getIcon(file, 0));
    }

    public void testJavaFilesAndTheirElementsUseNativeIcons() {
        assertNativeIcons("Example.java", "public class Example { class Nested {} }");
    }

    public void testKotlinFilesAndTheirElementsUseNativeIcons() {
        assertNativeIcons("Example.kt", "data class Example(val name: String)");
    }

    public void testKotlinScriptsUseNativeIcons() {
        assertNativeIcons("example.kts", "println(\"Hello\")");
    }

    public void testCompiledClassesUseNativeIcons() throws Exception {
        var virtualFile = myFixture.getTempDirFixture().createFile("Example.class");
        var file = PsiManager.getInstance(getProject()).findFile(virtualFile);
        assertNotNull(file);
        assertNull(provider.getIcon(file, 0));
    }

    public void testCaseInsensitiveFileNames() {
        assertNativeIcons("Example.JAVA", "class Example {}");
        assertNativeIcons("Example.KT", "class Example");
        assertFileIcon("TSCONFIG.JSON", "files/tsconfig.svg");
    }

    public void testFoldersWithSourceExtensionsStillUseSymbols() throws IOException {
        assertFolderIcon("examples.kt", "folders/folder.svg");
    }

    public void testOtherJavaRelatedFilesStillUseSymbols() {
        assertFileIcon("index.jsp", "files/java.svg");
    }

    public void testGitHubWorkflowsUseTheirPath() {
        assertPathIcon(".github/workflows/build.yml", "files/github.svg");
        assertPathIcon("nested/.github/workflows/release.yaml", "files/github.svg");
        assertPathIcon(".GitHub/Workflows/Check.YAML", "files/github.svg");
        assertPathIcon("config/build.yml", "files/yaml.svg");
        assertPathIcon("workflows/build.yml", "files/yaml.svg");
        assertPathIcon(".github/workflows/nested/config.yml", "files/yaml.svg");
        assertPathIcon(".github/workflows/README.md", "files/readme.svg");
    }

    public void testGitHubMetadata() {
        assertFileIcon("action.yml", "files/github.svg");
        assertFileIcon("action.yaml", "files/github.svg");
        assertFileIcon("CODEOWNERS", "files/github.svg");
        assertPathIcon(".github/dependabot.yml", "files/github.svg");
        assertPathIcon(".github/dependabot.yaml", "files/github.svg");
        assertPathIcon(".github/FUNDING.yml", "files/github.svg");
        assertPathIcon(".github/FUNDING.yaml", "files/github.svg");
        assertPathIcon("other/dependabot.yml", "files/yaml.svg");
    }

    public void testContextualFolders() throws IOException {
        assertFolderIcon(".github/workflows", "folders/folder-github.svg");
        assertFolderIcon(".github/actions", "folders/folder-github.svg");
        assertFolderIcon("workflows", "folders/folder.svg");
        assertFolderIcon(".devcontainer", "folders/folder-docker.svg");
        assertFolderIcon(".mvn", "folders/folder-maven.svg");
    }

    public void testIdeAndJvmLayoutFoldersUseNativeIcons() throws IOException {
        for (String path : new String[]{".idea", ".idea/runConfigurations", ".idea/inspectionProfiles",
                "src", "src/main", "src/test", "src/main/java", "src/main/kotlin",
                "src/main/resources", "src/test/resources", "tests", "generated",
                "generated-sources", "generated-test-sources"}) {
            assertNativeFolder(path);
        }
    }

    public void testSourceAndPackageFoldersFollowRootModelChanges() throws IOException {
        var source = myFixture.getTempDirFixture().findOrCreateDir("module/assets");
        var directory = PsiManager.getInstance(getProject()).findDirectory(source);
        assertNotNull(directory);
        assertIcon("folders/folder-assets.svg", provider.getIcon(directory, 0));
        PsiTestUtil.addSourceRoot(getModule(), source);
        assertEquals(source, ProjectFileIndex.getInstance(getProject()).getSourceRootForFile(source));
        assertNativeFolder("module/assets");
        assertNativeFolder("module/assets/com/example");
        // Even names with Symbols artwork must defer inside actual source trees.
        assertNativeFolder("module/assets/config");
        PsiTestUtil.removeSourceRoot(getModule(), source);
        assertIcon("folders/folder-assets.svg", provider.getIcon(directory, 0));
    }

    public void testTestAndResourceRootsUseNativeIcons() throws IOException {
        var testRoot = myFixture.getTempDirFixture().findOrCreateDir("module/test-code");
        var resources = myFixture.getTempDirFixture().findOrCreateDir("module/data-files");
        var testResources = myFixture.getTempDirFixture().findOrCreateDir("module/test-data-files");
        PsiTestUtil.addSourceRoot(getModule(), testRoot, JavaSourceRootType.TEST_SOURCE);
        PsiTestUtil.addSourceRoot(getModule(), resources, JavaResourceRootType.RESOURCE);
        PsiTestUtil.addSourceRoot(getModule(), testResources, JavaResourceRootType.TEST_RESOURCE);
        assertNativeFolder("module/test-code");
        assertNativeFolder("module/test-code/com/example");
        assertNativeFolder("module/data-files");
        assertNativeFolder("module/data-files/assets");
        assertNativeFolder("module/test-data-files");
    }

    public void testExcludedFoldersUseNativeIcons() throws IOException {
        var output = myFixture.getTempDirFixture().findOrCreateDir("module/output-cache");
        PsiTestUtil.addExcludedRoot(getModule(), output);
        assertTrue(ProjectFileIndex.getInstance(getProject()).isExcluded(output));
        assertNativeFolder("module/output-cache");
        assertNativeFolder("module/output-cache/assets");
    }

    public void testProjectlessFolderLookup() throws IOException {
        var idea = myFixture.getTempDirFixture().findOrCreateDir(".idea");
        var main = myFixture.getTempDirFixture().findOrCreateDir("src/main");
        var maven = myFixture.getTempDirFixture().findOrCreateDir(".mvn");
        assertNull(provider.getIcon(idea, 0, null));
        assertNull(provider.getIcon(main, 0, null));
        Icon icon = provider.getIcon(maven, 0, null);
        assertIcon("folders/folder-maven.svg", icon);
        assertEquals(16, icon.getIconWidth());
        assertEquals(16, icon.getIconHeight());
    }

    public void testDevelopmentContainerFiles() {
        assertFileIcon(".devcontainer.json", "files/docker.svg");
        assertPathIcon(".devcontainer/devcontainer.json", "files/docker.svg");
    }

    public void testRenovateConfigurations() {
        for (String name : new String[]{"renovate.json", "renovate.json5", "renovate.jsonc",
                ".renovaterc", ".renovaterc.json", ".renovaterc.json5", ".renovaterc.jsonc"}) {
            assertFileIcon(name, "files/renovate.svg");
        }
    }

    public void testAdditionalLanguageIcons() {
        assertFileIcon("Component.vue", "files/vue.svg");
        assertFileIcon("index.php", "files/php.svg");
        assertFileIcon("index.phtml", "files/php.svg");
        assertFileIcon("core.clj", "files/clojure.svg");
        assertFileIcon("core.cljs", "files/clojure.svg");
        assertFileIcon("core.cljc", "files/clojure.svg");
        assertFileIcon("app.coffee", "files/coffeescript.svg");
    }

    public void testBuildToolConsistency() {
        assertFileIcon("gradlew.bat", "files/gradle.svg");
        assertFileIcon(".gitlab-ci.yaml", "files/gitlab.svg");
        assertFileIcon(".gitlab-ci.yml", "files/gitlab.svg");
    }

    public void testGradleKotlinScriptsAndCatalogs() {
        assertFileIcon("build.gradle.kts", "files/gradle.svg");
        assertFileIcon("settings.gradle.kts", "files/gradle.svg");
        assertFileIcon("project.java-conventions.gradle.kts", "files/gradle.svg");
        assertFileIcon("BUILD.GRADLE.KTS", "files/gradle.svg");
        assertPathIcon("gradle/libs.versions.toml", "files/gradle.svg");
        assertPathIcon("gradle/tools.versions.toml", "files/gradle.svg");
        assertPathIcon("config/settings.toml", "files/gear.svg");
        assertNativeIcons("build.kts", "class BuildHelper {}");
    }

    public void testGradleScriptDeclarationsKeepNativeTypeIcons() {
        var file = myFixture.configureByText("build.gradle.kts", "class BuildHelper {}");
        assertIcon("files/gradle.svg", provider.getIcon(file, 0));
        assertNull(provider.getIcon(file.findElementAt(0), 0));
    }

    public void testGradleConventionFolders() throws IOException {
        assertFolderIcon("buildSrc", "folders/folder-gradle.svg");
        assertFolderIcon("build-logic", "folders/folder-gradle.svg");
    }

    public void testSpringBootGradleConfigurations() {
        myFixture.addFileToProject("service/build.gradle.kts",
                "// Build plugins\nplugins { id(\"org.springframework.boot\") version \"4.0.0\" }");
        assertCustomPathIcon("service/src/main/resources/application.yml", "spring.svg");
        assertCustomPathIcon("service/src/main/resources/application-production.yaml", "spring.svg");
        assertCustomPathIcon("service/src/test/resources/application-test.properties", "spring.svg");
        assertPathIcon("service/config/other.yml", "files/yaml.svg");
        assertPathIcon("other/application.yml", "files/yaml.svg");
    }

    public void testSpringBootMavenConfigurations() {
        myFixture.addFileToProject("maven-service/pom.xml", """
                <project><parent><groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-parent</artifactId></parent></project>
                """);
        assertCustomPathIcon("maven-service/module/config/application-local.properties", "spring.svg");
        assertCustomPathIcon("maven-service/application.yaml", "spring.svg");
    }

    public void testSpringBootVersionCatalog() {
        myFixture.addFileToProject("catalog-service/gradle/libs.versions.toml", """
                # Plugin aliases
                [plugins]
                spring-boot = { id = "org.springframework.boot", version = "4.0.0" }
                """);
        assertCustomPathIcon("catalog-service/src/main/resources/application.yml", "spring.svg");
    }

    public void testOrdinaryApplicationFilesAndCommentedDependenciesStayGeneric() {
        myFixture.addFileToProject("plain/build.gradle", """
                // id 'org.springframework.boot'
                /* implementation 'org.springframework.boot:spring-boot-starter-web' */
                plugins { id 'java' }
                """);
        myFixture.addFileToProject("plain/pom.xml", """
                <project><!-- <groupId>org.springframework.boot</groupId> --></project>
                """);
        assertPathIcon("plain/src/main/resources/application.yaml", "files/yaml.svg");
        var properties = myFixture.addFileToProject("plain/application.properties", "name=example");
        assertNull(provider.getIcon(properties, 0));
        assertNull(provider.getIcon(properties.getVirtualFile(), 0, getProject()));
    }

    public void testSpringContextFollowsUnsavedBuildEdits() {
        var build = myFixture.addFileToProject("editable/build.gradle.kts", "plugins { java }");
        var config = myFixture.addFileToProject("editable/application.yml", "server: {}");
        var document = FileDocumentManager.getInstance().getDocument(build.getVirtualFile());
        assertNotNull(document);
        assertIcon("files/yaml.svg", provider.getIcon(config, 0));
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
                document.setText("plugins { id(\"org.springframework.boot\") }"));
        assertResourceIcon("emberkai/icons/spring.svg", provider.getIcon(config, 0));
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("plugins { java }"));
        assertIcon("files/yaml.svg", provider.getIcon(config, 0));
    }

    public void testSpringContextFollowsBuildCreationAndDeletion() {
        var config = myFixture.addFileToProject("changing/application.yml", "server: {}");
        assertIcon("files/yaml.svg", provider.getIcon(config, 0));
        var build = myFixture.addFileToProject("changing/build.gradle",
                "dependencies { implementation 'org.springframework.boot:spring-boot-starter-web' }");
        assertResourceIcon("emberkai/icons/spring.svg", provider.getIcon(config, 0));
        WriteCommandAction.runWriteCommandAction(getProject(), build::delete);
        assertIcon("files/yaml.svg", provider.getIcon(config, 0));
    }

    public void testHelmChartMetadataAndTemplates() {
        var chart = myFixture.addFileToProject("deploy/store/Chart.yaml", "apiVersion: v2\nname: store\nversion: 1.0.0\n");
        assertResourceIcon("emberkai/icons/helm.svg", provider.getIcon(chart, 0));
        assertResourceIcon("emberkai/icons/helm.svg", provider.getIcon(chart.getVirtualFile(), 0, getProject()));
        for (String path : new String[]{"Chart.lock", "values.yaml", "values-prod.yml",
                "values.schema.json", "templates/deployment.yaml", "templates/nested/service.yml",
                "templates/_helpers.tpl", "templates/NOTES.txt", "crds/resource.yaml"}) {
            assertCustomPathIcon("deploy/store/" + path, "helm.svg");
        }
        assertPathIcon("deploy/store/docs/values.yaml", "files/yaml.svg");
        assertPathIcon("deploy/store/templates/README.md", "files/readme.svg");
        assertPathIcon("deploy/store/config/config.yml", "files/yaml.svg");
    }

    public void testHelmFoldersRequireChartContext() throws IOException {
        myFixture.addFileToProject("deploy/chart/Chart.yaml", "apiVersion: v2");
        assertCustomFolderIcon("deploy/chart", "folder-helm.svg");
        assertCustomFolderIcon("deploy/chart/templates", "folder-helm.svg");
        assertCustomFolderIcon("deploy/chart/charts", "folder-helm.svg");
        assertCustomFolderIcon("deploy/chart/crds", "folder-helm.svg");
        assertFolderIcon("other/templates", "folders/folder.svg");
        assertPathIcon("other/values.yaml", "files/yaml.svg");
        assertPathIcon("other/templates/deployment.yaml", "files/yaml.svg");
    }

    public void testHelmContextFollowsChartCreationAndDeletion() {
        var config = myFixture.addFileToProject("helm-changing/values.yaml", "replicas: 1");
        assertIcon("files/yaml.svg", provider.getIcon(config, 0));
        var chart = myFixture.addFileToProject("helm-changing/Chart.yaml", "apiVersion: v2");
        assertResourceIcon("emberkai/icons/helm.svg", provider.getIcon(config, 0));
        WriteCommandAction.runWriteCommandAction(getProject(), chart::delete);
        assertIcon("files/yaml.svg", provider.getIcon(config, 0));
    }

    public void testSpecificWorkflowContextWinsOverSpringContext() {
        myFixture.addFileToProject("boot/build.gradle.kts", "plugins { id(\"org.springframework.boot\") }");
        assertPathIcon("boot/.github/workflows/application.yml", "files/github.svg");
        myFixture.addFileToProject("boot/deploy/Chart.yaml", "apiVersion: v2");
        assertCustomPathIcon("boot/deploy/templates/application.yml", "helm.svg");
    }

    public void testCustomContextIconsRenderAtIdeSize() {
        for (String name : new String[]{"spring.svg", "helm.svg", "folder-helm.svg"}) {
            Icon icon = IconLoader.getIcon("/emberkai/icons/" + name, SymbolsIconProvider.class);
            assertEquals(name, 16, icon.getIconWidth());
            assertEquals(name, 16, icon.getIconHeight());
        }
    }

    public void testMavenFilesUseCustomIcon() {
        for (String path : new String[]{"pom.xml", "nested/pom.xml", "POM.XML", "mvnw", "mvnw.cmd",
                "artifact.pom", ".mvn/maven.config", ".mvn/jvm.config", ".mvn/extensions.xml",
                ".mvn/wrapper/maven-wrapper.properties"}) {
            var file = myFixture.addFileToProject(path, "data");
            assertResourceIcon("emberkai/icons/maven.svg", provider.getIcon(file, 0));
            assertResourceIcon("emberkai/icons/maven.svg", provider.getIcon(file.getVirtualFile(), 0, getProject()));
        }
        assertPathIcon("config/extensions.xml", "files/xml.svg");
    }

    public void testRegisteredFileIconProviderHandlesPomWithoutPsiOrProject() {
        FileIconProvider registered = FileIconProvider.EP_NAME.getExtensionList().stream()
                .filter(extension -> extension instanceof SymbolsIconProvider).findFirst().orElseThrow();
        var file = myFixture.addFileToProject("pom.xml", "<project/>").getVirtualFile();
        Icon icon = registered.getIcon(file, 0, null);
        assertResourceIcon("emberkai/icons/maven.svg", icon);
        // Force SVG loading: a resource that exists but cannot render is not enough.
        assertEquals(16, icon.getIconWidth());
        assertEquals(16, icon.getIconHeight());
    }

    public void testEveryMappingHasAnIconResource() {
        var icons = ResourceBundle.getBundle("symbols.icons", Locale.ROOT,
                SymbolsIconProvider.class.getClassLoader());
        for (String key : icons.keySet()) {
            String resource = "/symbols/icons/" + icons.getString(key);
            assertNotNull(resource, SymbolsIconProvider.class.getResource(resource));
        }
        var overrides = ResourceBundle.getBundle("emberkai.icons", Locale.ROOT,
                SymbolsIconProvider.class.getClassLoader());
        for (String key : overrides.keySet()) {
            String resource = overrides.getString(key);
            assertNotNull(key + " -> " + resource, SymbolsIconProvider.class.getResource(resource));
        }
    }

    private void assertFileIcon(String name, String path) {
        var file = myFixture.configureByText(name, "data");
        assertIcon(path, provider.getIcon(file, 0));
        assertIcon(path, provider.getIcon(file.getVirtualFile(), 0, getProject()));
    }

    private void assertPathIcon(String name, String path) {
        var file = myFixture.addFileToProject(name, "data");
        assertIcon(path, provider.getIcon(file, 0));
        assertIcon(path, provider.getIcon(file.getVirtualFile(), 0, getProject()));
    }

    private void assertCustomPathIcon(String name, String icon) {
        var file = myFixture.addFileToProject(name, "data");
        assertResourceIcon("emberkai/icons/" + icon, provider.getIcon(file, 0));
        assertResourceIcon("emberkai/icons/" + icon, provider.getIcon(file.getVirtualFile(), 0, getProject()));
    }

    private void assertCustomFolderIcon(String name, String icon) throws IOException {
        var file = myFixture.getTempDirFixture().findOrCreateDir(name);
        var directory = PsiManager.getInstance(getProject()).findDirectory(file);
        assertNotNull(directory);
        assertResourceIcon("emberkai/icons/" + icon, provider.getIcon(directory, 0));
        assertResourceIcon("emberkai/icons/" + icon, provider.getIcon(file, 0, getProject()));
    }

    private void assertFolderIcon(String name, String path) throws IOException {
        var virtualFile = myFixture.getTempDirFixture().findOrCreateDir(name);
        var directory = PsiManager.getInstance(getProject()).findDirectory(virtualFile);
        assertNotNull(directory);
        assertIcon(path, provider.getIcon(directory, 0));
        assertIcon(path, provider.getIcon(virtualFile, 0, getProject()));
    }

    private void assertNativeFolder(String name) throws IOException {
        var file = myFixture.getTempDirFixture().findOrCreateDir(name);
        var directory = PsiManager.getInstance(getProject()).findDirectory(file);
        assertNotNull(directory);
        for (int flags : new int[]{0, Iconable.ICON_FLAG_READ_STATUS}) {
            assertNull(name, provider.getIcon(directory, flags));
            assertNull(name, provider.getIcon(file, flags, getProject()));
        }
    }

    private void assertIcon(String path, Icon actual) {
        assertResourceIcon("symbols/icons/" + path, actual);
    }

    private void assertResourceIcon(String path, Icon actual) {
        assertNotNull(actual);
        assertSame(IconLoader.getIcon("/" + path, SymbolsIconProvider.class), actual);
    }

    private void assertNativeIcons(String name, String content) {
        var file = myFixture.configureByText(name, content);
        var element = file.findElementAt(Math.max(0, content.indexOf("class")));
        assertNotNull(element);
        for (int flags : new int[]{0, Iconable.ICON_FLAG_VISIBILITY, Iconable.ICON_FLAG_READ_STATUS}) {
            assertNull(name, provider.getIcon(file, flags));
            assertNull(name, provider.getIcon(element, flags));
            assertNull(name, provider.getIcon(file.getVirtualFile(), flags, getProject()));
        }
    }
}
