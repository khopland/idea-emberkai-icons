package com.github.khopland.symbols.badges

import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.RefreshProjectIconPresentations
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.JBColor
import java.awt.Color
import java.util.function.Function
import javax.swing.JComponent

/**
 * Production configuration stays recognizable once it is open: its editor tab is tinted red and a
 * banner names why it counts as production. Files qualify exactly as for the production badge, by
 * name or by inheriting from an environment folder such as `overlays/production/`.
 */
internal object ProductionEditorWarning {
    val TAB_COLOR: Color = JBColor(0xF9DADA, 0x4D2B2B)

    fun applies(file: VirtualFile): Boolean =
        EmberkaiIconsSettings.getInstance().isEnabled(IconRuleCategory.PRODUCTION_EDITOR_WARNINGS) &&
            EnvironmentVariant.fromFile(file) == EnvironmentVariant.PRODUCTION

    fun message(file: VirtualFile): String {
        val folder = EnvironmentVariant.environmentFolderOf(file)?.takeIf { EnvironmentVariant.isInherited(file) }
        val reason = folder?.let { " It inherits production from the ${it.parent.name}/${it.name}/ folder." }.orEmpty()
        return "This is production configuration; changes here reach production.$reason"
    }
}

internal class ProductionEditorTabColorProvider : EditorTabColorProvider, DumbAware {
    override fun getEditorTabColor(project: Project, file: VirtualFile): Color? =
        ProductionEditorWarning.TAB_COLOR.takeIf { ProductionEditorWarning.applies(file) }
}

internal class ProductionEditorNotificationProvider : EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (!ProductionEditorWarning.applies(file)) return null
        return Function { editor ->
            EditorNotificationPanel(editor, EditorNotificationPanel.Status.Warning).apply {
                text = ProductionEditorWarning.message(file)
                createActionLabel("Turn off production warnings") {
                    EmberkaiIconsSettings.getInstance().setEnabled(IconRuleCategory.PRODUCTION_EDITOR_WARNINGS, false)
                    RefreshProjectIconPresentations.refresh()
                }
            }
        }
    }
}
