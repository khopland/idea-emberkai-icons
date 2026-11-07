package com.github.khopland.emberkai.accents

import com.github.khopland.emberkai.core.BadgeSlot
import com.github.khopland.emberkai.core.BadgedIcon
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Graphics2D
import javax.swing.Icon

/**
 * Adds a small module-color dot to folder artwork. The badge is intentionally quieter than the
 * fully colored service-root icon so source roots and packages keep their role cues, and it is the
 * least urgent attention badge, so any other one replaces it.
 */
internal class ModuleAccentIcon(
    baseIcon: Icon,
    val accent: ModuleAccentColor,
) : BadgedIcon(baseIcon) {
    override val slot: BadgeSlot get() = BadgeSlot.ATTENTION
    override val urgency: Int get() = BadgedIcon.MODULE_ACCENT

    // Resolved at paint time, so a palette change shows without rebuilding decorated icons.
    private val accentColor: Color = JBColor.lazy { JBColor(accent.awtColor(false), accent.awtColor(true)) }

    override fun paintBadge(g: Graphics2D, x: Int, y: Int) {
        val size = (minOf(iconWidth, iconHeight) * 0.32f).coerceIn(4f, 6f)
        val origin = slotOrigin(x, y, size, inset = 0.5f)
        val dotX = origin.x
        val dotY = origin.y
        // Paint a halo first so the dot separates from both light and dark folder outlines.
        g.color = HALO
        g.fillOval((dotX - 1f).toInt(), (dotY - 1f).toInt(), (size + 2f).toInt(), (size + 2f).toInt())
        g.color = accentColor
        g.fillOval(dotX.toInt(), dotY.toInt(), size.toInt(), size.toInt())
    }

    override fun withBase(base: Icon): BadgedIcon = ModuleAccentIcon(base, accent)

    private companion object {
        val HALO: Color = JBColor(Color.WHITE, Color(0x1E1F22))
    }
}
