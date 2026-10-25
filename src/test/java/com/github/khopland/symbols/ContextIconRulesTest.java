package com.github.khopland.symbols;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.awt.image.BufferedImage;
import java.io.IOException;

public final class ContextIconRulesTest extends IconProviderTestCase {
    public void testFlywayVersionedAndRepeatableMigrations() {
        for (String name : new String[]{"db/migration/V1__create_tables.sql", "sql/V2_1__add_index.sql",
                "V2026.10.25.1__changes.sql", "R__refresh_views.sql", "V3__UPPER.SQL"}) {
            assertCustomPathIcon(name, "migration.svg");
        }
        assertPathIcon("queries/find_users.sql", "files/database.svg");
        assertPathIcon("Vnext__create.sql", "files/database.svg");
        assertPathIcon("V1_create.sql", "files/database.svg");
    }

    public void testMigrationConfigsAndFolders() throws IOException {
        assertCustomPathIcon("flyway.conf", "migration.svg");
        assertCustomPathIcon("config/flyway.toml", "migration.svg");
        assertCustomFolderIcon("db/migration", "folders/folder-migration.svg");
        assertCustomFolderIcon("service/migrations", "folders/folder-migration.svg");
    }

    public void testCanonicalApiContractNames() {
        for (String name : new String[]{"openapi.yaml", "openapi.yml", "openapi.json",
                "swagger.yaml", "swagger.yml", "SWAGGER.JSON"}) {
            assertCustomPathIcon("contracts/" + name, "openapi.svg");
        }
    }

    public void testApiContractsWithCustomNames() {
        assertContentIcon("store.yaml", "openapi: 3.1.1\ninfo: {title: Store}", "emberkai/icons/openapi.svg");
        assertContentIcon("old.yml", "swagger: '2.0'\ninfo: {}", "emberkai/icons/openapi.svg");
        assertContentIcon("contract.json", "{\"openapi\":\"3.0.3\",\"paths\":{}}", "emberkai/icons/openapi.svg");
    }

    public void testApiFoldersFollowMarkerCreationAndDeletion() throws IOException {
        assertFolderIcon("contracts", "folders/folder.svg");
        var spec = myFixture.addFileToProject("contracts/openapi.yaml", "");
        assertCustomFolderIcon("contracts", "folders/folder-api.svg");
        WriteCommandAction.runWriteCommandAction(getProject(), spec::delete);
        assertFolderIcon("contracts", "folders/folder.svg");
        for (String name : new String[]{"api", "openapi", "swagger"}) {
            assertCustomFolderIcon(name, "folders/folder-api.svg");
        }
    }

    public void testKubernetesMetadataInYamlAndJson() {
        assertContentIcon("deployment.yaml", "apiVersion: apps/v1\nkind: Deployment\nmetadata: {}",
                "emberkai/icons/kubernetes.svg");
        assertContentIcon("service.json", "{\"apiVersion\":\"v1\",\"kind\":\"Service\"}",
                "emberkai/icons/kubernetes.svg");
        assertContentIcon("custom.yml", "apiVersion: example.com/v1alpha1\nkind: Widget",
                "emberkai/icons/kubernetes.svg");
        assertContentIcon("bundle.yaml", "# metadata first\n---\n{}\n---\nkind: ConfigMap\napiVersion: v1",
                "emberkai/icons/kubernetes.svg");
    }

    public void testKustomizeNamesAndMetadata() {
        for (String name : new String[]{"kustomization.yaml", "kustomization.yml", "Kustomization"}) {
            assertCustomPathIcon("deployment/" + name, "kustomize.svg");
        }
        assertContentIcon("custom-build.yaml", "apiVersion: kustomize.config.k8s.io/v1beta1\nkind: Kustomization",
                "emberkai/icons/kustomize.svg");
    }

    public void testKustomizeFoldersRequireTheirTreeContext() throws IOException {
        assertFolderIcon("deploy", "folders/folder.svg");
        assertFolderIcon("deploy/overlays", "folders/folder.svg");
        var marker = myFixture.addFileToProject("deploy/Kustomization", "resources: []");
        assertCustomFolderIcon("deploy", "folders/folder-kustomize.svg");
        for (String name : new String[]{"base", "bases", "overlays", "components"}) {
            assertCustomFolderIcon("deploy/" + name, "folders/folder-kustomize.svg");
        }
        WriteCommandAction.runWriteCommandAction(getProject(), marker::delete);
        assertFolderIcon("deploy/overlays", "folders/folder.svg");
        assertCustomFolderIcon("k8s", "folders/folder-kubernetes.svg");
        assertCustomFolderIcon("kubernetes", "folders/folder-kubernetes.svg");
    }

    public void testNestedCommentsAndStringsDoNotLookLikeRootMetadata() {
        String[] ordinaryYaml = {
                "metadata:\n  apiVersion: v1\n  kind: Service",
                "message: |\n  apiVersion: v1\n  kind: Service",
                "# apiVersion: v1\n# kind: Service\nname: example",
                "kind: Service\n---\napiVersion: v1",
                "nested: {openapi: 3.1.0}", "openapi: false", "swagger: 3.0",
                "apiVersion: 1\nkind: Service", "apiVersion: v1\nkind: {}"
        };
        for (int i = 0; i < ordinaryYaml.length; i++) {
            assertContentIcon("ordinary-" + i + ".yaml", ordinaryYaml[i], "symbols/icons/files/yaml.svg");
        }
        assertContentIcon("settings.json", "{\"example\":{\"openapi\":\"3.1.0\"}}",
                "symbols/icons/files/brackets-yellow.svg");
    }

    public void testMalformedAndUnsupportedMetadataUsesFormatIcon() {
        String[] documents = {"openapi: [", "apiVersion: v1\nkind: Service\ninvalid: [",
                "openapi: 3.1.0\nopenapi: {}", "apiVersion: v1\nkind: Service\nkind: Pod",
                "!!example.Widget {apiVersion: v1, kind: Service}",
                "nested: " + "[".repeat(40) + "0" + "]".repeat(40)};
        for (int i = 0; i < documents.length; i++) {
            assertContentIcon("incomplete-" + i + ".yaml", documents[i], "symbols/icons/files/yaml.svg");
        }
    }

    public void testLargeMetadataFilesKeepTheirFormatIcon() {
        assertContentIcon("large.yaml", "apiVersion: v1\nkind: ConfigMap\ndata: " + "x".repeat(FileContentCache.MAX_FILE_SIZE),
                "symbols/icons/files/yaml.svg");
    }

    public void testStructuredMetadataFollowsUnsavedEdits() {
        var file = myFixture.addFileToProject("editable.yaml", "name: ordinary");
        assertFileResource(file, "symbols/icons/files/yaml.svg");
        var document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
        assertNotNull(document);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("openapi: 3.1.0"));
        assertFileResource(file, "emberkai/icons/openapi.svg");
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("apiVersion: v1\nkind: Service"));
        assertFileResource(file, "emberkai/icons/kubernetes.svg");
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("kind: ["));
        assertFileResource(file, "symbols/icons/files/yaml.svg");
    }

    public void testStructuredMetadataFollowsVirtualFileChanges() throws IOException {
        var file = myFixture.getTempDirFixture().createFile("disk.yaml", "openapi: 3.1.0");
        assertResourceIcon("emberkai/icons/openapi.svg", provider.getIcon(file, 0, getProject()));
        WriteCommandAction.writeCommandAction(getProject()).run(() -> VfsUtil.saveText(file, "apiVersion: v1\nkind: Pod"));
        assertResourceIcon("emberkai/icons/kubernetes.svg", provider.getIcon(file, 0, getProject()));
    }

    public void testExistingWorkflowAndChartContextWins() {
        assertContentIcon(".github/workflows/openapi.yml", "openapi: 3.1.0", "symbols/icons/files/github.svg");
        assertContentIcon(".github/workflows/deploy.yaml", "apiVersion: v1\nkind: Service", "symbols/icons/files/github.svg");
        myFixture.addFileToProject("chart/Chart.yaml", "apiVersion: v2");
        assertContentIcon("chart/templates/service.yaml", "apiVersion: v1\nkind: Service", "emberkai/icons/helm.svg");
        assertContentIcon("chart/templates/openapi.yaml", "openapi: 3.1.0", "emberkai/icons/helm.svg");
    }

    public void testDeveloperToolConfigurations() {
        String[][] configs = {{".sdkmanrc", "sdkman.svg"}, {".pre-commit-config.yaml", "pre-commit.svg"},
                {".pre-commit-hooks.yaml", "pre-commit.svg"}, {"justfile", "just.svg"}, {".justfile", "just.svg"},
                {"mise.toml", "mise.svg"}, {".mise.toml", "mise.svg"}, {"mise.local.toml", "mise.svg"},
                {".mise.local.toml", "mise.svg"}, {"mise.lock", "mise.svg"},
                {".mise/config.toml", "mise.svg"}, {"mise/config.toml", "mise.svg"}};
        for (var config : configs) assertCustomPathIcon("tools/" + config[0], config[1]);
        assertPathIcon("unrelated/config.toml", "files/gear.svg");
    }

    public void testTestAssetsAndSnapshotFiles() throws IOException {
        for (String name : new String[]{"fixtures", "__fixtures__"}) {
            assertCustomFolderIcon("tests/" + name, "folders/folder-fixtures.svg");
        }
        assertCustomFolderIcon("tests/testdata", "folders/folder-testdata.svg");
        for (String name : new String[]{"snapshots", "__snapshots__", "__image_snapshots__"}) {
            assertCustomFolderIcon("tests/" + name, "folders/folder-snapshots.svg");
        }
        assertCustomPathIcon("tests/__snapshots__/button.test.tsx.snap", "snapshot.svg");
        assertPathIcon("tests/fixtures/data.json", "files/brackets-yellow.svg");
        assertPathIcon("tests/__snapshots__/button.png", "files/image.svg");
    }

    public void testPackageRolesWinOverAssetNamesAndResourceAssetsGetBadges() throws IOException {
        var sources = myFixture.getTempDirFixture().findOrCreateDir("module/code");
        PsiTestUtil.addSourceRoot(getModule(), sources);
        assertCustomFolderIcon("module/code/fixtures", "folders/folder-package.svg");
        var resources = myFixture.getTempDirFixture().findOrCreateDir("module/data");
        PsiTestUtil.addSourceRoot(getModule(), resources, JavaResourceRootType.TEST_RESOURCE);
        assertCustomFolderIcon("module/data/testdata", "folders/folder-testdata.svg");
    }

    public void testNewArtworkRendersAtIdeSize() {
        for (String name : new String[]{"migration", "openapi", "kubernetes", "kustomize", "sdkman", "pre-commit", "just",
                "mise", "snapshot", "folders/folder-migration", "folders/folder-api", "folders/folder-kubernetes",
                "folders/folder-kustomize", "folders/folder-fixtures", "folders/folder-testdata", "folders/folder-snapshots"}) {
            var icon = IconLoader.getIcon("/emberkai/icons/" + name + ".svg", SymbolsIconProvider.class);
            assertEquals(name, 16, icon.getIconWidth());
            assertEquals(name, 16, icon.getIconHeight());
            var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try {
                icon.paintIcon(null, graphics, 0, 0);
            } finally {
                graphics.dispose();
            }
            boolean painted = false;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) painted |= (image.getRGB(x, y) >>> 24) != 0;
            assertTrue(name + " should paint visible pixels", painted);
        }
    }

    private void assertContentIcon(String path, String content, String icon) {
        assertFileResource(myFixture.addFileToProject(path, content), icon);
    }

    private void assertFileResource(PsiFile file, String icon) {
        assertResourceIcon(icon, provider.getIcon(file, 0));
        assertResourceIcon(icon, provider.getIcon(file.getVirtualFile(), 0, getProject()));
    }
}
