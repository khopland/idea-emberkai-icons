package com.github.khopland.symbols;

import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.Icon;
import java.io.IOException;

abstract class IconProviderTestCase extends BasePlatformTestCase {
    protected final SymbolsIconProvider provider = new SymbolsIconProvider();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Model a checkout root. Individual tests mark real source/resource roots
        // explicitly instead of treating every fixture folder as Java source.
        ModuleRootModificationUtil.updateModel(getModule(), model -> {
            for (var entry : model.getContentEntries()) {
                for (var source : entry.getSourceFolders()) entry.removeSourceFolder(source);
            }
        });
    }

    protected final void assertFileIcon(String name, String path) {
        var file = myFixture.configureByText(name, "data");
        assertIcon(path, provider.getIcon(file, 0));
        assertIcon(path, provider.getIcon(file.getVirtualFile(), 0, getProject()));
    }

    protected final void assertPathIcon(String name, String path) {
        var file = myFixture.addFileToProject(name, "data");
        assertIcon(path, provider.getIcon(file, 0));
        assertIcon(path, provider.getIcon(file.getVirtualFile(), 0, getProject()));
    }

    protected final void assertCustomPathIcon(String name, String icon) {
        var file = myFixture.addFileToProject(name, "data");
        assertResourceIcon("emberkai/icons/" + icon, provider.getIcon(file, 0));
        assertResourceIcon("emberkai/icons/" + icon, provider.getIcon(file.getVirtualFile(), 0, getProject()));
    }

    protected final void assertCustomFolderIcon(String name, String icon) throws IOException {
        var file = myFixture.getTempDirFixture().findOrCreateDir(name);
        var directory = PsiManager.getInstance(getProject()).findDirectory(file);
        assertNotNull(directory);
        assertResourceIcon("emberkai/icons/" + icon, provider.getIcon(directory, 0));
        assertResourceIcon("emberkai/icons/" + icon, provider.getIcon(file, 0, getProject()));
    }

    protected final void assertFolderIcon(String name, String path) throws IOException {
        var virtualFile = myFixture.getTempDirFixture().findOrCreateDir(name);
        var directory = PsiManager.getInstance(getProject()).findDirectory(virtualFile);
        assertNotNull(directory);
        assertIcon(path, provider.getIcon(directory, 0));
        assertIcon(path, provider.getIcon(virtualFile, 0, getProject()));
    }

    protected final void assertNativeFolder(String name) throws IOException {
        var file = myFixture.getTempDirFixture().findOrCreateDir(name);
        var directory = PsiManager.getInstance(getProject()).findDirectory(file);
        assertNotNull(directory);
        for (int flags : new int[]{0, Iconable.ICON_FLAG_READ_STATUS}) {
            assertNull(name, provider.getIcon(directory, flags));
            assertNull(name, provider.getIcon(file, flags, getProject()));
        }
    }

    protected final void assertIcon(String path, Icon actual) {
        assertResourceIcon("symbols/icons/" + path, actual);
    }

    protected final void assertResourceIcon(String path, Icon actual) {
        assertNotNull(actual);
        assertSame(IconLoader.getIcon("/" + path, SymbolsIconProvider.class), actual);
    }

    protected final void assertNativeIcons(String name, String content) {
        var file = myFixture.configureByText(name, content);
        var element = file.findElementAt(Math.max(0, content.indexOf("class")));
        assertNotNull(element);
        for (int flags : new int[]{0, Iconable.ICON_FLAG_VISIBILITY, Iconable.ICON_FLAG_READ_STATUS}) {
            assertNull(name, provider.getIcon(file, flags));
            assertNull(name, provider.getIcon(element, flags));
            assertNull(name, provider.getIcon(file.getVirtualFile(), flags, getProject()));
        }
    }
}
