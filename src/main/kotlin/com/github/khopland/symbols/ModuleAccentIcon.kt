package com.github.khopland.symbols

import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Graphics2D
import javax.swing.Icon

/**
 * Adds a small module-color dot to folder artwork. The badge is intentionally quieter than the
 * fully colored service-root icon so source roots and packages keep their role cues.
 */
internal class ModuleAccentIcon(
    baseIcon: Icon,
    val accent: ModuleAccentColor,
) : BadgedIcon(baseIcon) {
    private val accentColor: Color = JBColor(accent.awtColor(false), accent.awtColor(true))

    override fun paintBadge(g: Graphics2D, x: Int, y: Int) {
        val size = (minOf(iconWidth, iconHeight) * 0.32f).coerceIn(4f, 6f)
        val dotX = x + iconWidth - size - 0.5f
        val dotY = y + 0.5f
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
