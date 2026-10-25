package com.github.khopland.symbols

import com.intellij.ide.IconProvider
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.CoreAwareIconManager
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import javax.swing.Icon

internal class KotlinSymbolsIconProviderTest : BasePlatformTestCase() {
    private val provider = KotlinSymbolsIconProvider()
    private var activatedIcons = false

    override fun setUp() {
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

    fun testKotlinExtensionIsRegisteredBeforeTheNativeProvider() {
        assertTrue(IconProvider.EXTENSION_POINT_NAME.extensionList.any { it is KotlinSymbolsIconProvider })
    }

    fun testTypeDistinctionsOnDeclarationsAndFiles() {
        val examples = arrayOf(
            arrayOf("class", "class Example"),
            arrayOf("abstract-class", "abstract class Example"),
            arrayOf("interface", "interface Example"),
            arrayOf("enum", "enum class Example { ONE }"),
            arrayOf("annotation", "annotation class Example"),
            arrayOf("object", "object Example"),
            arrayOf("class", "data class Example(val value: Int)"),
        )
        for ((type, source) in examples) {
            val file = configureSourceFile(source)
            val declaration = file.declarations.first() as KtClassOrObject
            assertContainsType(type, provider.getIcon(declaration, 0))
            assertContainsType(type, provider.getIcon(file, 0))
            assertContainsType(type, declaration.getIcon(0))
        }
    }

    fun testScriptUsesCustomIcon() {
        val file = myFixture.configureByText("example.kts", "println(\"Hello\")") as KtFile
        assertContainsType("script", provider.getIcon(file, 0))
    }

    fun testGradleScriptKeepsNativeIcon() {
        val file = myFixture.configureByText("build.gradle.kts", "plugins {}") as KtFile
        assertNull(provider.getIcon(file, 0))
    }

    fun testMultiDeclarationFileKeepsNativeIcon() {
        val file = myFixture.configureByText("Multiple.kt", "class One\nclass Two") as KtFile
        assertNull(provider.getIcon(file, 0))
    }

    fun testSingleClassFileWithDifferentFileNameKeepsNativeIcon() {
        val file = myFixture.configureByText("Different.kt", "class Example") as KtFile
        PsiTestUtil.addSourceRoot(module, file.virtualFile.parent)
        assertNull(provider.getIcon(file, 0))
    }

    fun testEnumEntriesKeepNativeIcons() {
        val file = configureSourceFile("enum class Example { ONE }")
        val entry = PsiTreeUtil.findChildOfType(file, KtEnumEntry::class.java)
        assertNotNull(entry)
        assertNull(provider.getIcon(entry!!, 0))
    }

    fun testMembersKeepNativeIcons() {
        val file = configureSourceFile("class Example { val value = 1; fun method() {} }")
        val type = file.declarations.first() as KtClass
        for (member: PsiElement in type.declarations) {
            assertNull(provider.getIcon(member, 0))
        }
    }

    fun testPrivateTypeKeepsVisibilityBadge() {
        val file = configureSourceFile("private class Example")
        val type = file.declarations.first()
        val icon = provider.getIcon(type, Iconable.ICON_FLAG_VISIBILITY)
        assertContainsType("class", icon)
        assertTrue(
            IconManager.getInstance().getPlatformIcon(PlatformIcons.Private) in iconsIn(icon),
        )
    }

    fun testEveryCustomIconHasLightAndDarkArtwork() {
        val types = arrayOf(
            "class",
            "abstract-class",
            "interface",
            "enum",
            "annotation",
            "object",
            "script",
        )
        for (type in types) {
            for (suffix in arrayOf("", "_dark")) {
                val icon = IconLoader.getIcon("/icons/kotlin/$type$suffix.svg", javaClass)
                assertEquals(16, icon.iconWidth)
                assertEquals(16, icon.iconHeight)
            }
        }
    }

    private fun configureSourceFile(content: String): KtFile {
        val file = myFixture.configureByText("Example.kt", content) as KtFile
        PsiTestUtil.addSourceRoot(module, file.virtualFile.parent)
        return file
    }

    private fun assertContainsType(type: String, actual: Icon?) {
        assertNotNull(type, actual)
        val expected = IconLoader.getIcon("/icons/kotlin/$type.svg", KotlinSymbolsIconProvider::class.java)
        assertTrue("Missing custom $type icon in $actual", expected in iconsIn(actual))
    }
}
