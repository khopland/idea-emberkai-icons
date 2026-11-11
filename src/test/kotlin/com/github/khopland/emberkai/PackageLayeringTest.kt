package com.github.khopland.emberkai

import junit.framework.TestCase
import java.io.File

/**
 * Keeps the packages in layers that can become Gradle modules: a package may import its own layer
 * and the layers below it, never one above. The features layer holds siblings that must not import
 * each other, so each can be split out alone.
 */
internal class PackageLayeringTest : TestCase() {
    private val layers: List<Set<String>> = listOf(
        // Settings state, the icon families users toggle, and the artwork palette.
        setOf("settings"),
        // Resolving file and folder icons.
        setOf("core", "rules", "folders", "accents", "overrides", "generated"),
        // Badges, markers, and the project facts behind them.
        setOf("badges", "spring", "run", "node", "ownership", "git", "tooltips", "stereotypes", "scopes"),
        // Features with their own settings pages.
        setOf("lens", "editor"),
        // Settings pages, menus, dialogs, and the status bar, which may use everything.
        setOf("ui", "audit", "statusbar"),
    )
    private val siblings = setOf("lens", "editor")
    private val root = File("src/main/kotlin/com/github/khopland/emberkai")
    private val importPattern = Regex("""^import com\.github\.khopland\.emberkai\.(\w+)\.""", RegexOption.MULTILINE)

    fun testEveryPackageHasALayer() {
        val packages = root.listFiles { file -> file.isDirectory }!!.map { it.name }.toSet()
        assertEquals("Add new packages to a layer", emptySet<String>(), packages - layers.flatten().toSet())
    }

    fun testNoPackageImportsALayerAboveItOrASibling() {
        val layerOf = layers.flatMapIndexed { index, layer -> layer.map { it to index } }.toMap()
        val violations = root.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            val from = file.parentFile.name
            importPattern.findAll(file.readText()).map { it.groupValues[1] }
                .filter { to -> to != from }
                .filter { to -> layerOf.getValue(to) > layerOf.getValue(from) || (from in siblings && to in siblings) }
                .map { to -> "${file.relativeTo(root)} imports $to" }
        }.toSortedSet()
        assertEquals(emptySet<String>(), violations)
    }
}
