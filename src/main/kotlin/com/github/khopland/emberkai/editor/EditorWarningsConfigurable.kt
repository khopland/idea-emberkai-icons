package com.github.khopland.emberkai.editor

import com.github.khopland.emberkai.core.RefreshProjectIconPresentations
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

/** Settings > Appearance & Behavior > Emberkai Icons > Editor Warnings. */
class EditorWarningsConfigurable : BoundConfigurable("Editor Warnings") {
    override fun createPanel(): DialogPanel {
        val settings = EmberkaiEditorSettings.getInstance()
        return panel {
            group("Production files") {
                row {
                    comment(
                        "Files with the production environment badge, by name or inherited from a folder " +
                            "such as overlays/production/.",
                    )
                }
                row {
                    checkBox("Show a warning banner that says why the file counts as production")
                        .bindSelected(settings::productionBanner)
                }
                row {
                    checkBox("Tint the editor tab red")
                        .bindSelected(settings::productionTabTint)
                }
            }
        }
    }

    override fun apply() {
        val changed = isModified
        super.apply()
        if (changed) RefreshProjectIconPresentations.refresh()
    }
}
