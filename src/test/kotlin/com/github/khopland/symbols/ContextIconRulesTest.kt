package com.github.khopland.symbols

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import java.awt.image.BufferedImage

internal class ContextIconRulesTest : IconProviderTestCase() {
    fun testFlywayVersionedAndRepeatableMigrations() {
        for (name in arrayOf(
            "db/migration/V1__create_tables.sql", "sql/V2_1__add_index.sql",
            "V2026.10.25.1__changes.sql", "R__refresh_views.sql", "V3__UPPER.SQL",
        )) {
            assertCustomPathIcon(name, "migration.svg")
        }
        assertPathIcon("queries/find_users.sql", "files/database.svg")
        assertPathIcon("Vnext__create.sql", "files/database.svg")
        assertPathIcon("V1_create.sql", "files/database.svg")
    }

    fun testMigrationConfigsAndFolders() {
        assertCustomPathIcon("flyway.conf", "migration.svg")
        assertCustomPathIcon("config/flyway.toml", "migration.svg")
        assertCustomFolderIcon("db/migration", "folders/folder-migration.svg")
        assertCustomFolderIcon("service/migrations", "folders/folder-migration.svg")
    }

    fun testCanonicalApiContractNames() {
        for (name in arrayOf(
            "openapi.yaml", "openapi.yml", "openapi.json", "swagger.yaml", "swagger.yml", "SWAGGER.JSON",
        )) {
            assertCustomPathIcon("contracts/$name", "openapi.svg")
        }
    }

    fun testApiContractsWithCustomNames() {
        assertContentIcon("store.yaml", "openapi: 3.1.1\ninfo: {title: Store}", "emberkai/icons/openapi.svg")
        assertContentIcon("old.yml", "swagger: '2.0'\ninfo: {}", "emberkai/icons/openapi.svg")
        assertContentIcon("contract.json", "{\"openapi\":\"3.0.3\",\"paths\":{}}", "emberkai/icons/openapi.svg")
    }

    fun testApiFoldersFollowMarkerCreationAndDeletion() {
        assertFolderIcon("contracts", "folders/folder.svg")
        val spec = myFixture.addFileToProject("contracts/openapi.yaml", "")
        assertCustomFolderIcon("contracts", "folders/folder-api.svg")
        WriteCommandAction.runWriteCommandAction(project) { spec.delete() }
        assertFolderIcon("contracts", "folders/folder.svg")
        for (name in arrayOf("api", "openapi", "swagger")) {
            assertCustomFolderIcon(name, "folders/folder-api.svg")
        }
    }

    fun testKubernetesMetadataInYamlAndJson() {
        assertContentIcon(
            "deployment.yaml", "apiVersion: apps/v1\nkind: Deployment\nmetadata: {}",
            "emberkai/icons/kubernetes.svg",
        )
        assertContentIcon(
            "service.json", "{\"apiVersion\":\"v1\",\"kind\":\"Service\"}",
            "emberkai/icons/kubernetes.svg",
        )
        assertContentIcon(
            "custom.yml", "apiVersion: example.com/v1alpha1\nkind: Widget",
            "emberkai/icons/kubernetes.svg",
        )
        assertContentIcon(
            "bundle.yaml", "# metadata first\n---\n{}\n---\nkind: ConfigMap\napiVersion: v1",
            "emberkai/icons/kubernetes.svg",
        )
    }

    fun testKustomizeNamesAndMetadata() {
        for (name in arrayOf("kustomization.yaml", "kustomization.yml", "Kustomization")) {
            assertCustomPathIcon("deployment/$name", "kustomize.svg")
        }
        assertContentIcon(
            "custom-build.yaml", "apiVersion: kustomize.config.k8s.io/v1beta1\nkind: Kustomization",
            "emberkai/icons/kustomize.svg",
        )
    }

    fun testKustomizeFoldersRequireTheirTreeContext() {
        assertFolderIcon("deploy", "folders/folder.svg")
        assertFolderIcon("deploy/overlays", "folders/folder.svg")
        val marker = myFixture.addFileToProject("deploy/Kustomization", "resources: []")
        assertCustomFolderIcon("deploy", "folders/folder-kustomize.svg")
        for (name in arrayOf("base", "bases", "overlays", "components")) {
            assertCustomFolderIcon("deploy/$name", "folders/folder-kustomize.svg")
        }
        WriteCommandAction.runWriteCommandAction(project) { marker.delete() }
        assertFolderIcon("deploy/overlays", "folders/folder.svg")
        assertCustomFolderIcon("k8s", "folders/folder-kubernetes.svg")
        assertCustomFolderIcon("kubernetes", "folders/folder-kubernetes.svg")
    }

    fun testNestedCommentsAndStringsDoNotLookLikeRootMetadata() {
        val ordinaryYaml = arrayOf(
            "metadata:\n  apiVersion: v1\n  kind: Service",
            "message: |\n  apiVersion: v1\n  kind: Service",
            "# apiVersion: v1\n# kind: Service\nname: example",
            "kind: Service\n---\napiVersion: v1",
            "nested: {openapi: 3.1.0}", "openapi: false", "swagger: 3.0",
            "apiVersion: 1\nkind: Service", "apiVersion: v1\nkind: {}",
        )
        for (i in ordinaryYaml.indices) {
            assertContentIcon("ordinary-$i.yaml", ordinaryYaml[i], "symbols/icons/files/yaml.svg")
        }
        assertContentIcon(
            "settings.json", "{\"example\":{\"openapi\":\"3.1.0\"}}",
            "symbols/icons/files/brackets-yellow.svg",
        )
    }

    fun testMalformedAndUnsupportedMetadataUsesFormatIcon() {
        val documents = arrayOf(
            "openapi: [", "apiVersion: v1\nkind: Service\ninvalid: [",
            "openapi: 3.1.0\nopenapi: {}", "apiVersion: v1\nkind: Service\nkind: Pod",
            "!!example.Widget {apiVersion: v1, kind: Service}",
            "nested: " + "[".repeat(40) + "0" + "]".repeat(40),
        )
        for (i in documents.indices) {
            assertContentIcon("incomplete-$i.yaml", documents[i], "symbols/icons/files/yaml.svg")
        }
    }

    fun testLargeMetadataFilesKeepTheirFormatIcon() {
        assertContentIcon(
            "large.yaml",
            "apiVersion: v1\nkind: ConfigMap\ndata: " + "x".repeat(FileContentCache.MAX_FILE_SIZE),
            "symbols/icons/files/yaml.svg",
        )
    }

    fun testStructuredMetadataFollowsUnsavedEdits() {
        val file = myFixture.addFileToProject("editable.yaml", "name: ordinary")
        assertFileResource(file, "symbols/icons/files/yaml.svg")
        val document = FileDocumentManager.getInstance().getDocument(file.virtualFile)
        assertNotNull(document)
        WriteCommandAction.runWriteCommandAction(project) { document!!.setText("openapi: 3.1.0") }
        assertFileResource(file, "emberkai/icons/openapi.svg")
        WriteCommandAction.runWriteCommandAction(project) {
            document!!.setText("apiVersion: v1\nkind: Service")
        }
        assertFileResource(file, "emberkai/icons/kubernetes.svg")
        WriteCommandAction.runWriteCommandAction(project) { document!!.setText("kind: [") }
        assertFileResource(file, "symbols/icons/files/yaml.svg")
    }

    fun testStructuredMetadataFollowsVirtualFileChanges() {
        val file = myFixture.tempDirFixture.createFile("disk.yaml", "openapi: 3.1.0")
        assertResourceIcon("emberkai/icons/openapi.svg", provider.getIcon(file, 0, project))
        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(file, "apiVersion: v1\nkind: Pod")
        }
        assertResourceIcon("emberkai/icons/kubernetes.svg", provider.getIcon(file, 0, project))
    }

    fun testExistingWorkflowAndChartContextWins() {
        assertContentIcon(".github/workflows/openapi.yml", "openapi: 3.1.0", "symbols/icons/files/github.svg")
        assertContentIcon(
            ".github/workflows/deploy.yaml", "apiVersion: v1\nkind: Service", "symbols/icons/files/github.svg",
        )
        myFixture.addFileToProject("chart/Chart.yaml", "apiVersion: v2")
        assertContentIcon(
            "chart/templates/service.yaml", "apiVersion: v1\nkind: Service", "emberkai/icons/helm.svg",
        )
        assertContentIcon("chart/templates/openapi.yaml", "openapi: 3.1.0", "emberkai/icons/helm.svg")
    }

    fun testDeveloperToolConfigurations() {
        val configs = arrayOf(
            arrayOf(".sdkmanrc", "sdkman.svg"), arrayOf(".pre-commit-config.yaml", "pre-commit.svg"),
            arrayOf(".pre-commit-hooks.yaml", "pre-commit.svg"), arrayOf("justfile", "just.svg"),
            arrayOf(".justfile", "just.svg"), arrayOf("mise.toml", "mise.svg"),
            arrayOf(".mise.toml", "mise.svg"), arrayOf("mise.local.toml", "mise.svg"),
            arrayOf(".mise.local.toml", "mise.svg"), arrayOf("mise.lock", "mise.svg"),
            arrayOf(".mise/config.toml", "mise.svg"), arrayOf("mise/config.toml", "mise.svg"),
        )
        for (config in configs) assertCustomPathIcon("tools/${config[0]}", config[1])
        assertPathIcon("unrelated/config.toml", "files/gear.svg")
    }

    fun testTestAssetsAndSnapshotFiles() {
        for (name in arrayOf("fixtures", "__fixtures__")) {
            assertCustomFolderIcon("tests/$name", "folders/folder-fixtures.svg")
        }
        assertCustomFolderIcon("tests/testdata", "folders/folder-testdata.svg")
        for (name in arrayOf("snapshots", "__snapshots__", "__image_snapshots__")) {
            assertCustomFolderIcon("tests/$name", "folders/folder-snapshots.svg")
        }
        assertCustomPathIcon("tests/__snapshots__/button.test.tsx.snap", "snapshot.svg")
        assertPathIcon("tests/fixtures/data.json", "files/brackets-yellow.svg")
        assertPathIcon("tests/__snapshots__/button.png", "files/image.svg")
    }

    fun testPackageRolesWinOverAssetNamesAndResourceAssetsGetBadges() {
        val sources = myFixture.tempDirFixture.findOrCreateDir("module/code")
        PsiTestUtil.addSourceRoot(module, sources)
        assertCustomFolderIcon("module/code/fixtures", "folders/folder-package.svg")
        val resources = myFixture.tempDirFixture.findOrCreateDir("module/data")
        PsiTestUtil.addSourceRoot(module, resources, JavaResourceRootType.TEST_RESOURCE)
        assertCustomFolderIcon("module/data/testdata", "folders/folder-testdata.svg")
    }

    fun testNewArtworkRendersAtIdeSize() {
        for (name in arrayOf(
            "migration", "openapi", "kubernetes", "kustomize", "sdkman", "pre-commit", "just",
            "mise", "snapshot", "folders/folder-migration", "folders/folder-api", "folders/folder-kubernetes",
            "folders/folder-kustomize", "folders/folder-fixtures", "folders/folder-testdata",
            "folders/folder-snapshots",
        )) {
            val icon = IconLoader.getIcon("/emberkai/icons/$name.svg", SymbolsIconProvider::class.java)
            assertEquals(name, 16, icon.iconWidth)
            assertEquals(name, 16, icon.iconHeight)
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try {
                icon.paintIcon(null, graphics, 0, 0)
            } finally {
                graphics.dispose()
            }
            var painted = false
            for (x in 0 until 16) for (y in 0 until 16) {
                painted = painted || (image.getRGB(x, y) ushr 24) != 0
            }
            assertTrue("$name should paint visible pixels", painted)
        }
    }

    private fun assertContentIcon(path: String, content: String, icon: String) {
        assertFileResource(myFixture.addFileToProject(path, content), icon)
    }

    private fun assertFileResource(file: PsiFile, icon: String) {
        assertResourceIcon(icon, provider.getIcon(file, 0))
        assertResourceIcon(icon, provider.getIcon(file.virtualFile, 0, project))
    }
}
