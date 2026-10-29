package com.github.khopland.symbols

import com.github.khopland.symbols.accents.ModuleAccentColor
import com.github.khopland.symbols.accents.ModuleAccentIcon
import com.github.khopland.symbols.badges.BadgedIcon
import com.github.khopland.symbols.badges.EnvironmentVariant
import com.github.khopland.symbols.badges.EnvironmentVariantBadgeIcon
import com.github.khopland.symbols.badges.UnignoredSecretBadgeIcon
import com.github.khopland.symbols.core.SymbolsIconProvider
import com.github.khopland.symbols.spring.ActiveSpringProfileIcon
import com.github.khopland.symbols.spring.ModulithFolderIcon
import com.github.khopland.symbols.spring.ModulithLock
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import javax.swing.Icon

internal class BadgedIconTest : BasePlatformTestCase() {
    private val base: Icon = IconLoader.getIcon("/emberkai/icons/folder-helm.svg", SymbolsIconProvider::class.java)

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
