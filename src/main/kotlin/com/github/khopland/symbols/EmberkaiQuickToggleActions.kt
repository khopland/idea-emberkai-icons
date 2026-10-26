package com.github.khopland.symbols

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

abstract class EmberkaiSettingsToggle(text: String) : ToggleAction(text), DumbAware {
    protected val settings: EmberkaiIconsSettings
        get() = EmberkaiIconsSettings.getInstance()

    final override fun isSelected(event: AnActionEvent): Boolean = selected()

    final override fun setSelected(event: AnActionEvent, state: Boolean) {
        if (selected() == state) return
        update(state)
        RefreshProjectIconPresentations.refresh()
    }

    protected abstract fun selected(): Boolean
    protected abstract fun update(state: Boolean)
}

class ToggleServiceFolderColorsAction : EmberkaiSettingsToggle("Color service folders") {
    override fun selected(): Boolean = settings.colorServiceFolders
    override fun update(state: Boolean) { settings.colorServiceFolders = state }
}

class ToggleHighContrastFoldersAction : EmberkaiSettingsToggle("High-contrast folder markers") {
    override fun selected(): Boolean = settings.highContrastFolderIcons
    override fun update(state: Boolean) { settings.highContrastFolderIcons = state }
}

class ToggleMonochromeFoldersAction : EmberkaiSettingsToggle("Monochrome service folders") {
    override fun selected(): Boolean = settings.monochromeFolderIcons
    override fun update(state: Boolean) { settings.monochromeFolderIcons = state }
}

class ToggleReducedFolderColorAction : EmberkaiSettingsToggle("Reduce service-folder color") {
    override fun selected(): Boolean = settings.reducedFolderColor
    override fun update(state: Boolean) { settings.reducedFolderColor = state }
}

class ToggleArchitectureFoldersAction : EmberkaiSettingsToggle("JVM architecture folders") {
    override fun selected(): Boolean = settings.isEnabled(IconRuleCategory.ARCHITECTURE)
    override fun update(state: Boolean) { settings.setEnabled(IconRuleCategory.ARCHITECTURE, state) }
}
