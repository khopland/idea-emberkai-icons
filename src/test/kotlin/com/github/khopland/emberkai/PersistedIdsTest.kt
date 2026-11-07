package com.github.khopland.emberkai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The code moved to `com.github.khopland.emberkai`, but users' installs, keymaps, toolbars, and saved
 * widget settings refer to the original IDs. Renaming one silently drops that customization, so every
 * ID the descriptors declare must keep the `com.github.khopland.symbols` prefix.
 */
internal class PersistedIdsTest {
    private val descriptors = File("src/main/resources/META-INF").listFiles { file -> file.extension == "xml" }.orEmpty()

    @Test
    fun testPluginIdIsUnchanged() {
        val plugin = File("src/main/resources/META-INF/plugin.xml").readText()
        assertTrue(plugin.contains("<id>$PREFIX</id>"))
    }

    @Test
    fun testDeclaredIdsKeepTheOriginalPrefix() {
        assertTrue("No plugin descriptors found", descriptors.isNotEmpty())
        val ids = descriptors.flatMap { file -> DECLARED_ID.findAll(file.readText()).map { file.name to it.groupValues[2] } }
        assertTrue("No IDs found", ids.size > 10)
        assertEquals(emptyList<Pair<String, String>>(), ids.filterNot { (_, id) -> id == PREFIX || id.startsWith("$PREFIX.") })
    }

    private companion object {
        const val PREFIX = "com.github.khopland.symbols"

        /** Action, group, configurable, and widget IDs, extension point names, and extension namespaces. */
        val DECLARED_ID = Regex("""\b(id|qualifiedName|defaultExtensionNs)="(com\.github\.khopland\.[^"]+)"""")
    }
}
