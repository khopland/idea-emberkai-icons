package com.github.khopland.symbols

import com.github.khopland.symbols.audit.StereotypeRow
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.SemanticTooltipDecorator
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.setEnabled
import com.github.khopland.symbols.stereotypes.ClassStereotype
import com.github.khopland.symbols.stereotypes.ClassStereotypeLayerProvider
import com.github.khopland.symbols.stereotypes.ClassStereotypes
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
import javax.swing.Icon
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

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

    fun testJavaClassIconKeepsNativeLayersAndAddsTheBadge() {
        val psiClass = javaFile(
            "OrderService", "import org.springframework.stereotype.Service;", "@Service public class OrderService {}",
        ).classes.single()
        val layers = layers(psiClass.getIcon(Iconable.ICON_FLAG_VISIBILITY))
        assertTrue(layers.toString(), ClassStereotype.SERVICE.icon in layers)
        assertTrue("The native class icon should stay underneath", layers.size > 1)
        assertEquals(1, layers.count { it === ClassStereotype.SERVICE.icon })
        assertTrue("Editor tabs use the file icon", ClassStereotype.SERVICE.icon in layers(psiClass.containingFile.getIcon(0)))

        val finalClass = javaFile(
            "Clock", "import org.springframework.stereotype.Service;", "@Service public final class Clock {}",
        ).classes.single()
        val finalLayers = layers(finalClass.getIcon(Iconable.ICON_FLAG_VISIBILITY))
        assertTrue("Java's final mark keeps the top-left corner", ClassStereotype.SERVICE.icon !in finalLayers)

        settings.setEnabled(IconRuleCategory.STEREOTYPE_BADGES, false)
        assertNull(ClassStereotypeLayerProvider().getLayerIcon(psiClass, false))
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
        assertTrue(ClassStereotype.WEB.icon in layers(declaration.getIcon(Iconable.ICON_FLAG_VISIBILITY)))
        assertTrue("Editor tabs use the file icon", ClassStereotype.WEB.icon in layers(file.getIcon(0)))
        assertEquals(1, layers(file.getIcon(0)).count { it === ClassStereotype.WEB.icon })

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
        assertEquals("Web controller from @Controller; shown on the native class icon", row.describe())

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
