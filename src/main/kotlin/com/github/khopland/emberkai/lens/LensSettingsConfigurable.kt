package com.github.khopland.emberkai.lens

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.panel

/** Settings > Appearance & Behavior > Emberkai Icons > Lenses. */
class LensSettingsConfigurable : BoundConfigurable("Lenses") {
    override fun createPanel(): DialogPanel {
        val settings = EmberkaiLensSettings.getInstance()
        return panel {
            row {
                comment("Choose a lens under View | Emberkai Icons | Lens or from the status bar.")
            }
            group("Hotspots") {
                row {
                    comment("The files changed in the most commits recently. Needs Git.")
                }
                row("Count commits from the last") {
                    spinner(EmberkaiLensSettings.DAYS_RANGE).bindIntValue(settings::hotspotDays)
                    label("days")
                }
                row("Keep the top") {
                    spinner(EmberkaiLensSettings.LIMIT_RANGE).bindIntValue(settings::hotspotLimit)
                    label("files, and every file tied with the last one")
                }
                row("Skip files with fewer than") {
                    spinner(EmberkaiLensSettings.MINIMUM_COMMITS_RANGE).bindIntValue(settings::hotspotMinimumCommits)
                    label("commits")
                }
            }
        }
    }

    override fun apply() {
        val changed = isModified
        super.apply()
        if (!changed) return
        // An active Hotspots lens recounts; an inactive one counts with the new values when chosen.
        for (project in ProjectManager.getInstance().openProjects) {
            if (!project.isDisposed) Hotspots.getInstance(project)?.settingsChanged()
        }
    }
}
