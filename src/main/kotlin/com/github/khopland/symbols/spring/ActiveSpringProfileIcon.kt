package com.github.khopland.symbols.spring

import com.github.khopland.symbols.core.BadgeSlot
import com.github.khopland.symbols.core.BadgedIcon
import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import javax.swing.Icon

/**
 * Marks a Spring profile file that the selected run configuration activates. The ring-and-dot
 * "live" target sits in the attention corner, below warnings and exposed secrets, and its shape
 * stays distinct from the filled badge shapes when color is reduced or removed.
 */
internal class ActiveSpringProfileIcon(
    baseIcon: Icon,
    private val highContrast: Boolean = false,
    private val monochrome: Boolean = false,
) : BadgedIcon(baseIcon) {
    override val slot: BadgeSlot get() = BadgeSlot.ATTENTION
    override val urgency: Int get() = BadgedIcon.LIVE

    override fun paintBadge(g: Graphics2D, x: Int, y: Int) {
        val size = minOf(6.5f, iconWidth / 2f, iconHeight / 2f)
        val origin = slotOrigin(x, y, size, inset = 0.75f)
        val markerX = origin.x
        val markerY = origin.y
        val ring = Ellipse2D.Float(markerX, markerY, size, size)
        val dotSize = size * 0.38f
        val dot = Ellipse2D.Float(
            markerX + (size - dotSize) / 2, markerY + (size - dotSize) / 2, dotSize, dotSize,
        )
        // A halo keeps the ring readable over the base artwork in light and dark themes.
        g.color = HALO
        g.fill(ring)
        g.stroke = BasicStroke(if (highContrast) 2.6f else 1.8f)
        g.draw(ring)
        if (highContrast) {
            g.color = INK
            g.stroke = BasicStroke(2.2f)
            g.draw(ring)
        }
        g.color = if (monochrome) INK else LIVE
        g.stroke = BasicStroke(1.3f)
        g.draw(ring)
        g.fill(dot)
    }

    override fun withBase(base: Icon): BadgedIcon = ActiveSpringProfileIcon(base, highContrast, monochrome)

    private companion object {
        val LIVE: Color = JBColor(Color(0x08, 0x91, 0xB2), Color(0x22, 0xD3, 0xEE))
        val INK: Color = JBColor(Color(0x0F, 0x17, 0x2A), Color(0xE2, 0xE8, 0xF0))
        val HALO: Color = JBColor(Color.WHITE, Color(0x1E, 0x1F, 0x22))
    }
}
