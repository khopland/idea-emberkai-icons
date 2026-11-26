package com.github.khopland.emberkai.python

import com.github.khopland.emberkai.core.IconDecorator
import com.github.khopland.emberkai.core.IconResolution
import com.github.khopland.emberkai.core.decorated
import com.github.khopland.emberkai.core.iconOrFileTypeIcon
import com.github.khopland.emberkai.node.SourceStereotypeFact
import com.github.khopland.emberkai.node.StereotypeBadgeIcon
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.github.khopland.emberkai.settings.IconRuleCategory
import com.github.khopland.emberkai.settings.isEnabled
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/**
 * Badges Python sources with the same stereotype glyphs as TypeScript and JavaScript: a small glyph
 * in the top-left corner of the file icon, since Python modules have no class icon to replace.
 */
internal class PythonStereotypeDecorator : IconDecorator {
    override fun decorateFile(resolution: IconResolution, file: VirtualFile, project: Project?): IconResolution {
        if (!PythonStereotypes.isCandidate(file)) return resolution
        if (!EmberkaiIconsSettings.getInstance().isEnabled(IconRuleCategory.STEREOTYPE_BADGES)) return resolution
        if (project == null || project.isDisposed) return resolution
        val match = PythonStereotypes.match(file, project) ?: return resolution
        val baseIcon = resolution.iconOrFileTypeIcon(file) ?: return resolution
        return resolution.decorated(
            StereotypeBadgeIcon(baseIcon, match.stereotype),
            SourceStereotypeFact(match),
            "The top-left glyph marks a ${match.stereotype.displayName.lowercase(Locale.ROOT)} (${match.evidence}).",
        )
    }
}
