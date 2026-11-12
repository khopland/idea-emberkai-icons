package com.github.khopland.emberkai.badges

import com.github.khopland.emberkai.accents.ModuleAccentColor
import com.github.khopland.emberkai.accents.ModuleAccentIcon
import com.github.khopland.emberkai.core.BadgedIcon
import com.github.khopland.emberkai.core.BadgeSlot
import com.github.khopland.emberkai.core.EmberkaiIconProvider
import com.github.khopland.emberkai.spring.ActiveSpringProfileIcon
import com.github.khopland.emberkai.spring.ModulithFolderIcon
import com.github.khopland.emberkai.spring.ModulithLock
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import javax.swing.Icon

internal class BadgedIconTest : BasePlatformTestCase() {
    private val base: Icon = IconLoader.getIcon("/emberkai/icons/folder-helm.svg", EmberkaiIconProvider::class.java)

    // Headless runs (CI) leave the icon loader inactive, so every icon paints as a 1x1 placeholder.
    private val activatesIconLoader = GraphicsEnvironment.isHeadless()

    override fun setUp() {
        super.setUp()
        if (activatesIconLoader) IconLoader.activate()
    }

    override fun tearDown() {
        try {
            if (activatesIconLoader) IconLoader.deactivate()
        } finally {
            super.tearDown()
        }
    }

    fun testDarkVariantsReplaceTheBaseArtworkAndKeepTheBadge() {
        val darkPixels = pixels(IconLoader.getDarkIcon(base, true))
        assertFalse("The fixture artwork must have a distinct dark variant", pixels(base).contentEquals(darkPixels))

        val badges: List<BadgedIcon> = listOf(
            ModuleAccentIcon(base, ModuleAccentColor.GREEN),
            EnvironmentVariantBadgeIcon(base, EnvironmentVariant.PRODUCTION),
            UnignoredSecretBadgeIcon(base, highContrast = false),
            ActiveSpringProfileIcon(base),
            ModulithFolderIcon(base, ModulithLock.PUBLIC),
        )
        for (badge in badges) {
            val name = badge.javaClass.simpleName
            val dark = IconLoader.getDarkIcon(badge, true) as? BadgedIcon
            assertNotNull("$name should stay badged", dark)
            assertSame(badge.javaClass, dark!!.javaClass)
            assertTrue("$name should swap in the dark base", pixels(dark.baseIcon).contentEquals(darkPixels))
        }
    }

    fun testEachCornerShowsOnlyItsMostUrgentBadge() {
        val accented = ModuleAccentIcon(base, ModuleAccentColor.GREEN).claimSlot()
        val warned = ConfigDriftBadgeIcon(accented, highContrast = false).claimSlot()
        assertSame("A warning replaces the accent dot", base, (warned as BadgedIcon).baseIcon)

        val environment = EnvironmentVariantBadgeIcon(warned, EnvironmentVariant.PRODUCTION).claimSlot()
        assertSame("The environment has its own corner", warned, (environment as BadgedIcon).baseIcon)

        assertSame("A live marker yields to a warning", environment, ActiveSpringProfileIcon(environment).claimSlot())
        assertSame("Equal warnings draw one triangle", environment, UnpinnedReferenceBadgeIcon(environment, false).claimSlot())

        val secret = UnignoredSecretBadgeIcon(environment, highContrast = false).claimSlot()
        assertEquals(listOf(UnignoredSecretBadgeIcon::class, EnvironmentVariantBadgeIcon::class), layers(secret))
        assertEquals(listOf(BadgeSlot.ATTENTION, BadgeSlot.ENVIRONMENT), badges(secret).map { it.slot })
    }

    fun testOverlaysLeaveTheArtworksOwnCornerBadge() {
        val deployment = IconLoader.getIcon("/emberkai/icons/kubernetes/deployment.svg", EmberkaiIconProvider::class.java)
        val kindBadge = corner(pixels(deployment), deployment.iconWidth)
        val overlays: List<BadgedIcon> = listOf(
            EnvironmentVariantBadgeIcon(deployment, EnvironmentVariant.PRODUCTION),
            UnignoredSecretBadgeIcon(deployment, highContrast = false),
            ConfigDriftBadgeIcon(deployment, highContrast = false),
        )
        for (overlay in overlays) {
            assertTrue(
                "${overlay.javaClass.simpleName} must not cover the Kubernetes kind badge",
                corner(pixels(overlay), overlay.iconWidth).contentEquals(kindBadge),
            )
        }
    }

    private fun badges(icon: Icon): List<BadgedIcon> =
        generateSequence(icon as? BadgedIcon) { it.baseIcon as? BadgedIcon }.toList()

    private fun layers(icon: Icon) = badges(icon).map { it::class }

    /** The bottom-right pixels from (10, 10), where artwork such as Kubernetes kinds draws its own badge. */
    private fun corner(pixels: IntArray, width: Int): IntArray =
        pixels.filterIndexed { index, _ -> index % width >= 10 && index / width >= 10 }.toIntArray()

    private fun pixels(icon: Icon): IntArray {
        val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            icon.paintIcon(null, graphics, 0, 0)
        } finally {
            graphics.dispose()
        }
        return image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    }
}
