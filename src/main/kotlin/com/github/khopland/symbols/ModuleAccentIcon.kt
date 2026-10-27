package com.github.khopland.symbols

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

/**
 * Adds a small module-color dot to folder artwork. The badge is intentionally quieter than the
 * fully colored service-root icon so source roots and packages keep their role cues.
 */
internal class ModuleAccentIcon(
    private val base: Icon,
    val accent: ModuleAccentColor,
) : Icon {
    private val accentColor: Color = JBColor(accent.awtColor(false), accent.awtColor(true))

    override fun getIconWidth(): Int = base.iconWidth

    override fun getIconHeight(): Int = base.iconHeight

    override fun paintIcon(component: Component?, g: Graphics, x: Int, y: Int) {
        base.paintIcon(component, g, x, y)
        val graphics = g.create() as Graphics2D
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val size = (minOf(base.iconWidth, base.iconHeight) * 0.32f).coerceIn(4f, 6f)
            val dotX = x + base.iconWidth - size - 0.5f
            val dotY = y + 0.5f
            // Paint a halo first so the dot separates from both light and dark folder outlines.
            graphics.color = HALO
            graphics.fillOval((dotX - 1f).toInt(), (dotY - 1f).toInt(), (size + 2f).toInt(), (size + 2f).toInt())
            graphics.color = accentColor
            graphics.fillOval(dotX.toInt(), dotY.toInt(), size.toInt(), size.toInt())
        } finally {
            graphics.dispose()
        }
    }

    private companion object {
        val HALO: Color = JBColor(Color.WHITE, Color(0x1E1F22))
    }
}
