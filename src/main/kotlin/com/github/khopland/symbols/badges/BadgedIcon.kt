package com.github.khopland.symbols.badges

import com.intellij.ui.icons.IconReplacer
import com.intellij.ui.icons.ReplaceableIcon
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

/**
 * Base artwork with a small painted badge. The badge never changes the icon's size, and icon
 * replacements such as dark or light variants reach the base artwork, so previews and themed
 * renderings swap the artwork while keeping the badge.
 */
internal abstract class BadgedIcon(val baseIcon: Icon) : ReplaceableIcon {
    final override fun getIconWidth(): Int = baseIcon.iconWidth

    final override fun getIconHeight(): Int = baseIcon.iconHeight

    final override fun paintIcon(component: Component?, g: Graphics, x: Int, y: Int) {
        baseIcon.paintIcon(component, g, x, y)
        val graphics = g.create() as Graphics2D
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            paintBadge(graphics, x, y)
        } finally {
            graphics.dispose()
        }
    }

    final override fun replaceBy(replacer: IconReplacer): Icon = withBase(replacer.replaceIcon(baseIcon))

    /** Paints over the base artwork at ([x], [y]); antialiasing is already on. */
    protected abstract fun paintBadge(g: Graphics2D, x: Int, y: Int)

    /** The same badge over [base]. */
    protected abstract fun withBase(base: Icon): BadgedIcon
}
