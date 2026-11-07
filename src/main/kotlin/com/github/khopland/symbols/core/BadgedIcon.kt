package com.github.khopland.symbols.core

import com.intellij.ui.icons.IconReplacer
import com.intellij.ui.icons.ReplaceableIcon
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Point2D
import javax.swing.Icon

/**
 * The corner a badge paints in. Each corner means one thing and holds at most one badge, so a
 * 16px icon never carries more than the artwork and two small marks. Native Java and Kotlin class
 * icons keep the top-left corner for their own marks and class stereotypes.
 */
internal enum class BadgeSlot {
    /** Top-left: the role a source file plays, such as a controller, on artwork that is not a native class icon. */
    ROLE,

    /** Top-right: the one thing to act on or that is live now; the most urgent badge wins. */
    ATTENTION,

    /** Bottom-left: the environment a configuration file or folder belongs to. */
    ENVIRONMENT,

    /** Bottom-right: part of the artwork, where Symbols folder glyphs and Kubernetes kinds already sit. */
    ARTWORK,
}

/**
 * Base artwork with a small painted badge. The badge never changes the icon's size, and icon
 * replacements such as dark or light variants reach the base artwork, so previews and themed
 * renderings swap the artwork while keeping the badge.
 */
internal abstract class BadgedIcon(val baseIcon: Icon) : ReplaceableIcon {
    abstract val slot: BadgeSlot

    /** Decides which badge a contested [slot] shows: the higher one is drawn. */
    open val urgency: Int get() = 0

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

    final override fun replaceBy(replacer: IconReplacer): Icon = replaced(replacer)

    /** This badge with [replacer] applied; badges that paint artwork of their own replace that too. */
    protected open fun replaced(replacer: IconReplacer): BadgedIcon = withBase(replacer.replaceIcon(baseIcon))

    /**
     * This badge if it wins its [slot], with any weaker badge in that slot removed, or the base icon
     * unchanged when the slot already shows a badge at least as urgent.
     */
    fun claimSlot(): Icon {
        val rival = generateSequence(baseIcon as? BadgedIcon) { it.baseIcon as? BadgedIcon }
            .firstOrNull { it.slot == slot } ?: return this
        return if (rival.urgency >= urgency) baseIcon else withBase(without(baseIcon, rival))
    }

    /** Paints over the base artwork at ([x], [y]); antialiasing is already on. */
    protected abstract fun paintBadge(g: Graphics2D, x: Int, y: Int)

    /** The same badge over [base]. */
    protected abstract fun withBase(base: Icon): BadgedIcon

    /** The top-left point of a [size]-pixel badge drawn [inset] pixels inside this badge's [slot] corner. */
    protected fun slotOrigin(x: Int, y: Int, size: Float, inset: Float): Point2D.Float {
        val right = x + iconWidth - size - inset
        val bottom = y + iconHeight - size - inset
        return when (slot) {
            BadgeSlot.ROLE -> Point2D.Float(x + inset, y + inset)
            BadgeSlot.ATTENTION -> Point2D.Float(right, y + inset)
            BadgeSlot.ENVIRONMENT -> Point2D.Float(x + inset, bottom)
            BadgeSlot.ARTWORK -> Point2D.Float(right, bottom)
        }
    }

    private fun without(icon: Icon, layer: BadgedIcon): Icon = when {
        icon === layer -> layer.baseIcon
        icon is BadgedIcon -> icon.withBase(without(icon.baseIcon, layer))
        else -> icon
    }

    /** [urgency] for the [BadgeSlot.ATTENTION] badges, least urgent first. */
    companion object {
        const val MODULE_ACCENT = 0
        const val LIVE = 1
        const val WARNING = 2
        const val EXPOSED_SECRET = 3
    }
}
