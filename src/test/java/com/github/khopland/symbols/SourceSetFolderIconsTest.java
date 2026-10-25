package com.github.khopland.symbols;

import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;
import org.jetbrains.jps.model.java.JavaResourceRootProperties;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.IOException;

public final class SourceSetFolderIconsTest extends IconProviderTestCase {
    public void testMultiplatformProductionLayoutsBeforeImport() throws IOException {
        for (String name : new String[]{"commonMain", "jvmMain", "iosMain", "androidMain", "nativeMain", "wasmJsMain",
                "desktopMain"}) {
            String path = "shared/src/" + name;
            assertCustomFolderIcon(path, "folders/folder-content.svg");
            assertCustomFolderIcon(path + "/kotlin", "folders/folder-source.svg");
            assertCustomFolderIcon(path + "/kotlin/com/example", "folders/folder-package.svg");
            assertCustomFolderIcon(path + "/resources", "folders/folder-resources.svg");
        }
    }

    public void testMultiplatformTestLayoutsBeforeImport() throws IOException {
        for (String name : new String[]{"commonTest", "jvmTest", "iosTest", "androidUnitTest", "wasmJsTest"}) {
            String path = "shared/src/" + name;
            assertCustomFolderIcon(path, "folders/folder-content.svg");
            assertCustomFolderIcon(path + "/kotlin", "folders/folder-test-source.svg");
            assertCustomFolderIcon(path + "/kotlin/com/example", "folders/folder-test-package.svg");
            assertCustomFolderIcon(path + "/resources", "folders/folder-test-resources.svg");
        }
    }

    public void testLayoutFallbackIncludesJavaPackagesAndResourceAssets() throws IOException {
        assertCustomFolderIcon("shared/src/jvmMain/java", "folders/folder-source.svg");
        assertCustomFolderIcon("shared/src/jvmTest/java/com/example", "folders/folder-test-package.svg");
        assertCustomFolderIcon("shared/src/commonTest/resources/fixtures", "folders/folder-fixtures.svg");
        assertFolderIcon("shared/src/commonMain/resources/assets", "folders/folder-assets.svg");
    }

    public void testSimilarNamesOutsideSourceLayoutsKeepTheirIcons() throws IOException {
        assertFolderIcon("docs/commonMain", "folders/folder.svg");
        assertFolderIcon("docs/commonMain/kotlin", "folders/folder.svg");
        assertFolderIcon("src/mainly", "folders/folder.svg");
        assertFolderIcon("src/commonMain/config", "folders/folder-config.svg");
    }

    public void testImportedRootRolesOverrideMultiplatformLayoutGuesses() throws IOException {
        var source = myFixture.getTempDirFixture().findOrCreateDir("shared/src/commonTest/kotlin");
        PsiTestUtil.addSourceRoot(getModule(), source, JavaSourceRootType.SOURCE);
        assertCustomFolderIcon("shared/src/commonTest/kotlin", "folders/folder-source.svg");
        assertCustomFolderIcon("shared/src/commonTest/kotlin/com/example", "folders/folder-package.svg");
    }

    public void testAlternateSourceRootTypesUseTheirSharedPropertiesContract() {
        // Kotlin Multiplatform uses its own root types with JavaSourceRootProperties.
        // Exercise that public contract without requiring the Kotlin plugin in the test IDE.
        for (boolean test : new boolean[]{false, true}) {
            var type = new AlternateSourceRootType(test);
            var source = JpsElementFactory.getInstance().createModuleSourceRoot("file:///imported/code", type, type.createDefaultProperties());
            assertEquals(test ? ProjectFolderIcons.Role.TEST_SOURCE : ProjectFolderIcons.Role.SOURCE,
                    ProjectFolderIcons.sourceRole(source, true));
            assertEquals(test ? ProjectFolderIcons.Role.TEST_PACKAGE : ProjectFolderIcons.Role.PACKAGE,
                    ProjectFolderIcons.sourceRole(source, false));
        }
    }

    public void testAlternateResourceRootTypesUseTheirSharedPropertiesContract() {
        for (boolean test : new boolean[]{false, true}) {
            var type = new AlternateResourceRootType(test);
            var source = JpsElementFactory.getInstance().createModuleSourceRoot("file:///imported/data", type, type.createDefaultProperties());
            assertEquals(test ? ProjectFolderIcons.Role.TEST_RESOURCES : ProjectFolderIcons.Role.RESOURCES,
                    ProjectFolderIcons.sourceRole(source, true));
            assertNull(ProjectFolderIcons.sourceRole(source, false));
        }
    }

    public void testGeneratedAndExcludedMultiplatformRootsKeepNativeIcons() throws IOException {
        var generated = myFixture.getTempDirFixture().findOrCreateDir("shared/src/commonMain/kotlin");
        var type = JavaSourceRootType.SOURCE;
        var properties = type.createDefaultProperties();
        properties.setForGeneratedSources(true);
        PsiTestUtil.addSourceRoot(getModule(), generated, type, properties);
        assertNativeFolder("shared/src/commonMain/kotlin");
        assertNativeFolder("shared/src/commonMain/kotlin/com/example");
        var excluded = myFixture.getTempDirFixture().findOrCreateDir("other/src/iosTest");
        PsiTestUtil.addExcludedRoot(getModule(), excluded);
        assertNativeFolder("other/src/iosTest/kotlin");
    }

    private static final class AlternateSourceRootType extends JpsElementTypeBase<JavaSourceRootProperties>
            implements JpsModuleSourceRootType<JavaSourceRootProperties> {
        private final boolean test;

        private AlternateSourceRootType(boolean test) {
            this.test = test;
        }

        @Override
        public boolean isForTests() {
            return test;
        }

        @Override
        public JavaSourceRootProperties createDefaultProperties() {
            return JavaSourceRootType.SOURCE.createDefaultProperties();
        }
    }

    private static final class AlternateResourceRootType extends JpsElementTypeBase<JavaResourceRootProperties>
            implements JpsModuleSourceRootType<JavaResourceRootProperties> {
        private final boolean test;

        private AlternateResourceRootType(boolean test) {
            this.test = test;
        }

        @Override
        public boolean isForTests() {
            return test;
        }

        @Override
        public JavaResourceRootProperties createDefaultProperties() {
            return JavaResourceRootType.RESOURCE.createDefaultProperties();
        }
    }
}
