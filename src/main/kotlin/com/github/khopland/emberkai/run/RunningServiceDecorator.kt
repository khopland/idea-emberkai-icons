package com.github.khopland.emberkai.run

import com.github.khopland.emberkai.core.IconDecorator
import com.github.khopland.emberkai.core.IconFact
import com.github.khopland.emberkai.core.IconResolution
import com.github.khopland.emberkai.core.IconRuleCategory
import com.github.khopland.emberkai.core.decorated
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.github.khopland.emberkai.settings.isEnabled
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Live run and debug sessions owned by this module folder. */
internal data class RunningSessionsFact(val sessions: List<RunningServices.Session>) : IconFact {
    override val label: String get() = sessions.joinToString(", ") { it.describe() }
    override val tooltipOrder: Int get() = 70
}

/** A module folder with a live run or debug session carries the running marker, in place of its accent dot. */
internal class RunningServiceDecorator : IconDecorator {
    override fun decorateFolder(resolution: IconResolution, directory: VirtualFile, project: Project?): IconResolution {
        if (project == null || project.isDisposed) return resolution
        val sessions = RunningServices.getIfCreated(project)?.sessionsFor(directory)?.takeIf { it.isNotEmpty() }
            ?: return resolution
        val settings = EmberkaiIconsSettings.getInstance()
        if (!settings.isEnabled(IconRuleCategory.RUNNING_SERVICES)) return resolution
        val icon = resolution.icon ?: return resolution
        return resolution.decorated(
            RunningServiceIcon(
                icon,
                debug = sessions.all { it.debug },
                highContrast = settings.highContrastFolderIcons,
                monochrome = settings.monochromeFolderIcons,
            ),
            RunningSessionsFact(sessions),
            "A running marker shows a live session: " + sessions.joinToString("; ") { it.describe() } + ".",
        )
    }
}
