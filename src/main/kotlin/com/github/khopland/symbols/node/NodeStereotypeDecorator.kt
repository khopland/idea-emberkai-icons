package com.github.khopland.symbols.node

import com.github.khopland.symbols.core.BadgeSlot
import com.github.khopland.symbols.core.BadgedIcon
import com.github.khopland.symbols.core.IconDecorator
import com.github.khopland.symbols.core.IconFact
import com.github.khopland.symbols.core.IconResolution
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.decorated
import com.github.khopland.symbols.core.iconOrFileTypeIcon
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.isEnabled
import com.github.khopland.symbols.stereotypes.ClassStereotype
import com.github.khopland.symbols.stereotypes.StereotypeMatch
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
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
        glyph.paintIcon(null, g, x, y)
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
        if (project == null || project.isDisposed || !ProjectFileIndex.getInstance(project).isInContent(file)) {
            return resolution
        }
        val match = NodeStereotypes.match(file, project) ?: return resolution
        val baseIcon = resolution.iconOrFileTypeIcon(file) ?: return resolution
        return resolution.decorated(
            StereotypeBadgeIcon(baseIcon, match.stereotype),
            SourceStereotypeFact(match),
            "The top-left glyph marks a ${match.stereotype.displayName.lowercase(Locale.ROOT)} (${match.evidence}).",
        )
    }
}
