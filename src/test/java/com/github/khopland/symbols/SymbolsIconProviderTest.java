package com.github.khopland.symbols;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.Icon;
import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;

public final class SymbolsIconProviderTest extends BasePlatformTestCase {
    private final SymbolsIconProvider provider = new SymbolsIconProvider();

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

    public void testEveryMappingHasAnIconResource() {
        var icons = ResourceBundle.getBundle("symbols.icons", Locale.ROOT,
                SymbolsIconProvider.class.getClassLoader());
        for (String key : icons.keySet()) {
            String resource = "/symbols/icons/" + icons.getString(key);
            assertNotNull(resource, SymbolsIconProvider.class.getResource(resource));
        }
    }

    private void assertFileIcon(String name, String path) {
        var file = myFixture.configureByText(name, "data");
        assertIcon(path, provider.getIcon(file, 0));
    }

    private void assertFolderIcon(String name, String path) throws IOException {
        var virtualFile = myFixture.getTempDirFixture().findOrCreateDir(name);
        var directory = PsiManager.getInstance(getProject()).findDirectory(virtualFile);
        assertNotNull(directory);
        assertIcon(path, provider.getIcon(directory, 0));
    }

    private void assertIcon(String path, Icon actual) {
        assertSame(IconLoader.getIcon("/symbols/icons/" + path, SymbolsIconProvider.class), actual);
    }

    private void assertNativeIcons(String name, String content) {
        var file = myFixture.configureByText(name, content);
        var element = file.findElementAt(Math.max(0, content.indexOf("class")));
        assertNotNull(element);
        for (int flags : new int[]{0, Iconable.ICON_FLAG_VISIBILITY, Iconable.ICON_FLAG_READ_STATUS}) {
            assertNull(name, provider.getIcon(file, flags));
            assertNull(name, provider.getIcon(element, flags));
        }
    }
}
