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

/** Keys this config variant lacks compared with its siblings. */
internal data class ConfigDriftFact(val drift: ConfigDrift) : IconFact {
    override val label: String
        get() = drift.missing.size.let { if (it == 1) "missing ${drift.missing.keys.single()}" else "missing $it keys" }
    override val tooltipOrder: Int get() = 62
}

/** Warns about a profile, values, or `.env` variant that lacks keys every sibling variant defines. */
internal class ConfigDriftDecorator : IconDecorator {
    override fun decorateFile(resolution: IconResolution, file: VirtualFile, project: Project?): IconResolution {
        val settings = EmberkaiIconsSettings.getInstance()
        if (!settings.isEnabled(IconRuleCategory.CONFIG_DRIFT)) return resolution
        val drift = ConfigVariants.of(file) ?: return resolution
        val baseIcon = resolution.iconOrFileTypeIcon(file) ?: return resolution
        val listed = drift.missing.entries.take(MAX_LISTED).joinToString("; ") { (key, files) -> "$key (in ${files.joinToString()})" }
        val more = (drift.missing.size - MAX_LISTED).takeIf { it > 0 }?.let { " and $it more" }.orEmpty()
        return resolution.decorated(
            ConfigDriftBadgeIcon(baseIcon, settings.highContrastFolderIcons || settings.monochromeFolderIcons),
            ConfigDriftFact(drift),
            "A warning badge marks a config variant that lacks keys every sibling variant defines: $listed$more.",
        )
    }

    private companion object {
        const val MAX_LISTED = 10
    }
}
