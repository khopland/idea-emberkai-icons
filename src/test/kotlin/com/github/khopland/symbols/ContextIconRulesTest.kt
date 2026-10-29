package com.github.khopland.symbols

import com.github.khopland.symbols.core.FileContentCache
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.SymbolsIconProvider
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import org.jetbrains.jps.model.java.JavaResourceRootType

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
            "emberkai/icons/kubernetes/deployment.svg",
        )
        assertContentIcon(
            "service.json", "{\"apiVersion\":\"v1\",\"kind\":\"Service\"}",
            "emberkai/icons/kubernetes/service.svg",
        )
        assertContentIcon(
            "custom.yml", "apiVersion: example.com/v1alpha1\nkind: Widget",
            "emberkai/icons/kubernetes.svg",
        )
        assertContentIcon(
            "bundle.yaml", "# metadata first\n---\n{}\n---\nkind: ConfigMap\napiVersion: v1",
            "emberkai/icons/kubernetes/configmap.svg",
        )
    }

    fun testKubernetesResourceKinds() {
        val resources = arrayOf(
            "apps/v1" to "StatefulSet", "apps/v1" to "DaemonSet", "networking.k8s.io/v1" to "Ingress",
            "v1" to "Secret", "batch/v1" to "Job", "batch/v1" to "CronJob",
            "apiextensions.k8s.io/v1" to "CustomResourceDefinition", "v1" to "PersistentVolumeClaim",
            "v1" to "Namespace",
        )
        for ((apiVersion, kind) in resources) {
            assertContentIcon(
                "kinds/$kind.yaml", "apiVersion: $apiVersion\nkind: $kind",
                "emberkai/icons/kubernetes/${kind.lowercase()}.svg",
            )
        }
        val explanation = IconResolver.resolveWithDetails(
            myFixture.addFileToProject("explain.yaml", "apiVersion: v1\nkind: Secret").virtualFile, project,
        )
        assertEquals("context.kubernetes.secret", explanation.mappingKey)
        assertEquals(IconRuleCategory.KUBERNETES, explanation.contextRule)
        assertContentIcon(
            "knative.yaml", "apiVersion: serving.knative.dev/v1\nkind: Service",
            "emberkai/icons/kubernetes.svg",
        )
    }

    fun testMultiDocumentKubernetesKinds() {
        assertContentIcon(
            "same.yaml", "apiVersion: v1\nkind: Secret\n---\napiVersion: v1\nkind: Secret",
            "emberkai/icons/kubernetes/secret.svg",
        )
        assertContentIcon(
            "mixed.yaml",
            "apiVersion: apps/v1\nkind: Deployment\n---\nnotes: {}\n---\napiVersion: v1\nkind: Service",
            "emberkai/icons/kubernetes/bundle.svg",
        )
        assertContentIcon(
            "custom-mixed.yaml",
            "apiVersion: example.com/v1\nkind: Widget\n---\napiVersion: example.com/v1\nkind: Gadget",
            "emberkai/icons/kubernetes/bundle.svg",
        )
        assertContentIcon(
            "custom-same.yaml",
            "apiVersion: example.com/v1\nkind: Widget\n---\napiVersion: example.com/v1\nkind: Widget",
            "emberkai/icons/kubernetes.svg",
        )
        assertContentIcon(
            "trailing.yaml", "apiVersion: v1\nkind: Secret\n---\nkind: [",
            "emberkai/icons/kubernetes.svg",
        )
        assertContentIcon(
            "with-flux.yaml",
            "apiVersion: v1\nkind: Namespace\n---\napiVersion: source.toolkit.fluxcd.io/v1\nkind: GitRepository",
            "emberkai/icons/kubernetes/bundle.svg",
        )
        assertContentIcon(
            "flux-first.yaml",
            "apiVersion: source.toolkit.fluxcd.io/v1\nkind: GitRepository\n---\napiVersion: v1\nkind: Namespace",
            "emberkai/icons/gitops/flux.svg",
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
        assertFileResource(file, "emberkai/icons/kubernetes/service.svg")
        WriteCommandAction.runWriteCommandAction(project) { document!!.setText("apiVersion: v1\nkind: Widget") }
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
        assertContentIcon(
            "chart/templates/flux.yaml",
            "apiVersion: kustomize.toolkit.fluxcd.io/v1\nkind: Kustomization\nmetadata: {}",
            "emberkai/icons/helm.svg",
        )
    }

    fun testCiConfigurationsAndFolders() {
        assertCustomPathIcon("azure-pipelines.yml", "ci/azurepipelines.svg")
        assertCustomPathIcon("ci/azure-pipelines-pr.yaml", "ci/azurepipelines.svg")
        assertCustomPathIcon("bitbucket-pipelines.yaml", "ci/bitbucket.svg")
        assertPathIcon("pipeline.yml", "files/yaml.svg")
        assertCustomPathIcon(".circleci/config.yml", "ci/circleci.svg")
        assertPathIcon(".buildkite/pipeline.yaml", "files/yaml.svg")
        assertPathIcon("config/.drone.yml", "files/yaml.svg")
        assertPathIcon("nested/config.yaml", "files/yaml.svg")

        val folders = mapOf(
            ".azure-pipelines" to "folder-azurepipelines", "azure-pipelines" to "folder-azurepipelines",
            ".bitbucket" to "folder-bitbucket", "bitbucket" to "folder-bitbucket",
        )
        for ((name, icon) in folders) {
            assertCustomFolderIcon("ci/$name", "folders/$icon.svg")
        }
        assertFolderIcon("ci/.circleci", "folders/folder-config.svg")
        for (name in arrayOf(".buildkite", ".drone", "drone")) {
            assertFolderIcon("ci/$name", "folders/folder.svg")
        }
    }

    fun testAnsibleSubdirectoriesRequireAnAnsibleRoot() {
        assertFolderIcon("roles", "folders/folder.svg")
        assertFolderIcon("playbooks", "folders/folder.svg")
    }

    fun testAnsibleConfigurationsPlaybooksAndFolders() {
        assertCustomPathIcon("ansible.cfg", "ansible.svg")
        assertContentIcon(
            "site.yml", "---\n- hosts: all\n  tasks:\n    - name: ping\n      ping:", "emberkai/icons/ansible.svg",
        )
        assertContentIcon(
            "deploy/deploy.yaml", "- hosts: web\n  roles:\n    - common", "emberkai/icons/ansible.svg",
        )
        assertCustomPathIcon("ansible/inventory/hosts.ini", "ansible.svg")
        assertContentIcon("settings.yaml", "name: example", "symbols/icons/files/yaml.svg")

        assertCustomFolderIcon("ansible", "folders/folder-ansible.svg")
        for (name in arrayOf("playbooks", "roles", "inventory", "inventories")) {
            assertCustomFolderIcon("ansible/$name", "folders/folder-ansible.svg")
        }
    }

    fun testInfrastructureConfigurationsAndTemplates() {
        for (name in arrayOf("azure.yaml", "samconfig.toml", "cdk.json", "cdk/cdk.context.json")) {
            assertCustomPathIcon(name, "infrastructure.svg")
        }
        assertContentIcon(
            "template.yaml",
            "AWSTemplateFormatVersion: '2010-09-09'\nResources:\n  Bucket:\n    Type: AWS::S3::Bucket",
            "emberkai/icons/infrastructure.svg",
        )
        assertContentIcon(
            "stack.json", "{\"Resources\":{\"Queue\":{\"Type\":\"AWS::SQS::Queue\"}}}",
            "emberkai/icons/infrastructure.svg",
        )
        assertContentIcon("settings.yaml", "name: example", "symbols/icons/files/yaml.svg")
    }

    fun testGitOpsResourcesBootstrapFilesAndFolders() {
        for (kind in arrayOf("Application", "ApplicationSet", "AppProject")) {
            assertContentIcon(
                "apps/$kind.yaml", "apiVersion: argoproj.io/v1alpha1\nkind: $kind\nmetadata: {}",
                "emberkai/icons/gitops/argocd.svg",
            )
        }
        assertCustomPathIcon("argocd/argocd-cm.yaml", "gitops/argocd.svg")
        assertContentIcon(
            "source.yaml",
            "apiVersion: source.toolkit.fluxcd.io/v1\nkind: GitRepository\nmetadata: {}",
            "emberkai/icons/gitops/flux.svg",
        )
        assertCustomPathIcon("flux-system/gotk-components.yaml", "gitops/flux.svg")
        assertCustomPathIcon("flux-system/gotk-sync.yaml", "gitops/flux.svg")
        assertPathIcon("gotk-components.yaml", "files/yaml.svg")

        for (name in arrayOf("argocd", "argocd-apps")) {
            assertCustomFolderIcon("gitops/$name", "folders/folder-argocd.svg")
        }
        assertCustomFolderIcon("flux-system", "folders/folder-flux.svg")
        assertFolderIcon("empty/flux-system", "folders/folder.svg")
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
            "ansible", "infrastructure", "migration", "openapi", "kubernetes", "kustomize",
            "kubernetes/deployment", "kubernetes/statefulset", "kubernetes/daemonset", "kubernetes/service",
            "kubernetes/ingress", "kubernetes/configmap", "kubernetes/secret", "kubernetes/job",
            "kubernetes/cronjob", "kubernetes/customresourcedefinition", "kubernetes/persistentvolumeclaim",
            "kubernetes/namespace", "kubernetes/bundle",
            "ci/azurepipelines", "ci/bitbucket",
            "gitops/argocd", "gitops/flux",
            "sdkman", "pre-commit", "just", "mise", "snapshot", "openapi_dark",
            "sdkman_dark", "infrastructure_dark", "folder-helm_dark",
            "folders/folder-ansible", "folders/folder-argocd", "folders/folder-azurepipelines",
            "folders/folder-bitbucket", "folders/folder-flux", "folders/folder-migration",
            "folders/folder-api", "folders/folder-api_dark", "folders/folder-kubernetes",
            "folders/folder-kustomize", "folders/folder-fixtures", "folders/folder-testdata",
            "folders/folder-snapshots",
            "folders/folder-ansible_dark", "folders/folder-argocd_dark",
            "folders/folder-azurepipelines_dark", "folders/folder-bitbucket_dark", "folders/folder-content_dark",
            "folders/folder-fixtures_dark", "folders/folder-flux_dark", "folders/folder-kubernetes_dark",
            "folders/folder-kustomize_dark", "folders/folder-migration_dark", "folders/folder-package_dark",
            "folders/folder-resources_dark", "folders/folder-snapshots_dark", "folders/folder-test-package_dark",
            "folders/folder-testdata_dark", "folders/folder-terraform", "folders/folder-terraform_dark",
            "folders/folder-idea", "folders/folder-idea_dark",
            "frameworks/quarkus", "frameworks/micronaut", "frameworks/micronaut_dark", "frameworks/ktor",
            "quality/detekt", "quality/openrewrite", "quality/sonar", "contracts/asyncapi", "contracts/avro",
            "ci/circleci", "ci/circleci_dark", "ci/tekton", "ci/bazel", "devloop/skaffold", "devloop/tilt",
            "devloop/dagger", "devloop/dagger_dark", "infra/packer", "infra/vagrant", "infra/nomad",
        )) {
            val path = "emberkai/icons/$name.svg"
            val icon = IconLoader.getIcon("/$path", SymbolsIconProvider::class.java)
            assertIconAtIdeSize(path, icon)
            if (GraphicsEnvironment.isHeadless()) continue
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
