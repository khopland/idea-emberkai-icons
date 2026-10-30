package com.github.khopland.symbols.badges

import com.github.khopland.symbols.core.IconDecorator
import com.github.khopland.symbols.core.IconFact
import com.github.khopland.symbols.core.IconResolution
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.decorated
import com.github.khopland.symbols.core.iconOrFileTypeIcon
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.isEnabled
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal data object ExposedSecretFact : IconFact {
    override val label: String get() = "not ignored by VCS — may contain secrets"
    override val tooltipOrder: Int get() = 90
}

/** Warns about likely credentials that VCS would pick up; the name check runs before any VCS lookup. */
internal class UnignoredSecretDecorator : IconDecorator {
    override fun decorateFile(resolution: IconResolution, file: VirtualFile, project: Project?): IconResolution {
        val settings = EmberkaiIconsSettings.getInstance()
        if (!settings.isEnabled(IconRuleCategory.UNIGNORED_SECRETS) || !SecretFileNames.isCandidate(file)) {
            return resolution
        }
        if (project == null || project.isDisposed || project.isDefault) return resolution
        val exposure = SecretFileExposure.getInstance(project) ?: return resolution
        if (!exposure.isExposed(file)) return resolution
        val baseIcon = resolution.iconOrFileTypeIcon(file) ?: return resolution
        return resolution.decorated(
            UnignoredSecretBadgeIcon(
                baseIcon,
                highContrast = settings.highContrastFolderIcons || settings.monochromeFolderIcons,
            ),
            ExposedSecretFact,
            "A red warning badge marks a file that usually holds credentials " +
                "or private keys and is inside a VCS root without being ignored, so it could be committed. " +
                "Ignore it in VCS (for example in .gitignore) to clear the badge.",
        )
    }
}
