package com.github.khopland.symbols

import com.github.khopland.symbols.core.IconDescriptions
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.ui.DeferredIcon
import com.intellij.ui.IconManager
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO
import javax.swing.Icon
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.psi.KtFile

/**
 * Renders the Marketplace and README screenshots from the plugin's real icons: every row asks the
 * IDE for the node's icon exactly as the Project view does, so badges, accents, and native class
 * icons match a live IDE. Skipped unless `-Pscreenshots` is passed:
 *
 *     ./gradlew test --tests '*MarketplaceScreenshots' -Pscreenshots
 */
internal class MarketplaceScreenshots : IconProviderTestCase() {
    private val outputDir = System.getProperty("emberkai.screenshots.dir")?.let(::File)
    private val root: Path by lazy { Paths.get(project.basePath!!).resolve("shop-platform") }
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null

    override fun runInDispatchThread(): Boolean = true

    override fun setUp() {
        super.setUp()
        settings = EmberkaiIconsSettings.getInstance()
        savedState = settings.state
        if (GraphicsEnvironment.isHeadless()) IconLoader.activate()
        // Light tests use a dummy icon manager that drops native class icons and their layers.
        IconManager.activate(null)
    }

    override fun tearDown() {
        try {
            savedState?.let(settings::loadState)
            if (outputDir != null) root.toFile().deleteRecursively()
            IconManager.deactivate()
            if (GraphicsEnvironment.isHeadless()) IconLoader.deactivate()
        } finally {
            super.tearDown()
        }
    }

    fun testRenderScreenshots() {
        val dir = outputDir ?: return
        dir.mkdirs()
        createSampleProject()
        for (theme in Theme.entries) {
            render(springServiceRows(), theme, tooltipOn = "orders-service/src/main/resources/db/migration")
                .write(dir, "spring-service", theme)
            render(platformRows(), theme, tooltipOn = "deploy/helm/orders/values-production.yaml")
                .write(dir, "kubernetes-and-ci", theme)
            render(repositoryRows(), theme, tooltipOn = "deploy/overlays/production/deployment-patch.yaml")
                .write(dir, "repository-overview", theme)
        }
    }

    private fun createSampleProject() {
        val files = mapOf(
            "orders-service/build.gradle.kts" to "plugins { id(\"org.springframework.boot\") version \"3.5.0\" }",
            "orders-service/src/main/kotlin/com/shop/orders/OrderController.kt" to kotlin(
                "import org.springframework.web.bind.annotation.RestController", "@RestController\nclass OrderController",
            ),
            "orders-service/src/main/kotlin/com/shop/orders/OrderService.kt" to kotlin(
                "import org.springframework.stereotype.Service", "@Service\nclass OrderService",
            ),
            "orders-service/src/main/kotlin/com/shop/orders/OrderRepository.kt" to kotlin(
                "import org.springframework.data.jpa.repository.JpaRepository",
                "interface OrderRepository : JpaRepository<Order, Long>",
            ),
            "orders-service/src/main/kotlin/com/shop/orders/Order.kt" to kotlin(
                "import jakarta.persistence.Entity", "@Entity\nclass Order(val id: Long)",
            ),
            "orders-service/src/main/kotlin/com/shop/orders/OrdersApplication.kt" to kotlin(
                "import org.springframework.boot.autoconfigure.SpringBootApplication",
                "@SpringBootApplication\nclass OrdersApplication",
            ),
            "orders-service/src/main/kotlin/com/shop/orders/config/SecurityConfig.kt" to kotlin(
                "import org.springframework.context.annotation.Configuration", "@Configuration\nclass SecurityConfig",
            ),
            "orders-service/src/main/resources/db/migration/V1__create_orders.sql" to "create table orders();",
            "orders-service/src/main/resources/db/migration/V2__add_status.sql" to "alter table orders;",
            "orders-service/src/main/resources/db/migration/V2_0__add_index.sql" to "create index;",
            "orders-service/src/main/resources/db/migration/R__refresh_views.sql" to "create view;",
            "orders-service/src/main/resources/application.yaml" to "server:\n  port: 8080",
            "orders-service/src/main/resources/application-local.yaml" to "server:\n  port: 8081",
            "orders-service/src/main/resources/application-production.yaml" to "server:\n  port: 80",
            "orders-service/src/main/resources/openapi.yaml" to "openapi: 3.1.0\ninfo:\n  title: Orders",
            "orders-service/src/test/kotlin/com/shop/orders/OrderServiceTest.kt" to
                "package com.shop.orders\n\nclass OrderServiceTest",
            "orders-service/Dockerfile" to "FROM eclipse-temurin:21",
            "payments-service/build.gradle.kts" to "plugins { id(\"org.springframework.boot\") }",
            "web-app/package.json" to "{}",
            ".github/workflows/ci.yml" to "on: push",
            ".github/workflows/release.yml" to "on: push",
            ".github/CODEOWNERS" to "* @shop/platform",
            "deploy/helm/orders/Chart.yaml" to "apiVersion: v2\nname: orders",
            "deploy/helm/orders/values.yaml" to "replicas: 1",
            "deploy/helm/orders/values-staging.yaml" to "replicas: 2",
            "deploy/helm/orders/values-production.yaml" to "replicas: 3",
            "deploy/helm/orders/templates/deployment.yaml" to "kind: Deployment",
            "deploy/helm/orders/templates/_helpers.tpl" to "{{/* */}}",
            "deploy/k8s/deployment.yaml" to k8s("apps/v1", "Deployment"),
            "deploy/k8s/service.yaml" to k8s("v1", "Service"),
            "deploy/k8s/ingress.yaml" to k8s("networking.k8s.io/v1", "Ingress"),
            "deploy/k8s/configmap.yaml" to k8s("v1", "ConfigMap"),
            "deploy/k8s/cronjob.yaml" to k8s("batch/v1", "CronJob"),
            "deploy/k8s/namespace.yaml" to k8s("v1", "Namespace"),
            "deploy/k8s/secret.yaml" to k8s("v1", "Secret"),
            "deploy/base/kustomization.yaml" to "resources: []",
            "deploy/overlays/staging/kustomization.yaml" to "resources: []",
            "deploy/overlays/production/kustomization.yaml" to "resources: []",
            "deploy/overlays/production/deployment-patch.yaml" to k8s("apps/v1", "Deployment"),
            "deploy/argocd/orders-app.yaml" to k8s("argoproj.io/v1alpha1", "Application"),
            "infra/main.tf" to "terraform {}",
            "infra/.terraform.lock.hcl" to "",
            "contracts/order-events.yaml" to "asyncapi: 3.0.0\ninfo:\n  title: Orders",
            "contracts/order.avsc" to "{}",
            "gradle/libs.versions.toml" to "[versions]",
            "docker-compose.yaml" to "services: {}",
            "docker-compose.production.yaml" to "services: {}",
            ".env.example" to "PORT=8080",
            ".env.local" to "PORT=8080",
            "AGENTS.md" to "# Agents",
            "README.md" to "# Shop",
            "settings.gradle.kts" to "rootProject.name = \"shop\"",
            "build.gradle.kts" to "",
            "renovate.json" to "{}",
        )
        // Service folder colors are keyed to the project base path, so the sample lives on disk under it.
        for ((path, text) in files) {
            val target = root.resolve(path)
            Files.createDirectories(target.parent)
            Files.writeString(target, text)
        }
        Files.createDirectories(root.resolve(".idea"))
        PsiTestUtil.addContentRoot(module, file(""))
        PsiTestUtil.addSourceRoot(module, dir("orders-service/src/main/kotlin"))
        PsiTestUtil.addSourceRoot(module, dir("orders-service/src/main/resources"), JavaResourceRootType.RESOURCE)
        PsiTestUtil.addSourceRoot(module, dir("orders-service/src/test/kotlin"), JavaSourceRootType.TEST_SOURCE)
    }

    private fun springServiceRows() = listOf(
        Row("orders-service", 0),
        Row("orders-service/src", 1),
        Row("orders-service/src/main", 2),
        Row("orders-service/src/main/kotlin", 3),
        Row("orders-service/src/main/kotlin/com/shop/orders", 4, "com.shop.orders"),
        Row("orders-service/src/main/kotlin/com/shop/orders/config", 5, expanded = false),
        Row("orders-service/src/main/kotlin/com/shop/orders/Order.kt", 5, "Order"),
        Row("orders-service/src/main/kotlin/com/shop/orders/OrderController.kt", 5, "OrderController"),
        Row("orders-service/src/main/kotlin/com/shop/orders/OrderRepository.kt", 5, "OrderRepository"),
        Row("orders-service/src/main/kotlin/com/shop/orders/OrdersApplication.kt", 5, "OrdersApplication"),
        Row("orders-service/src/main/kotlin/com/shop/orders/OrderService.kt", 5, "OrderService"),
        Row("orders-service/src/main/resources", 3),
        Row("orders-service/src/main/resources/db/migration", 4, "db.migration"),
        Row("orders-service/src/main/resources/db/migration/R__refresh_views.sql", 5),
        Row("orders-service/src/main/resources/db/migration/V1__create_orders.sql", 5),
        Row("orders-service/src/main/resources/db/migration/V2__add_status.sql", 5),
        Row("orders-service/src/main/resources/db/migration/V2_0__add_index.sql", 5),
        Row("orders-service/src/main/resources/application.yaml", 4),
        Row("orders-service/src/main/resources/application-local.yaml", 4),
        Row("orders-service/src/main/resources/application-production.yaml", 4),
        Row("orders-service/src/main/resources/openapi.yaml", 4),
        Row("orders-service/src/test", 2),
        Row("orders-service/src/test/kotlin", 3),
        Row("orders-service/src/test/kotlin/com/shop/orders", 4, "com.shop.orders"),
        Row("orders-service/src/test/kotlin/com/shop/orders/OrderServiceTest.kt", 5, "OrderServiceTest"),
        Row("orders-service/build.gradle.kts", 1),
        Row("orders-service/Dockerfile", 1),
    )

    private fun platformRows() = listOf(
        Row(".github", 0),
        Row(".github/workflows", 1),
        Row(".github/workflows/ci.yml", 2),
        Row(".github/workflows/release.yml", 2),
        Row(".github/CODEOWNERS", 1),
        Row("deploy", 0),
        Row("deploy/argocd", 1),
        Row("deploy/argocd/orders-app.yaml", 2),
        Row("deploy/helm/orders", 1, "helm/orders"),
        Row("deploy/helm/orders/templates", 2),
        Row("deploy/helm/orders/templates/_helpers.tpl", 3),
        Row("deploy/helm/orders/templates/deployment.yaml", 3),
        Row("deploy/helm/orders/Chart.yaml", 2),
        Row("deploy/helm/orders/values.yaml", 2),
        Row("deploy/helm/orders/values-production.yaml", 2),
        Row("deploy/helm/orders/values-staging.yaml", 2),
        Row("deploy/k8s", 1),
        Row("deploy/k8s/configmap.yaml", 2),
        Row("deploy/k8s/cronjob.yaml", 2),
        Row("deploy/k8s/deployment.yaml", 2),
        Row("deploy/k8s/ingress.yaml", 2),
        Row("deploy/k8s/namespace.yaml", 2),
        Row("deploy/k8s/secret.yaml", 2),
        Row("deploy/k8s/service.yaml", 2),
        Row("infra", 0),
        Row("infra/.terraform.lock.hcl", 1),
        Row("infra/main.tf", 1),
    )

    private fun repositoryRows() = listOf(
        Row("", 0, "shop-platform"),
        Row(".github", 1, expanded = false),
        Row(".idea", 1, expanded = false),
        Row("contracts", 1),
        Row("contracts/order.avsc", 2),
        Row("contracts/order-events.yaml", 2),
        Row("deploy", 1),
        Row("deploy/base", 2, expanded = false),
        Row("deploy/overlays", 2),
        Row("deploy/overlays/production", 3),
        Row("deploy/overlays/production/deployment-patch.yaml", 4),
        Row("deploy/overlays/production/kustomization.yaml", 4),
        Row("deploy/overlays/staging", 3, expanded = false),
        Row("gradle", 1),
        Row("gradle/libs.versions.toml", 2),
        Row("orders-service", 1, expanded = false),
        Row("payments-service", 1, expanded = false),
        Row("web-app", 1, expanded = false),
        Row(".env.example", 1),
        Row(".env.local", 1),
        Row("AGENTS.md", 1),
        Row("build.gradle.kts", 1),
        Row("docker-compose.production.yaml", 1),
        Row("docker-compose.yaml", 1),
        Row("README.md", 1),
        Row("renovate.json", 1),
        Row("settings.gradle.kts", 1),
    )

    private data class Row(val path: String, val depth: Int, val label: String? = null, val expanded: Boolean? = null)

    private enum class Theme(
        val dark: Boolean,
        val window: Color,
        val header: Color,
        val border: Color,
        val text: Color,
        val muted: Color,
        val selection: Color,
        val tooltip: Color,
        val tooltipBorder: Color,
    ) {
        DARK(
            dark = true,
            window = Color(0x2B2D30), header = Color(0x2B2D30), border = Color(0x1E1F22),
            text = Color(0xDFE1E5), muted = Color(0x868A91), selection = Color(0x2E436E),
            tooltip = Color(0x393B40), tooltipBorder = Color(0x4E5157),
        ),
        LIGHT(
            dark = false,
            window = Color(0xF7F8FA), header = Color(0xF7F8FA), border = Color(0xDFE1E5),
            text = Color(0x1E1F22), muted = Color(0x6C707E), selection = Color(0xD5E1FF),
            tooltip = Color(0xFFFFFF), tooltipBorder = Color(0xC9CCD6),
        ),
    }

    private fun render(rows: List<Row>, theme: Theme, tooltipOn: String): BufferedImage {
        val image = BufferedImage(WIDTH * SCALE, HEIGHT * SCALE, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.scale(SCALE.toDouble(), SCALE.toDouble())
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

            g.color = theme.window
            g.fillRect(0, 0, WIDTH, HEIGHT)
            val font = uiFont()
            g.font = font.deriveFont(Font.BOLD, 13f)
            g.color = theme.text
            g.drawString("Project", 16f, 26f)
            g.color = theme.border
            g.drawLine(0, HEADER - 1, WIDTH, HEADER - 1)

            g.font = font.deriveFont(13f)
            val metrics = g.fontMetrics
            val selected = rows.indexOfFirst { it.path == tooltipOn }
            var tooltipAnchor: Pair<Int, Int>? = null
            rows.forEachIndexed { index, row ->
                val top = HEADER + 6 + index * ROW
                val file = file(row.path)
                val x = 14 + row.depth * INDENT
                if (index == selected) {
                    g.color = theme.selection
                    g.fill(RoundRectangle2D.Float(6f, top.toFloat(), (WIDTH - 12).toFloat(), ROW.toFloat(), 6f, 6f))
                }
                val expanded = row.expanded ?: file.isDirectory
                if (file.isDirectory) chevron(g, x, top, expanded, theme.muted)
                val icon = icon(file, theme)
                icon.paintIcon(null, g, x + 18, top + (ROW - 16) / 2)
                val label = row.label ?: file.name
                val baseline = top + (ROW + metrics.ascent - metrics.descent) / 2
                g.color = theme.text
                g.drawString(label, x + 40, baseline)
                if (index == selected) tooltipAnchor = (x + 40 + metrics.stringWidth(label) + 20) to top
            }
            tooltipAnchor?.let { (x, y) -> tooltip(g, describe(file(tooltipOn)), x, y, theme, font) }
        } finally {
            g.dispose()
        }
        return image
    }

    private fun chevron(g: java.awt.Graphics2D, x: Int, top: Int, expanded: Boolean, color: Color) {
        val cx = x + 6f
        val cy = top + ROW / 2f
        val path = Path2D.Float()
        if (expanded) {
            path.moveTo(cx - 3.5f, cy - 1.75f); path.lineTo(cx, cy + 1.75f); path.lineTo(cx + 3.5f, cy - 1.75f)
        } else {
            path.moveTo(cx - 1.75f, cy - 3.5f); path.lineTo(cx + 1.75f, cy); path.lineTo(cx - 1.75f, cy + 3.5f)
        }
        g.color = color
        g.stroke = BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(path)
    }

    /** Draws the hover tooltip beside the row at [rowTop], so it covers no other rows. */
    private fun tooltip(g: java.awt.Graphics2D, text: String, x: Int, rowTop: Int, theme: Theme, font: Font) {
        g.font = font.deriveFont(12.5f)
        val metrics = g.fontMetrics
        val width = metrics.stringWidth(text) + 20
        val height = ROW + 4
        val left = x.coerceAtMost(WIDTH - width - 10)
        val y = rowTop - 2
        val shape = RoundRectangle2D.Float(left.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), 8f, 8f)
        g.color = Color(0, 0, 0, if (theme.dark) 90 else 40)
        g.fill(RoundRectangle2D.Float(left + 1f, y + 3f, width.toFloat(), height.toFloat(), 8f, 8f))
        g.color = theme.tooltip
        g.fill(shape)
        g.color = theme.tooltipBorder
        g.stroke = BasicStroke(1f)
        g.draw(shape)
        g.color = theme.text
        g.drawString(text, left + 10, y + (height + metrics.ascent - metrics.descent) / 2)
    }

    private fun icon(file: VirtualFile, theme: Theme): Icon {
        val psiManager = PsiManager.getInstance(project)
        val element = if (file.isDirectory) psiManager.findDirectory(file) else psiManager.findFile(file)
        // The Project view shows a single-class Kotlin file as its class node.
        val node = (element as? KtFile)?.declarations?.singleOrNull() ?: element
        var icon = node!!.getIcon(0)
        if (icon is DeferredIcon) icon = icon.evaluate()
        return if (theme.dark) IconLoader.getDarkIcon(icon, true) else icon
    }

    private fun describe(file: VirtualFile): String =
        checkNotNull(IconDescriptions.describe(file, IconResolver.resolveWithDetails(file, project), project)) { file.path }

    private fun file(path: String): VirtualFile =
        checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.resolve(path))) { path }

    private fun dir(path: String): VirtualFile = file(path)

    private fun uiFont(): Font {
        val families = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val family = listOf("Inter", "SF Pro Text", ".AppleSystemUIFont", "Helvetica Neue").firstOrNull { it in families }
        return if (family != null) Font(family, Font.PLAIN, 13) else UIUtil.getLabelFont()
    }

    private fun BufferedImage.write(dir: File, name: String, theme: Theme) {
        ImageIO.write(this, "png", File(dir, "$name-${theme.name.lowercase()}.png"))
    }

    private fun kotlin(import: String, declaration: String) = "package com.shop.orders\n\n$import\n\n$declaration\n"

    private fun k8s(apiVersion: String, kind: String) = "apiVersion: $apiVersion\nkind: $kind\nmetadata:\n  name: orders\n"

    private companion object {
        const val SCALE = 2
        const val WIDTH = 680
        const val ROW = 24
        const val INDENT = 18
        const val HEADER = 40
        const val HEIGHT = HEADER + 6 + 27 * ROW + 10
    }
}
