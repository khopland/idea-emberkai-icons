package com.github.khopland.symbols.settings

import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.RefreshProjectIconPresentations
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

/** View-menu toggles for single settings; text and descriptions are registered in plugin.xml. */
abstract class EmberkaiSettingsToggle : ToggleAction(), DumbAware {
    protected val settings: EmberkaiIconsSettings
        get() = EmberkaiIconsSettings.getInstance()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    final override fun isSelected(event: AnActionEvent): Boolean = selected()

    final override fun setSelected(event: AnActionEvent, state: Boolean) {
        if (selected() == state) return
        update(state)
        RefreshProjectIconPresentations.refresh()
    }

    protected abstract fun selected(): Boolean
    protected abstract fun update(state: Boolean)
}

class ToggleServiceFolderColorsAction : EmberkaiSettingsToggle() {
    override fun selected(): Boolean = settings.colorServiceFolders
    override fun update(state: Boolean) { settings.colorServiceFolders = state }
}

class ToggleModuleAwareColorsAction : EmberkaiSettingsToggle() {
    override fun selected(): Boolean = settings.moduleAwareColors
    override fun update(state: Boolean) { settings.moduleAwareColors = state }
}

class ToggleHighContrastFoldersAction : EmberkaiSettingsToggle() {
    override fun selected(): Boolean = settings.highContrastFolderIcons
    override fun update(state: Boolean) { settings.highContrastFolderIcons = state }
}

class ToggleMonochromeFoldersAction : EmberkaiSettingsToggle() {
    override fun selected(): Boolean = settings.monochromeFolderIcons
    override fun update(state: Boolean) { settings.monochromeFolderIcons = state }
}

class ToggleReducedFolderColorAction : EmberkaiSettingsToggle() {
    override fun selected(): Boolean = settings.reducedFolderColor
    override fun update(state: Boolean) { settings.reducedFolderColor = state }
}

class ToggleStereotypeBadgesAction : EmberkaiSettingsToggle() {
    override fun selected(): Boolean = settings.isEnabled(IconRuleCategory.STEREOTYPE_BADGES)
    override fun update(state: Boolean) { settings.setEnabled(IconRuleCategory.STEREOTYPE_BADGES, state) }
}

class ToggleArchitectureFoldersAction : EmberkaiSettingsToggle() {
    override fun selected(): Boolean = settings.isEnabled(IconRuleCategory.ARCHITECTURE)
    override fun update(state: Boolean) { settings.setEnabled(IconRuleCategory.ARCHITECTURE, state) }
}
