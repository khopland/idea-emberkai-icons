package com.github.khopland.emberkai.audit

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil.escapeXmlEntities
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.IconUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/** Opens the legend of every Emberkai badge, from **View > Emberkai Icons** and other entry points. */
internal class ShowIconLegendAction : AnAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(event: AnActionEvent) {
        IconLegendDialog(event.project).show()
    }
}

/** Every mark, grouped by corner, at its real 16px size and enlarged so its shape is easy to learn. */
internal class IconLegendDialog(project: Project?) : DialogWrapper(project) {
    init {
        title = "Emberkai Icon Legend"
        setOKButtonText("Close")
        init()
    }

    override fun createActions() = arrayOf(okAction)

    override fun createCenterPanel(): JComponent {
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(8, 12, 8, 12)
        }
        for (section in IconLegend.sections()) {
            if (section.entries.isEmpty()) continue
            content.add(JLabel("<html><b>${escapeXmlEntities(section.title)}</b></html>").apply {
                alignmentX = Component.LEFT_ALIGNMENT
                border = EmptyBorder(12, 0, 2, 0)
            })
            content.add(JLabel("<html>${escapeXmlEntities(section.hint)}</html>").apply {
                alignmentX = Component.LEFT_ALIGNMENT
                border = EmptyBorder(0, 0, 4, 0)
            })
            content.add(entries(section.entries))
        }
        return JBScrollPane(content).apply {
            preferredSize = Dimension(620, 560)
            border = null
            verticalScrollBar.unitIncrement = 16
        }
    }

    private fun entries(entries: List<LegendEntry>): JComponent = JPanel(GridBagLayout()).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        entries.forEachIndexed { row, entry ->
            fun cell(column: Int, weight: Double = 0.0) = GridBagConstraints().apply {
                gridx = column
                gridy = row
                weightx = weight
                anchor = GridBagConstraints.WEST
                fill = if (weight > 0) GridBagConstraints.HORIZONTAL else GridBagConstraints.NONE
                insets = Insets(3, 0, 3, 10)
            }
            add(JLabel(entry.icon), cell(0))
            add(JLabel(IconUtil.scale(entry.icon, null, 2f)), cell(1))
            add(JLabel("<html><b>${escapeXmlEntities(entry.name)}</b> — ${escapeXmlEntities(entry.meaning)}</html>").apply {
                accessibleContext.accessibleName = "${entry.name}: ${entry.meaning}"
            }, cell(2, 1.0))
        }
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }
}
