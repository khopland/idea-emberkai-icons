package com.github.khopland.symbols

import com.github.khopland.symbols.core.MappingKeys
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** The contextual search stops walking parents where a mapping key can no longer exist. */
internal class MappingKeysTest : BasePlatformTestCase() {
    fun testContextualWalkStopsAtTheDeepestPossibleKey() {
        val file = myFixture.tempDirFixture.createFile("a/b/c/values.yaml", "")
        val keys = mutableListOf<String>()

        val match = MappingKeys.contextual(file.parent, "file.", "values.yaml", maxDepth = 2) {
            keys += it
            null
        }

        assertNull(match)
        assertEquals(listOf("file.c/values.yaml", "file.b/c/values.yaml"), keys)
    }

    fun testContextualWalkKeepsTheLongestMatch() {
        val file = myFixture.tempDirFixture.createFile("a/b/c/values.yaml", "")

        val match = MappingKeys.contextual(file.parent, "file.", "values.yaml", maxDepth = 2) { key ->
            key.takeIf { it.startsWith("file.b/") }
        }

        assertEquals("file.b/c/values.yaml", match)
    }
}
