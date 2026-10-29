package com.github.khopland.symbols.spring

import com.github.khopland.symbols.badges.BadgedIcon
import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Arc2D
import javax.swing.Icon

/** Emberkai folder artwork with a small Spring Modulith lock badge in the lower-right corner. */
internal class ModulithFolderIcon(
    baseIcon: Icon,
    internal val lock: ModulithLock,
) : BadgedIcon(baseIcon) {
    override fun paintBadge(g: Graphics2D, x: Int, y: Int) {
        val size = (minOf(iconWidth, iconHeight) * 0.5f).coerceIn(7f, 9f)
        paintLock(g, x + iconWidth - size, y + iconHeight - size, size)
    }

    override fun withBase(base: Icon): BadgedIcon = ModulithFolderIcon(base, lock)

    private fun paintLock(g: Graphics2D, x: Float, y: Float, size: Float) {
        val bodyX = x + size * 0.16f
        val bodyY = y + size * 0.46f
        val bodyWidth = size * 0.68f
        val bodyHeight = size * 0.42f
        val radius = (size * 0.22f).toInt().coerceAtLeast(1)
        val arcWidth = size * 0.4f
        val arcHeight = size * 0.56f
        val arcX = x + (size - arcWidth) / 2f
        val arcY = y + size * 0.14f

        // Halo first so the badge separates from both light and dark folder outlines.
        g.color = HALO
        g.stroke = BasicStroke(size * 0.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(Arc2D.Float(arcX, arcY, arcWidth, arcHeight, 0f, 180f, Arc2D.OPEN))
        val pad = size * 0.1f
        g.fillRoundRect(
            (bodyX - pad).toInt(), (bodyY - pad).toInt(),
            (bodyWidth + pad * 2).toInt(), (bodyHeight + pad * 2).toInt(), radius, radius,
        )

        g.color = lock.color
        g.stroke = BasicStroke(size * 0.16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(Arc2D.Float(arcX, arcY, arcWidth, arcHeight, 0f, 180f, Arc2D.OPEN))
        g.fillRoundRect(
            bodyX.toInt(), bodyY.toInt(),
            bodyWidth.toInt(), bodyHeight.toInt(), radius, radius,
        )
    }

    private companion object {
        val HALO: Color = JBColor(Color(0xFFFFFF), Color(0x1E1F22))
    }
}
