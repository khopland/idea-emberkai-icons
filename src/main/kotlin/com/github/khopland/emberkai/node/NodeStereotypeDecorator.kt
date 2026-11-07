package com.github.khopland.emberkai.node

import com.github.khopland.emberkai.core.BadgeSlot
import com.github.khopland.emberkai.core.BadgedIcon
import com.github.khopland.emberkai.core.IconDecorator
import com.github.khopland.emberkai.core.IconFact
import com.github.khopland.emberkai.core.IconResolution
import com.github.khopland.emberkai.core.IconRuleCategory
import com.github.khopland.emberkai.core.decorated
import com.github.khopland.emberkai.core.iconOrFileTypeIcon
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.github.khopland.emberkai.settings.isEnabled
import com.github.khopland.emberkai.stereotypes.ClassStereotype
import com.github.khopland.emberkai.stereotypes.StereotypeMatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.icons.IconReplacer
import java.awt.Graphics2D
import java.util.Locale
import javax.swing.Icon

internal data class SourceStereotypeFact(val match: StereotypeMatch) : IconFact {
    override val label: String
        get() = "${match.stereotype.displayName.lowercase(Locale.ROOT)} (${match.evidence})"
    override val tooltipOrder: Int get() = 15
}

/**
 * The class stereotype glyph, which draws itself in the top-left corner, over file artwork. Icon
 * replacements, such as the dark variants a theme preview asks for, reach the glyph too.
 */
internal class StereotypeBadgeIcon(
    baseIcon: Icon,
    val stereotype: ClassStereotype,
    internal val glyph: Icon = stereotype.icon,
) : BadgedIcon(baseIcon) {
    override val slot: BadgeSlot get() = BadgeSlot.ROLE

    override fun paintBadge(g: Graphics2D, x: Int, y: Int) {
        // The glyph is drawn for a 16px icon; scaled artwork, such as a zoomed editor tab, scales it too.
        val scale = iconWidth.toDouble() / glyph.iconWidth
        if (scale == 1.0 || glyph.iconWidth <= 0) return glyph.paintIcon(null, g, x, y)
        val scaled = g.create() as Graphics2D
        try {
            scaled.translate(x, y)
            scaled.scale(scale, scale)
            glyph.paintIcon(null, scaled, 0, 0)
        } finally {
            scaled.dispose()
        }
    }

    override fun withBase(base: Icon): BadgedIcon = StereotypeBadgeIcon(base, stereotype, glyph)

    override fun replaced(replacer: IconReplacer): BadgedIcon =
        StereotypeBadgeIcon(replacer.replaceIcon(baseIcon), stereotype, replacer.replaceIcon(glyph))
}

/**
 * Badges TypeScript and JavaScript project sources with the same stereotype glyphs as Java and
 * Kotlin classes. Their icons are Emberkai artwork rather than native class icons, so the glyph is
 * painted over the file icon instead of added as an icon layer.
 */
internal class NodeStereotypeDecorator : IconDecorator {
    override fun decorateFile(resolution: IconResolution, file: VirtualFile, project: Project?): IconResolution {
        if (!NodeStereotypes.isCandidate(file)) return resolution
        if (!EmberkaiIconsSettings.getInstance().isEnabled(IconRuleCategory.STEREOTYPE_BADGES)) return resolution
        if (project == null || project.isDisposed) return resolution
        val match = NodeStereotypes.match(file, project) ?: return resolution
        val baseIcon = resolution.iconOrFileTypeIcon(file) ?: return resolution
        return resolution.decorated(
            StereotypeBadgeIcon(baseIcon, match.stereotype),
            SourceStereotypeFact(match),
            "The top-left glyph marks a ${match.stereotype.displayName.lowercase(Locale.ROOT)} (${match.evidence}).",
        )
    }
}
