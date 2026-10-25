package com.github.khopland.symbols

import com.intellij.ide.IconLayerProvider
import com.intellij.ide.IconProvider
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.ui.CoreAwareIconManager
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import javax.swing.Icon

internal class JavaSymbolsIconProviderTest : LightJavaCodeInsightFixtureTestCase() {
    private val provider = JavaSymbolsIconProvider()
    private var activatedIcons = false

    override fun setUp() {
        // Headless fixtures otherwise use a dummy manager that omits all badges.
        activatedIcons = IconManager.getInstance() !is CoreAwareIconManager
        if (activatedIcons) IconManager.activate(null)
        super.setUp()
        Registry.get("psi.deferIconLoading").setValue(false, testRootDisposable)
    }

    override fun tearDown() {
        try {
            super.tearDown()
        } finally {
            if (activatedIcons) IconManager.Companion.deactivate()
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor =
        LightJavaCodeInsightFixtureTestCase.JAVA_21

    fun testJavaExtensionIsRegistered() {
        assertTrue(IconProvider.EXTENSION_POINT_NAME.extensionList.any { it is JavaSymbolsIconProvider })
    }

    fun testTypeDistinctionsOnDeclarationsAndFiles() {
        val examples = arrayOf(
            arrayOf("class", "public class Example {}"),
            arrayOf("abstract-class", "public abstract class Example {}"),
            arrayOf("interface", "public interface Example {}"),
            arrayOf("enum", "public enum Example { ONE, TWO }"),
            arrayOf("record", "public record Example(String value) {}"),
            arrayOf("annotation", "public @interface Example {}"),
            arrayOf("exception", "public class Example extends Exception {}"),
            arrayOf("abstract-exception", "public abstract class Example extends Exception {}"),
        )
        for ((type, source) in examples) {
            val file = myFixture.configureByText("Example.java", source) as PsiJavaFile
            assertContainsType(type, provider.getIcon(file, 0))
            assertContainsType(type, provider.getIcon(file.classes[0], 0))
            assertContainsType(type, file.classes[0].getIcon(0))
        }
    }

    fun testFileUsesMatchingDeclarationInsteadOfFirstType() {
        val file = myFixture.configureByText("Example.java", "class Helper {} public interface Example {}")
        assertContainsType("interface", provider.getIcon(file, 0))
    }

    fun testAnonymousClassHasItsOwnIcon() {
        val file = myFixture.configureByText("Example.java", "class Example { Object value = new Object() {}; }")
        val anonymous = PsiTreeUtil.findChildOfType(file, PsiAnonymousClass::class.java)
        assertNotNull(anonymous)
        assertContainsType("anonymous", provider.getIcon(anonymous!!, 0))
    }

    fun testSingleDeclarationWithDifferentFileName() {
        val file = myFixture.configureByText("Different.java", "interface Example {}")
        assertContainsType("interface", provider.getIcon(file, 0))
    }

    fun testGenericSourceIconForEmptyOrAmbiguousFiles() {
        assertContainsType("source", provider.getIcon(myFixture.configureByText("Empty.java", ""), 0))
        assertContainsType(
            "source",
            provider.getIcon(myFixture.configureByText("Multiple.java", "class One {} interface Two {}"), 0),
        )
    }

    fun testDescriptorsAndOtherLanguagesUseNativeIcons() {
        assertNull(provider.getIcon(myFixture.configureByText("package-info.java", "package example;"), 0))
        assertNull(provider.getIcon(myFixture.configureByText("module-info.java", "module example {}"), 0))
        assertNull(provider.getIcon(myFixture.configureByText("Example.kt", "interface Example"), 0))
        assertNull(provider.getIcon(myFixture.configureByText("data.json", "{}"), 0))
    }

    fun testMembersAndTypeParametersKeepNativeIcons() {
        val file = myFixture.configureByText(
            "Example.java",
            "class Example<T> { int value; void method() {} }",
        ) as PsiJavaFile
        val psiClass = file.classes[0]
        assertNull(provider.getIcon(psiClass.typeParameters[0], 0))
        assertNull(provider.getIcon(psiClass.fields[0], 0))
        assertNull(provider.getIcon(psiClass.methods[0], 0))
        assertNull(provider.getIcon(psiClass.nameIdentifier!!, 0))
    }

    fun testNestedTypeKeepsStaticFinalAndVisibilityBadges() {
        val file = myFixture.configureByText(
            "Example.java",
            "class Example { private static final class Nested {} }",
        ) as PsiJavaFile
        val nested = file.classes[0].innerClasses[0]
        val icon = provider.getIcon(nested, Iconable.ICON_FLAG_VISIBILITY)
        assertContainsType("class", icon)
        assertContainsPlatformIcon(PlatformIcons.StaticMark, icon)
        assertContainsPlatformIcon(PlatformIcons.FinalMark, icon)
        assertContainsPlatformIcon(PlatformIcons.Private, icon)
    }

    fun testRunnableClassKeepsRunBadge() {
        val file = myFixture.configureByText(
            "Example.java",
            "public class Example { public static void main(String[] args) {} }",
        ) as PsiJavaFile
        val icon = provider.getIcon(file.classes[0], 0)
        assertContainsType("class", icon)
        assertContainsPlatformIcon(PlatformIcons.RunnableMark, icon)
    }

    fun testRegisteredLayersAndReadStatusArePreserved() {
        val file = myFixture.configureByText("Example.java", "public class Example {}") as PsiJavaFile
        val psiClass: PsiClass = file.classes[0]
        val customLayer = IconManager.getInstance().getPlatformIcon(PlatformIcons.JunitTestMark)
        val locked = booleanArrayOf(false)
        IconLayerProvider.EP_NAME.point.registerExtension(
            object : IconLayerProvider {
                override fun getLayerIcon(element: Iconable, isLocked: Boolean): Icon? {
                    if (element !== psiClass) return null
                    locked[0] = isLocked
                    return customLayer
                }

                override fun getLayerDescription(): String = "Test layer"
            },
            testRootDisposable,
        )
        WriteCommandAction.runWriteCommandAction(project) { file.virtualFile.setWritable(false) }
        try {
            assertTrue(customLayer in iconsIn(provider.getIcon(file, Iconable.ICON_FLAG_READ_STATUS)))
            assertTrue("Read status must reach native layer providers", locked[0])
            provider.getIcon(psiClass, 0)
            assertFalse("Read status is controlled by the requested flags", locked[0])
        } finally {
            WriteCommandAction.runWriteCommandAction(project) { file.virtualFile.setWritable(true) }
        }
    }

    fun testCompiledInterfacesKeepTheirType() {
        val stream = PsiClassOwner::class.java.getResourceAsStream("PsiClassOwner.class")
        assertNotNull(stream)
        val bytes = stream!!.use { it.readBytes() }
        val virtualFile = myFixture.tempDirFixture.createFile("CompiledIconFixture.class")
        WriteCommandAction.runWriteCommandAction(project) { virtualFile.setBinaryContent(bytes) }
        val library = PsiTestUtil.addProjectLibrary(
            module,
            "compiled-fixture",
            listOf(virtualFile.parent),
            emptyList(),
        )
        try {
            val file = PsiManager.getInstance(project).findFile(virtualFile)
            assertInstanceOf(file, PsiClassOwner::class.java)
            assertContainsType("interface", provider.getIcon(file!!, 0))
        } finally {
            PsiTestUtil.removeLibrary(module, library)
        }
    }

    fun testEveryCustomIconHasLightAndDarkArtwork() {
        val types = arrayOf(
            "class",
            "abstract-class",
            "interface",
            "enum",
            "record",
            "annotation",
            "exception",
            "abstract-exception",
            "anonymous",
            "source",
        )
        for (type in types) {
            for (suffix in arrayOf("", "_dark")) {
                assertSvgArtwork("/icons/java/$type$suffix.svg")
            }
        }
    }

    private fun assertSvgArtwork(path: String) {
        val svg = javaClass.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
        assertNotNull("Missing SVG resource $path", svg)
        assertTrue(
            "$path must declare a 16x16 canvas",
            svg!!.contains("width=\"16\"") && svg.contains("height=\"16\""),
        )
    }

    private fun assertContainsType(type: String, actual: Icon?) {
        assertNotNull(type, actual)
        val expected = IconLoader.getIcon("/icons/java/$type.svg", JavaSymbolsIconProvider::class.java)
        assertTrue("Missing custom $type icon in $actual", expected in iconsIn(actual))
    }

    private fun assertContainsPlatformIcon(type: PlatformIcons, actual: Icon?) {
        assertTrue(
            "Missing native $type badge",
            IconManager.getInstance().getPlatformIcon(type) in iconsIn(actual),
        )
    }
}
