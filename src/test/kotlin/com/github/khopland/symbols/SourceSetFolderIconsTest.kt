package com.github.khopland.symbols

import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.ex.JpsElementTypeBase
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType

internal class SourceSetFolderIconsTest : IconProviderTestCase() {
    fun testMultiplatformProductionLayoutsBeforeImport() {
        for (name in arrayOf(
            "commonMain", "jvmMain", "iosMain", "androidMain", "nativeMain", "wasmJsMain", "desktopMain",
        )) {
            val path = "shared/src/$name"
            assertCustomFolderIcon(path, "folders/folder-content.svg")
            assertCustomFolderIcon("$path/kotlin", "folders/folder-source.svg")
            assertCustomFolderIcon("$path/kotlin/com/example", "folders/folder-package.svg")
            assertCustomFolderIcon("$path/resources", "folders/folder-resources.svg")
        }
    }

    fun testMultiplatformTestLayoutsBeforeImport() {
        for (name in arrayOf("commonTest", "jvmTest", "iosTest", "androidUnitTest", "wasmJsTest")) {
            val path = "shared/src/$name"
            assertCustomFolderIcon(path, "folders/folder-content.svg")
            assertCustomFolderIcon("$path/kotlin", "folders/folder-test-source.svg")
            assertCustomFolderIcon("$path/kotlin/com/example", "folders/folder-test-package.svg")
            assertCustomFolderIcon("$path/resources", "folders/folder-test-resources.svg")
        }
    }

    fun testLayoutFallbackIncludesJavaPackagesAndResourceAssets() {
        assertCustomFolderIcon("shared/src/jvmMain/java", "folders/folder-source.svg")
        assertCustomFolderIcon("shared/src/jvmTest/java/com/example", "folders/folder-test-package.svg")
        assertCustomFolderIcon("shared/src/commonTest/resources/fixtures", "folders/folder-fixtures.svg")
        assertFolderIcon("shared/src/commonMain/resources/assets", "folders/folder-assets.svg")
    }

    fun testSimilarNamesOutsideSourceLayoutsKeepTheirIcons() {
        assertFolderIcon("docs/commonMain", "folders/folder.svg")
        assertFolderIcon("docs/commonMain/kotlin", "folders/folder.svg")
        assertFolderIcon("src/mainly", "folders/folder.svg")
        assertFolderIcon("src/commonMain/config", "folders/folder-config.svg")
    }

    fun testImportedRootRolesOverrideMultiplatformLayoutGuesses() {
        val source = myFixture.tempDirFixture.findOrCreateDir("shared/src/commonTest/kotlin")
        PsiTestUtil.addSourceRoot(module, source, JavaSourceRootType.SOURCE)
        assertCustomFolderIcon("shared/src/commonTest/kotlin", "folders/folder-source.svg")
        assertCustomFolderIcon("shared/src/commonTest/kotlin/com/example", "folders/folder-package.svg")
    }

    fun testAlternateSourceRootTypesUseTheirSharedPropertiesContract() {
        // Kotlin Multiplatform uses its own root types with JavaSourceRootProperties.
        // Exercise that public contract without requiring the Kotlin plugin in the test IDE.
        for (test in booleanArrayOf(false, true)) {
            val type = AlternateSourceRootType(test)
            val source = JpsElementFactory.getInstance()
                .createModuleSourceRoot("file:///imported/code", type, type.createDefaultProperties())
            assertEquals(
                if (test) ProjectFolderIcons.Role.TEST_SOURCE else ProjectFolderIcons.Role.SOURCE,
                ProjectFolderIcons.sourceRole(source, true),
            )
            assertEquals(
                if (test) ProjectFolderIcons.Role.TEST_PACKAGE else ProjectFolderIcons.Role.PACKAGE,
                ProjectFolderIcons.sourceRole(source, false),
            )
        }
    }

    fun testAlternateResourceRootTypesUseTheirSharedPropertiesContract() {
        for (test in booleanArrayOf(false, true)) {
            val type = AlternateResourceRootType(test)
            val source = JpsElementFactory.getInstance()
                .createModuleSourceRoot("file:///imported/data", type, type.createDefaultProperties())
            assertEquals(
                if (test) ProjectFolderIcons.Role.TEST_RESOURCES else ProjectFolderIcons.Role.RESOURCES,
                ProjectFolderIcons.sourceRole(source, true),
            )
            assertNull(ProjectFolderIcons.sourceRole(source, false))
        }
    }

    fun testGeneratedAndExcludedMultiplatformRootsKeepNativeIcons() {
        val generated = myFixture.tempDirFixture.findOrCreateDir("shared/src/commonMain/kotlin")
        val type = JavaSourceRootType.SOURCE
        val properties = type.createDefaultProperties()
        properties.isForGeneratedSources = true
        PsiTestUtil.addSourceRoot(module, generated, type, properties)
        assertNativeFolder("shared/src/commonMain/kotlin")
        assertNativeFolder("shared/src/commonMain/kotlin/com/example")
        val excluded = myFixture.tempDirFixture.findOrCreateDir("other/src/iosTest")
        PsiTestUtil.addExcludedRoot(module, excluded)
        assertNativeFolder("other/src/iosTest/kotlin")
    }

    private class AlternateSourceRootType(
        private val test: Boolean,
    ) : JpsElementTypeBase<JavaSourceRootProperties>(),
        JpsModuleSourceRootType<JavaSourceRootProperties> {
        override fun isForTests(): Boolean = test

        override fun createDefaultProperties(): JavaSourceRootProperties =
            JavaSourceRootType.SOURCE.createDefaultProperties()
    }

    private class AlternateResourceRootType(
        private val test: Boolean,
    ) : JpsElementTypeBase<JavaResourceRootProperties>(),
        JpsModuleSourceRootType<JavaResourceRootProperties> {
        override fun isForTests(): Boolean = test

        override fun createDefaultProperties(): JavaResourceRootProperties =
            JavaResourceRootType.RESOURCE.createDefaultProperties()
    }
}
