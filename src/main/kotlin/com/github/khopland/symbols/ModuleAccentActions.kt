package com.github.khopland.symbols

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.DefaultListCellRenderer
import javax.swing.Icon
import javax.swing.JList

/** Lets a user pin the accent color for the module that owns the selected folder. */
internal class AssignModuleAccentAction : AnAction("Assign Module Accent Color…"), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible = project != null && file != null && file.isDirectory &&
            moduleFor(project, file) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.isDirectory } ?: return
        val module = moduleFor(project, file) ?: return
        val store = ModuleAccentStore.getInstance(project)
        val palette = EmberkaiIconsSettings.getInstance().serviceFolderPalette
        val choices: List<ModuleAccentColor?> = listOf(null) + ModuleAccentColor.entries
        val popup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(choices)
            .setTitle("Module accent: ${module.name}")
            .setRenderer(AccentColorRenderer(store, module.name, palette))
            .setItemChosenCallback { choice ->
                if (choice == null) {
                    store.clearOverride(module.name)
                } else {
                    store.setOverride(module.name, choice)
                }
                RefreshProjectIconPresentations.refresh()
            }
            .createPopup()
        popup.showInBestPositionFor(event.dataContext)
    }
}

/** Removes every pinned module accent so colors return to their module-name defaults. */
internal class ResetAllModuleAccentColorsAction : AnAction("Reset All Module Accent Colors"), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabledAndVisible = project != null &&
            ModuleAccentStore.getInstance(project).hasOverrides()
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        ModuleAccentStore.getInstance(project).clearAll()
        RefreshProjectIconPresentations.refresh()
    }
}

private class AccentColorRenderer(
    private val store: ModuleAccentStore,
    private val moduleName: String,
    private val palette: FolderIconPalette,
) : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        val accent = value as? ModuleAccentColor
        if (accent == null) {
            val automatic = store.colorFor(moduleName, palette)
            text = "Automatic (${automatic.displayName})"
            icon = AccentSwatch(automatic.awtColor(false))
        } else {
            text = accent.displayName
            icon = AccentSwatch(accent.awtColor(false))
        }
        return this
    }
}

private class AccentSwatch(private val color: Color) : Icon {
    override fun getIconWidth(): Int = 12

    override fun getIconHeight(): Int = 12

    override fun paintIcon(component: Component?, g: Graphics, x: Int, y: Int) {
        val graphics = g.create() as Graphics2D
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = color
            graphics.fillRoundRect(x, y, 12, 12, 5, 5)
            graphics.color = BORDER
            graphics.stroke = BasicStroke(1f)
            graphics.drawRoundRect(x, y, 11, 11, 5, 5)
        } finally {
            graphics.dispose()
        }
    }

    private companion object {
        val BORDER: Color = JBColor(Color(0x8C8C8C), Color(0xB0B0B0))
    }
}

private fun moduleFor(project: Project, file: VirtualFile): Module? =
    computeReadAction<Module?> {
        ProjectFileIndex.getInstance(project).getModuleForFile(file)
    }
