package com.github.sebastiandotdev.symbols

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.psi.PsiManager
import com.intellij.openapi.util.Iconable

class MyPluginTest : BasePlatformTestCase() {
  
  private val provider = IconProvider()
  
  fun testUsesExactFileNameIconWhenAvailable() {
    val psiFile = myFixture.configureByText("tsconfig.json", "{}")
    
    val icon = provider.getIcon(psiFile, 0)
    
    assertSame(Icons.tsconfig, icon)
    assertNotSame(Icons.brackets_yellow, icon)
  }
  
  fun testUsesCompositeExtensionBeforeSimpleExtension() {
    val psiFile = myFixture.configureByText("feature.test.tsx", "export {}")
    
    val icon = provider.getIcon(psiFile, 0)
    
    assertSame(Icons.react_test, icon)
    assertNotSame(Icons.react_ts, icon)
    assertNotSame(Icons.ts, icon)
  }
  
  fun testUsesFolderSpecificIcon() {
    val dirVFile = myFixture.tempDirFixture.findOrCreateDir("assets")
    val directory = PsiManager.getInstance(project).findDirectory(dirVFile)
    
    assertNotNull(directory)
    val icon = provider.getIcon(directory!!, 0)
    
    assertSame(Icons.folder_assets, icon)
  }
  
  fun testUsesDefaultFolderIconWhenFolderIsUnknown() {
    val dirVFile = myFixture.tempDirFixture.findOrCreateDir("random-folder")
    val directory = PsiManager.getInstance(project).findDirectory(dirVFile)
    
    assertNotNull(directory)
    val icon = provider.getIcon(directory!!, 0)
    
    assertSame(Icons.folder, icon)
  }
  
  fun testReturnsNullForUnknownFileType() {
    val psiFile = myFixture.configureByText("something.unknownext", "data")
    
    val icon = provider.getIcon(psiFile, 0)
    
    assertNull(icon)
  }

  fun testDefersJavaFilesAndDeclarationsToNativeProviders() {
    assertUsesNativeIcons("Example.java", "public class Example { class Nested {} }")
  }

  fun testDefersKotlinFilesAndDeclarationsToNativeProviders() {
    assertUsesNativeIcons("Example.kt", "data class Example(val name: String)")
  }

  fun testDefersKotlinScriptsToNativeProviders() {
    assertUsesNativeIcons("example.kts", "println(\"Hello\")")
  }

  fun testDefersCompiledClassesToNativeProviders() {
    val virtualFile = myFixture.tempDirFixture.createFile("Example.class")
    val psiFile = PsiManager.getInstance(project).findFile(virtualFile)

    assertNotNull(psiFile)
    assertNull(provider.getIcon(psiFile!!, 0))
  }

  fun testDefersMixedCaseJavaAndKotlinExtensionsToNativeProviders() {
    assertUsesNativeIcons("Example.JAVA", "class Example {}")
    assertUsesNativeIcons("Example.KT", "class Example")
  }

  fun testStillThemesFoldersWithSourceFileExtensions() {
    val virtualFile = myFixture.tempDirFixture.findOrCreateDir("examples.kt")
    val directory = PsiManager.getInstance(project).findDirectory(virtualFile)

    assertNotNull(directory)
    assertSame(Icons.folder, provider.getIcon(directory!!, 0))
  }

  fun testStillUsesSymbolsForOtherJavaRelatedFiles() {
    val psiFile = myFixture.configureByText("index.jsp", "<html></html>")

    assertSame(Icons.java, provider.getIcon(psiFile, 0))
  }

  private fun assertUsesNativeIcons(fileName: String, content: String) {
    val psiFile = myFixture.configureByText(fileName, content)
    val declaration = psiFile.findElementAt(content.indexOf("class").coerceAtLeast(0))

    assertNotNull(declaration)
    for (flags in listOf(0, Iconable.ICON_FLAG_VISIBILITY, Iconable.ICON_FLAG_READ_STATUS)) {
      assertNull("$fileName must retain its native file icon", provider.getIcon(psiFile, flags))
      assertNull("$fileName must retain its native declaration icon", provider.getIcon(declaration!!, flags))
    }
  }
}
