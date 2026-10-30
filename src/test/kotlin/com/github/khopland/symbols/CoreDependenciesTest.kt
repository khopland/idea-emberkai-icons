package com.github.khopland.symbols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the icon engine separable from the features built on it. `core` may use the packages that
 * choose a base icon (folder roles, project overrides, detection rules, and settings); badges,
 * markers, and tooltips plug in through [com.github.khopland.symbols.core.IconDecorator] instead.
 */
internal class CoreDependenciesTest {
    private val allowed = setOf("core", "folders", "overrides", "rules", "settings")

    @Test
    fun testCoreImportsOnlyEnginePackages() {
        val core = File("src/main/kotlin/com/github/khopland/symbols/core")
        val sources = core.listFiles { file -> file.extension == "kt" }.orEmpty()
        assertTrue("No core sources found in ${core.absolutePath}", sources.isNotEmpty())
        val violations = sources.sortedBy { it.name }.flatMap { file ->
            file.readLines()
                .mapNotNull { IMPORT.matchEntire(it.trim()) }
                .filter { it.groupValues[1] !in allowed }
                .map { "${file.name}: ${it.value}" }
        }
        assertEquals("core imports feature packages", emptyList<String>(), violations)
    }

    private companion object {
        val IMPORT = Regex("""import com\.github\.khopland\.symbols\.([a-z]+)\..*""")
    }
}
