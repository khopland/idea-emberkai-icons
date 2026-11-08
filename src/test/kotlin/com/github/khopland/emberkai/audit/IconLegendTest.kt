package com.github.khopland.emberkai.audit

import com.github.khopland.emberkai.core.BadgeSlot
import com.github.khopland.emberkai.stereotypes.ClassStereotype
import com.intellij.icons.AllIcons
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.util.Locale

internal class IconLegendTest : BasePlatformTestCase() {
    private val samples by lazy {
        IconLegend.sampleBadges(AllIcons.FileTypes.Text, AllIcons.Nodes.Folder, contrast = false, highContrast = false, monochrome = false)
    }

    /** A badge that the legend does not show, or shows without a meaning, is one nobody can look up. */
    fun testEveryBadgeClassIsInTheLegendWithAMeaning() {
        val shown = samples.map { it.javaClass.simpleName }.toSet()
        val missing = concreteBadgeClasses() - shown
        assertTrue("Add these badges to IconLegend.sampleBadges and IconLegend.describe: $missing", missing.isEmpty())
        val undescribed = samples.filter { IconLegend.describe(it).second == "Undocumented badge." }
        assertTrue("Describe these badges in IconLegend.describe: $undescribed", undescribed.isEmpty())
    }

    fun testEveryCornerHasASectionAndEveryStereotypeIsListed() {
        val sections = IconLegend.sections()
        val stereotypes = sections.flatMap { it.entries }.map { it.name }
        ClassStereotype.entries.forEach { assertTrue(it.displayName, it.displayName in stereotypes) }
        BadgeSlot.entries.forEach { slot ->
            assertTrue(slot.displayName, sections.any { it.title.startsWith(slot.displayName) && it.entries.isNotEmpty() })
        }
    }

    /**
     * At 7px, color does most of the work, so two stereotypes that share a fill look alike. Test
     * stereotypes share one hue, told apart by shape, so a test never reads as production code.
     */
    fun testStereotypeGlyphsHaveTheirOwnColors() {
        val fills = ClassStereotype.entries.associateWith { stereotype ->
            val source = File("design/icons/stereotypes/${stereotype.name.lowercase(Locale.ROOT).replace('_', '-')}.svg")
            val fill = FILL.findAll(source.readText()).map { it.groupValues[1] }.firstOrNull { it.endsWith("-400") || it == "spring-green" }
            assertNotNull("${source.name} needs a -400 fill that names its stereotype color", fill)
            fill!!
        }
        val tests = ClassStereotype.entries.filter { it.name.endsWith("_TEST") }
        assertEquals("Test stereotypes share one hue", 1, tests.map(fills::getValue).toSet().size)
        val clashes = fills.entries.groupBy({ it.value }, { it.key })
            .filterValues { it.size > 1 && !tests.containsAll(it) }
        assertTrue("Stereotypes that share a fill: $clashes", clashes.isEmpty())
    }

    /** Concrete [com.github.khopland.emberkai.core.BadgedIcon] subclasses, read from the main sources. */
    private fun concreteBadgeClasses(): Set<String> {
        val declarations = File("src/main/kotlin").walk().filter { it.extension == "kt" }
            .flatMap { CLASS.findAll(it.readText()) }
            .associate { it.groupValues[2] to (it.groupValues[3] to it.groupValues[1].isNotEmpty()) }
        fun isBadge(name: String): Boolean = name == "BadgedIcon" || declarations[name]?.first?.let(::isBadge) == true
        return declarations.filter { (name, parent) -> !parent.second && name != "BadgedIcon" && isBadge(name) }.keys
    }

    private companion object {
        val FILL = Regex("""fill="([a-z0-9-]+)"""")
        val CLASS = Regex("""(abstract )?class (\w+)\((?:[^()]|\([^()]*\))*\)\s*:\s*(\w+)\(""")
    }
}
