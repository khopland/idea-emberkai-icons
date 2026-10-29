package com.github.khopland.symbols

import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.image.BufferedImage
import javax.swing.Icon

internal class BadgedIconTest : BasePlatformTestCase() {
    private val base: Icon = IconLoader.getIcon("/emberkai/icons/folder-helm.svg", SymbolsIconProvider::class.java)

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
