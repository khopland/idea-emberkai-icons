package com.github.khopland.symbols

import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import javax.swing.Icon

/**
 * Marks a Spring profile file that the selected run configuration activates. The ring-and-dot
 * "live" target sits in the top-right corner, away from the bottom-right environment badge, and
 * its shape stays distinct from the filled badge shapes when color is reduced or removed.
 */
internal class ActiveSpringProfileIcon(
    internal val baseIcon: Icon,
    private val highContrast: Boolean = false,
    private val monochrome: Boolean = false,
) : Icon {
    override fun getIconWidth(): Int = baseIcon.iconWidth

    override fun getIconHeight(): Int = baseIcon.iconHeight

    override fun paintIcon(component: Component?, graphics: Graphics, x: Int, y: Int) {
        baseIcon.paintIcon(component, graphics, x, y)
        val size = minOf(6.5f, iconWidth / 2f, iconHeight / 2f)
        val markerX = x + iconWidth - size - 0.75f
        val markerY = y + 0.75f
        val ring = Ellipse2D.Float(markerX, markerY, size, size)
        val dotSize = size * 0.38f
        val dot = Ellipse2D.Float(
            markerX + (size - dotSize) / 2, markerY + (size - dotSize) / 2, dotSize, dotSize,
        )
        val copy = graphics.create() as Graphics2D
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            // A halo keeps the ring readable over the base artwork in light and dark themes.
            copy.color = HALO
            copy.fill(ring)
            copy.stroke = BasicStroke(if (highContrast) 2.6f else 1.8f)
            copy.draw(ring)
            if (highContrast) {
                copy.color = INK
                copy.stroke = BasicStroke(2.2f)
                copy.draw(ring)
            }
            copy.color = if (monochrome) INK else LIVE
            copy.stroke = BasicStroke(1.3f)
            copy.draw(ring)
            copy.fill(dot)
        } finally {
            copy.dispose()
        }
    }

    private companion object {
        val LIVE: Color = JBColor(Color(0x08, 0x91, 0xB2), Color(0x22, 0xD3, 0xEE))
        val INK: Color = JBColor(Color(0x0F, 0x17, 0x2A), Color(0xE2, 0xE8, 0xF0))
        val HALO: Color = JBColor(Color.WHITE, Color(0x1E, 0x1F, 0x22))
    }
}
