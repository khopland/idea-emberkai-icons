package com.github.khopland.symbols

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class EmberkaiIconsConfigurable : Configurable {
    private var panel: JPanel? = null
    private var preserveNativeIcons: JCheckBox? = null
    private var colorServiceFolders: JCheckBox? = null
    private val categoryBoxes = linkedMapOf<IconRuleCategory, JCheckBox>()

    override fun getDisplayName(): String = "Emberkai Icons"

    override fun createComponent(): JComponent {
        val root = JPanel()
        root.layout = BoxLayout(root, BoxLayout.Y_AXIS)

        root.add(sectionTitle("Icon behavior"))
        preserveNativeIcons = checkBox("Keep IntelliJ's native Java and Kotlin file icons")
        colorServiceFolders = checkBox("Color service folders by build or package manifest")
        root.add(preserveNativeIcons)
        root.add(colorServiceFolders)
        root.add(Box.createVerticalStrut(12))

        root.add(sectionTitle("Context icon categories"))
        root.add(JLabel("Context switches leave standard mappings enabled. Project trees and open tabs refresh on Apply; other IDE surfaces may need reopening.").apply {
            alignmentX = Component.LEFT_ALIGNMENT
        })
        for (category in IconRuleCategory.entries) {
            val checkbox = checkBox(category.displayName)
            categoryBoxes[category] = checkbox
            root.add(checkbox)
        }

        panel = root
        return root
    }

    override fun isModified(): Boolean {
        val settings = EmberkaiIconsSettings.getInstance()
        return preserveNativeIcons?.isSelected != settings.preserveNativeJavaKotlinIcons ||
            colorServiceFolders?.isSelected != settings.colorServiceFolders ||
            IconRuleCategory.entries.any { category ->
                categoryBoxes[category]?.isSelected != settings.isEnabled(category)
            }
    }

    override fun apply() {
        val settings = EmberkaiIconsSettings.getInstance()
        val changed = isModified()
        preserveNativeIcons?.let { settings.preserveNativeJavaKotlinIcons = it.isSelected }
        colorServiceFolders?.let { settings.colorServiceFolders = it.isSelected }
        for (category in IconRuleCategory.entries) {
            categoryBoxes[category]?.let { settings.setEnabled(category, it.isSelected) }
        }
        if (changed) refreshProjectViews()
    }

    override fun reset() {
        val settings = EmberkaiIconsSettings.getInstance()
        preserveNativeIcons?.isSelected = settings.preserveNativeJavaKotlinIcons
        colorServiceFolders?.isSelected = settings.colorServiceFolders
        for (category in IconRuleCategory.entries) {
            categoryBoxes[category]?.isSelected = settings.isEnabled(category)
        }
    }

    override fun disposeUIResources() {
        panel = null
        preserveNativeIcons = null
        colorServiceFolders = null
        categoryBoxes.clear()
    }

    private fun sectionTitle(text: String): JLabel = JLabel(text).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        border = javax.swing.BorderFactory.createEmptyBorder(8, 0, 4, 0)
    }

    private fun checkBox(text: String): JCheckBox = JCheckBox(text).apply {
        alignmentX = Component.LEFT_ALIGNMENT
    }

    private fun refreshProjectViews() {
        ProjectManager.getInstance().openProjects
            .filterNot { it.isDisposed }
            .forEach { project ->
                ProjectView.getInstance(project).refresh()
                val editors = FileEditorManager.getInstance(project)
                editors.openFiles.forEach(editors::updateFilePresentation)
            }
    }
}
