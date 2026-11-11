package com.github.khopland.emberkai.stereotypes

import com.github.khopland.emberkai.IconProviderTestCase
import com.github.khopland.emberkai.audit.StereotypeRow
import com.github.khopland.emberkai.settings.IconRuleCategory
import com.github.khopland.emberkai.lens.IconLens
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.github.khopland.emberkai.scopes.EmberkaiScope
import com.github.khopland.emberkai.settings.setEnabled
import com.github.khopland.emberkai.tooltips.SemanticTooltipDecorator
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.testFramework.PsiTestUtil
import com.intellij.ui.DeferredIcon
import com.intellij.ui.IconManager
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RowIcon
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import javax.swing.Icon

internal class ClassStereotypeTest : IconProviderTestCase() {
    private lateinit var settings: EmberkaiIconsSettings
    private var savedState: EmberkaiIconsSettings.State? = null

    override fun setUp() {
        super.setUp()
        settings = EmberkaiIconsSettings.getInstance()
        savedState = settings.state
        // Light tests use a dummy icon manager that ignores icon layer providers.
        IconManager.activate(null)
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/main/java"))
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/main/kotlin"))
    }

    override fun tearDown() {
        try {
            savedState?.let(settings::loadState)
            IconManager.deactivate()
        } finally {
            super.tearDown()
        }
    }

    fun testJavaAnnotationsAreReadThroughImports() {
        assertJava(ClassStereotype.WEB, "import org.springframework.web.bind.annotation.RestController;", "@RestController")
        assertJava(ClassStereotype.WEB, "import org.springframework.stereotype.*;", "@Controller")
        assertJava(ClassStereotype.SERVICE, "import org.springframework.stereotype.Service;", "@Service")
        assertJava(ClassStereotype.REPOSITORY, "import org.springframework.stereotype.Repository;", "@Repository")
        assertJava(ClassStereotype.CONFIGURATION, "", "@org.springframework.context.annotation.Configuration")
        assertJava(ClassStereotype.ENTITY, "import jakarta.persistence.Entity;", "@Entity(name = \"orders\")")
        assertJava(ClassStereotype.ENTITY, "import javax.persistence.*;", "@Entity")
        assertJava(
            ClassStereotype.APPLICATION,
            "import org.springframework.boot.autoconfigure.SpringBootApplication;\n" +
                "import org.springframework.context.annotation.Configuration;",
            "@Configuration @SpringBootApplication",
        )
    }

    fun testQuarkusAndMicronautRoles() {
        assertJava(ClassStereotype.WEB, "import jakarta.ws.rs.Path;", "@Path(\"/orders\")")
        assertJava(ClassStereotype.WEB, "import io.micronaut.http.annotation.Controller;", "@Controller(\"/orders\")")
        assertJava(ClassStereotype.SERVICE, "import jakarta.enterprise.context.ApplicationScoped;", "@ApplicationScoped")
        assertJava(ClassStereotype.REPOSITORY, "import io.micronaut.data.jdbc.annotation.JdbcRepository;", "@JdbcRepository")
        assertJava(ClassStereotype.ENTITY, "import io.micronaut.data.annotation.MappedEntity;", "@MappedEntity")
        assertJava(ClassStereotype.CLIENT, "import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;", "@RegisterRestClient")
        assertJava(ClassStereotype.LISTENER, "import io.micronaut.configuration.kafka.annotation.KafkaListener;", "@KafkaListener")

        val panache = javaFile(
            "OrderRepository",
            "import io.quarkus.hibernate.orm.panache.PanacheRepository;",
            "public class OrderRepository implements PanacheRepository<Order> {}",
        )
        assertEquals(ClassStereotype.REPOSITORY, ClassStereotypes.fileStereotype(panache)?.stereotype)
    }

    fun testEntryPointsAreReadFromMethodsAndOutrankTheClassRole() {
        val scheduled = javaFile(
            "Cleanup",
            "import org.springframework.scheduling.annotation.Scheduled;\nimport org.springframework.stereotype.Service;",
            "@Service public class Cleanup {\n  @Scheduled(cron = \"0 0 * * * *\") public void purge() {}\n}",
        )
        assertEquals(ClassStereotype.SCHEDULED, ClassStereotypes.fileStereotype(scheduled)?.stereotype)
        assertEquals("@Scheduled on purge()", ClassStereotypes.fileStereotype(scheduled)?.evidence)

        val listener = javaFile(
            "OrderEvents",
            "import org.springframework.kafka.annotation.KafkaListener;\nimport org.springframework.jms.annotation.*;",
            "public class OrderEvents {\n  @KafkaListener(topics = \"orders\") void onOrder(String o) {}\n  @JmsListener(destination = \"q\") void onJms(String m) {}\n}",
        )
        assertEquals(ClassStereotype.LISTENER, ClassStereotypes.fileStereotype(listener)?.stereotype)

        val exchange = javaFile(
            "OrdersClient",
            "import org.springframework.web.service.annotation.GetExchange;",
            "public interface OrdersClient {\n  @GetExchange(\"/orders\") String orders();\n}",
        )
        assertEquals(ClassStereotype.CLIENT, ClassStereotypes.fileStereotype(exchange)?.stereotype)
        assertEquals("@GetExchange on orders()", ClassStereotypes.fileStereotype(exchange)?.evidence)

        val feign = kotlinFile(
            "PaymentsClient",
            "import org.springframework.cloud.openfeign.FeignClient",
            "@FeignClient(\"payments\")\ninterface PaymentsClient",
        )
        assertEquals(ClassStereotype.CLIENT, ClassStereotypes.fileStereotype(feign)?.stereotype)

        val incoming = kotlinFile(
            "PriceConsumer",
            "import org.eclipse.microprofile.reactive.messaging.Incoming\nimport jakarta.enterprise.context.ApplicationScoped",
            "@ApplicationScoped\nclass PriceConsumer {\n    @Incoming(\"prices\")\n    fun consume(price: Double) = Unit\n}",
        )
        assertEquals(ClassStereotype.LISTENER, ClassStereotypes.fileStereotype(incoming)?.stereotype)
        assertEquals("@Incoming on consume()", ClassStereotypes.fileStereotype(incoming)?.evidence)
    }

    fun testMethodAnnotationsNeedTheirImportsToo() {
        val lookalike = javaFile(
            "Jobs",
            "import com.acme.Scheduled;",
            "public class Jobs {\n  @Scheduled void run() {}\n}",
        )
        assertNull(ClassStereotypes.fileStereotype(lookalike))
        val events = javaFile(
            "Events",
            "import org.springframework.context.event.EventListener;",
            "public class Events {\n  @EventListener void on(Object e) {}\n}",
        )
        assertNull("In-process events are not entry points", ClassStereotypes.fileStereotype(events))
        val classOnly = javaFile(
            "Scheduler",
            "import org.springframework.scheduling.annotation.Scheduled;",
            "@Scheduled public class Scheduler {}",
        )
        assertNull("@Scheduled counts only on methods", ClassStereotypes.fileStereotype(classOnly))
    }

    fun testEntryPointScopeAndLens() {
        val controller = javaFile(
            "Orders", "import org.springframework.web.bind.annotation.RestController;", "@RestController public class Orders {}",
        ).virtualFile
        val listener = kotlinFile(
            "Prices",
            "import org.springframework.kafka.annotation.KafkaListener",
            "class Prices {\n    @KafkaListener(topics = [\"prices\"])\n    fun on(p: String) = Unit\n}",
        ).virtualFile
        val service = javaFile("Billing", "import org.springframework.stereotype.Service;", "@Service public class Billing {}").virtualFile
        val client = javaFile(
            "Remote", "import org.springframework.cloud.openfeign.FeignClient;", "@FeignClient(\"r\") public interface Remote {}",
        ).virtualFile
        val readme = myFixture.addFileToProject("README.md", "# readme").virtualFile

        for (file in listOf(controller, listener)) {
            assertTrue(file.name, EmberkaiScope.ENTRY_POINTS.contains(file, project))
            assertTrue(file.name, IconLens.ENTRY_POINTS.contains(file, project))
        }
        for (file in listOf(service, client, readme)) {
            assertFalse(file.name, EmberkaiScope.ENTRY_POINTS.contains(file, project))
        }

        settings.setEnabled(IconRuleCategory.STEREOTYPE_BADGES, false)
        assertTrue("Scopes ignore the badge setting", EmberkaiScope.ENTRY_POINTS.contains(controller, project))
    }

    fun testTestClassesShowWhatTheyStart() {
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), true)
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/kotlin"), true)
        val application = testFile(
            "OrdersApplicationTests.java",
            "import org.springframework.boot.test.context.SpringBootTest;",
            "@SpringBootTest class OrdersApplicationTests {}",
        )
        val slice = testFile(
            "OrdersControllerTest.java",
            "import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;",
            "@WebMvcTest(OrdersController.class) class OrdersControllerTest {}",
        )
        val boot4Slice = testFile(
            "OrderRepositoryTest.java",
            "import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;",
            "@DataJpaTest class OrderRepositoryTest {}",
        )
        val containers = testFile(
            "OrderRepositoryContainerTest.java",
            "import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;\n" +
                "import org.testcontainers.junit.jupiter.Testcontainers;",
            "@DataJpaTest @Testcontainers class OrderRepositoryContainerTest {}",
        )
        val quarkus = testFile("OrdersResourceTest.java", "import io.quarkus.test.junit.QuarkusTest;", "@QuarkusTest class OrdersResourceTest {}")
        val micronaut = testFile(
            "OrdersClientTest.kt",
            "import io.micronaut.test.extensions.junit5.annotation.MicronautTest",
            "@MicronautTest\nclass OrdersClientTest",
        )
        val lookalike = testFile("LookalikeTest.java", "import com.acme.SpringBootTest;", "@SpringBootTest class LookalikeTest {}")

        assertEquals(ClassStereotype.APPLICATION_TEST, ClassStereotypes.fileStereotype(application)?.stereotype)
        assertEquals(ClassStereotype.SLICE_TEST, ClassStereotypes.fileStereotype(slice)?.stereotype)
        assertEquals("@WebMvcTest", ClassStereotypes.fileStereotype(slice)?.evidence)
        assertEquals(ClassStereotype.SLICE_TEST, ClassStereotypes.fileStereotype(boot4Slice)?.stereotype)
        val container = ClassStereotypes.fileStereotype(containers)
        assertEquals("Starting containers outranks the slice", ClassStereotype.CONTAINER_TEST, container?.stereotype)
        assertEquals("@Testcontainers", container?.evidence)
        assertEquals(ClassStereotype.APPLICATION_TEST, ClassStereotypes.fileStereotype(quarkus)?.stereotype)
        assertEquals(ClassStereotype.APPLICATION_TEST, ClassStereotypes.fileStereotype(micronaut)?.stereotype)
        assertNull(ClassStereotypes.fileStereotype(lookalike))

        val psiClass = (slice as PsiJavaFile).classes.single()
        assertTrue(ClassStereotype.SLICE_TEST.classIcon in layers(psiClass.getIcon(Iconable.ICON_FLAG_VISIBILITY)))
        for (file in listOf(application, slice, containers)) {
            assertFalse("Tests are not entry points", EmberkaiScope.ENTRY_POINTS.contains(file.virtualFile, project))
        }
        assertEquals(
            "Test slice from @WebMvcTest; shown as the class icon",
            StereotypeRow(ClassStereotypes.fileStereotype(slice)).describe(),
        )
    }

    fun testSpringDataRepositoryInterfacesNeedNoAnnotation() {
        val file = javaFile(
            "OrderRepository",
            "import org.springframework.data.jpa.repository.JpaRepository;",
            "public interface OrderRepository extends JpaRepository<Order, Long> {}",
        )
        assertEquals(ClassStereotype.REPOSITORY, ClassStereotypes.fileStereotype(file)?.stereotype)
        assertEquals("extends JpaRepository", ClassStereotypes.fileStereotype(file)?.evidence)
    }

    fun testLookalikeAnnotationsAreIgnored() {
        assertJava(null, "import com.acme.Service;", "@Service")
        assertJava(null, "", "@Service")
        assertJava(null, "import org.springframework.stereotype.Component;", "@Component")
        assertJava(null, "import org.springframework.stereotype.*;", "@Deprecated")
    }

    fun testJavaClassIconSwapsTheTypeIconAndKeepsNativeMarks() {
        val psiClass = javaFile(
            "OrderService", "import org.springframework.stereotype.Service;", "@Service public class OrderService {}",
        ).classes.single()
        val layers = layers(psiClass.getIcon(Iconable.ICON_FLAG_VISIBILITY))
        assertEquals("The stereotype icon replaces the type icon", ClassStereotype.SERVICE.classIcon, layers.first())
        assertTrue("The visibility column should stay", layers.size > 1)
        assertTrue("The native class icon is gone", AllIcons.Nodes.Class !in layers)
        assertTrue("Editor tabs use the file icon", ClassStereotype.SERVICE.classIcon in layers(psiClass.containingFile.getIcon(0)))

        val finalClass = javaFile(
            "Clock", "import org.springframework.stereotype.Service;", "@Service public final class Clock {}",
        ).classes.single()
        val finalLayers = layers(finalClass.getIcon(Iconable.ICON_FLAG_VISIBILITY))
        assertEquals(ClassStereotype.SERVICE.classIcon, finalLayers.first())
        assertTrue("Java's final mark is layered over the stereotype icon", AllIcons.Nodes.FinalMark in finalLayers)

        settings.setEnabled(IconRuleCategory.STEREOTYPE_BADGES, false)
        assertNull(JavaStereotypeIconProvider().getIcon(psiClass, Iconable.ICON_FLAG_VISIBILITY))
    }

    fun testKotlinDeclarationsAndFiles() {
        val file = kotlinFile(
            "OrderController",
            "import org.springframework.web.bind.annotation.RestController as Rest",
            "@Rest\nclass OrderController",
        )
        val declaration = file.declarations.single() as KtClassOrObject
        assertEquals(ClassStereotype.WEB, ClassStereotypes.badgeFor(declaration)?.stereotype)
        assertEquals(ClassStereotype.WEB, ClassStereotypes.badgeFor(file)?.stereotype)
        assertEquals(ClassStereotype.WEB.classIcon, layers(declaration.getIcon(Iconable.ICON_FLAG_VISIBILITY)).first())
        assertEquals("Editor tabs use the file icon", ClassStereotype.WEB.classIcon, layers(file.getIcon(0)).first())
        assertTrue("The native Kotlin class icon is gone", KotlinIcons.CLASS !in layers(file.getIcon(0)))

        val repository = kotlinFile(
            "OrderRepository",
            "import org.springframework.data.repository.kotlin.CoroutineCrudRepository",
            "interface OrderRepository : CoroutineCrudRepository<Order, Long>",
        )
        assertEquals(ClassStereotype.REPOSITORY, ClassStereotypes.fileStereotype(repository)?.stereotype)

        val entity = kotlinFile("Order", "", "@jakarta.persistence.Entity\ndata class Order(val id: Long)")
        assertEquals(ClassStereotype.ENTITY, ClassStereotypes.fileStereotype(entity)?.stereotype)
    }

    fun testFileBadgeNeedsTheFilesOwnClassAndNativeIcons() {
        val helpers = kotlinFile(
            "Beans",
            "import org.springframework.stereotype.Service",
            "@Service\nclass OrderService\n\nfun helper() = Unit",
        )
        assertNull(ClassStereotypes.fileStereotype(helpers))
        assertEquals(
            ClassStereotype.SERVICE,
            ClassStereotypes.badgeFor(helpers.declarations.first() as KtClassOrObject)?.stereotype,
        )

        val service = kotlinFile("OrderService", "import org.springframework.stereotype.Service", "@Service\nclass OrderService")
        settings.preserveNativeJavaKotlinIcons = false
        assertNull("Emberkai file artwork stays undecorated", ClassStereotypes.badgeFor(service))
        assertNotNull("Declarations keep native icons", ClassStereotypes.badgeFor(service.declarations.single()))
    }

    fun testFilesOutsideSourceRootsAreIgnored() {
        val file = myFixture.addFileToProject(
            "scratch/OrderService.java",
            "import org.springframework.stereotype.Service;\n@Service class OrderService {}",
        ) as PsiJavaFile
        assertNull(ClassStereotypes.badgeFor(file.classes.single()))
    }

    fun testDetectionReadsStubsWithoutLoadingTheTree() {
        val java = freshFile(
            "src/main/java/com/example/Orders.java",
            "package com.example;\nimport org.springframework.stereotype.Service;\n@Service public class Orders {}",
        ) as PsiJavaFile
        assertEquals(ClassStereotype.SERVICE, ClassStereotypes.badgeFor(java.classes.single())?.stereotype)
        assertNull("Java AST was loaded", (java as PsiFileImpl).treeElement)

        val kotlin = freshFile(
            "src/main/kotlin/com/example/Orders.kt",
            "package com.example\nimport org.springframework.stereotype.Repository\n@Repository class Orders",
        ) as KtFile
        assertEquals(ClassStereotype.REPOSITORY, ClassStereotypes.badgeFor(kotlin)?.stereotype)
        assertNull("Kotlin AST was loaded", (kotlin as PsiFileImpl).treeElement)

    }

    fun testJavaMethodAnnotationsAreReadFromStubs() {
        val javaJob = freshFile(
            "src/main/java/com/example/Job.java",
            "package com.example;\nimport org.springframework.scheduling.annotation.Scheduled;\n" +
                "public class Job {\n  @Scheduled(fixedRate = 1000) public void tick() {}\n}",
        ) as PsiJavaFile
        assertEquals(ClassStereotype.SCHEDULED, ClassStereotypes.badgeFor(javaJob.classes.single())?.stereotype)
        assertNull("Java AST was loaded for method annotations", (javaJob as PsiFileImpl).treeElement)
    }

    // Each fresh Kotlin file needs its own test: later files in one fixture arrive with a tree already loaded.
    fun testKotlinMethodAnnotationsAreReadFromStubs() {
        val kotlinJob = freshFile(
            "src/main/kotlin/com/example/KJob.kt",
            "package com.example\nimport org.springframework.scheduling.annotation.Scheduled\n" +
                "class KJob {\n    @Scheduled(fixedRate = 1000)\n    fun tick() = Unit\n}",
        ) as KtFile
        assertEquals(ClassStereotype.SCHEDULED, ClassStereotypes.badgeFor(kotlinJob)?.stereotype)
        assertNull("Kotlin AST was loaded for method annotations", (kotlinJob as PsiFileImpl).treeElement)
    }

    fun testCachedStereotypeFollowsEdits() {
        val file = javaFile("Orders", "import org.springframework.stereotype.*;", "@Service public class Orders {}")
        val psiClass = file.classes.single()
        assertEquals(ClassStereotype.SERVICE, ClassStereotypes.badgeFor(psiClass)?.stereotype)

        val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) {
            val offset = document.text.indexOf("@Service")
            document.replaceString(offset, offset + "@Service".length, "@Repository")
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        assertEquals(ClassStereotype.REPOSITORY, ClassStereotypes.badgeFor(file.classes.single())?.stereotype)
    }

    fun testTooltipAndExplanationDescribeTheBadge() {
        val psiClass = javaFile(
            "OrderController", "import org.springframework.stereotype.Controller;", "@Controller public class OrderController {}",
        ).classes.single()
        val data = PresentationData()
        SemanticTooltipDecorator().decorate(ClassTreeNode(project, psiClass, ViewSettings.DEFAULT), data)
        assertEquals("Web controller stereotype (@Controller)", data.tooltip)

        val row = StereotypeRow(ClassStereotypes.fileStereotype(psiClass.containingFile))
        assertEquals("Web controller from @Controller; shown as the class icon", row.describe())

        settings.setEnabled(IconRuleCategory.STEREOTYPE_BADGES, false)
        val disabled = PresentationData()
        SemanticTooltipDecorator().decorate(ClassTreeNode(project, psiClass, ViewSettings.DEFAULT), disabled)
        assertNull(disabled.tooltip)
    }

    private fun assertJava(expected: ClassStereotype?, imports: String, annotations: String) {
        val name = "Sample${counter++}"
        val file = javaFile(name, imports, "$annotations\npublic class $name {}")
        assertEquals(annotations, expected, ClassStereotypes.badgeFor(file.classes.single())?.stereotype)
        assertEquals(annotations, expected, ClassStereotypes.fileStereotype(file)?.stereotype)
    }

    private var counter = 0

    private fun javaFile(name: String, imports: String, body: String): PsiJavaFile =
        myFixture.addFileToProject(
            "src/main/java/com/example/$name.java",
            "package com.example;\n\n$imports\n\n$body\n",
        ) as PsiJavaFile

    private fun kotlinFile(name: String, imports: String, body: String): KtFile =
        myFixture.addFileToProject(
            "src/main/kotlin/com/example/$name.kt",
            "package com.example\n\n$imports\n\n$body\n",
        ) as KtFile

    private fun testFile(name: String, imports: String, body: String): PsiFile {
        val language = if (name.endsWith(".kt")) "kotlin" else "java"
        val semicolon = if (language == "java") ";" else ""
        return myFixture.addFileToProject(
            "src/test/$language/com/example/$name",
            "package com.example$semicolon\n\n$imports\n\n$body\n",
        )
    }

    /** A file written through the VFS, so no PSI tree exists until something parses it. */
    private fun freshFile(path: String, text: String): PsiFile {
        val file = myFixture.tempDirFixture.createFile(path, text)
        return PsiManager.getInstance(project).findFile(file)!!
    }

    private fun layers(icon: Icon?): List<Icon> = when (icon) {
        null -> emptyList()
        is DeferredIcon -> layers(icon.evaluate())
        is RowIcon -> icon.allIcons.flatMap(::layers)
        is LayeredIcon -> icon.allLayers.filterNotNull().flatMap(::layers)
        else -> listOf(icon)
    }
}
