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

/** What is out of date in a Chart.lock. */
internal data class LockDriftFact(val problems: List<String>) : IconFact {
    override val label: String get() = "out of date with Chart.yaml"
    override val tooltipOrder: Int get() = 60
}

/** Warns about a Chart.lock that `helm dependency build` would reject as out of sync with Chart.yaml. */
internal class HelmLockDriftDecorator : IconDecorator {
    override fun decorateFile(resolution: IconResolution, file: VirtualFile, project: Project?): IconResolution {
        val settings = EmberkaiIconsSettings.getInstance()
        if (!settings.isEnabled(IconRuleCategory.LOCKFILE_DRIFT)) return resolution
        val problems = HelmLockDrift.problems(file).takeIf { it.isNotEmpty() } ?: return resolution
        val baseIcon = resolution.iconOrFileTypeIcon(file) ?: return resolution
        return resolution.decorated(
            LockDriftBadgeIcon(baseIcon, settings.highContrastFolderIcons || settings.monochromeFolderIcons),
            LockDriftFact(problems),
            "A warning badge marks a lock file that no longer matches Chart.yaml: " +
                "${problems.joinToString("; ")}. Run helm dependency update to refresh it.",
        )
    }
}
