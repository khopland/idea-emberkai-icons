package com.github.khopland.symbols.run

import com.github.khopland.symbols.badges.BadgedIcon
import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import javax.swing.Icon

/**
 * Marks a module folder with a live run or debug session: a solid core inside an outer pulse ring,
 * in the top-right "live" corner. The solid core keeps it distinct from the Spring profile marker's
 * hollow ring-and-dot, which only appears on files, when color is reduced or removed.
 */
internal class RunningServiceIcon(
    baseIcon: Icon,
    internal val debug: Boolean,
    private val highContrast: Boolean = false,
    private val monochrome: Boolean = false,
) : BadgedIcon(baseIcon) {
    override fun paintBadge(g: Graphics2D, x: Int, y: Int) {
        val size = minOf(7f, iconWidth / 2f, iconHeight / 2f)
        val markerX = x + iconWidth - size - 0.5f
        val markerY = y + 0.5f
        val ring = Ellipse2D.Float(markerX, markerY, size, size)
        val coreSize = size * 0.5f
        val core = Ellipse2D.Float(
            markerX + (size - coreSize) / 2, markerY + (size - coreSize) / 2, coreSize, coreSize,
        )
        val color = when {
            monochrome || highContrast -> INK
            debug -> DEBUG
            else -> RUNNING
        }
        // The halo separates the ring from module accents and folder outlines in both themes.
        g.color = HALO
        g.fill(ring)
        g.stroke = BasicStroke(if (highContrast) 2.6f else 1.8f)
        g.draw(ring)
        g.color = color
        g.stroke = BasicStroke(if (highContrast) 1.4f else 1f)
        g.draw(ring)
        g.fill(core)
    }

    override fun withBase(base: Icon): BadgedIcon = RunningServiceIcon(base, debug, highContrast, monochrome)

    private companion object {
        val RUNNING: Color = JBColor(Color(0x15, 0x80, 0x3D), Color(0x4A, 0xDE, 0x80))
        val DEBUG: Color = JBColor(Color(0xB4, 0x53, 0x09), Color(0xFB, 0xBF, 0x24))
        val INK: Color = JBColor(Color(0x0F, 0x17, 0x2A), Color(0xE2, 0xE8, 0xF0))
        val HALO: Color = JBColor(Color.WHITE, Color(0x1E, 0x1F, 0x22))
    }
}
