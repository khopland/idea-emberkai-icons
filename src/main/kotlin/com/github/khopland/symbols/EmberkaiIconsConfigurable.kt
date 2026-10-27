package com.github.khopland.symbols

import com.intellij.icons.AllIcons
import com.intellij.openapi.options.Configurable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

class EmberkaiIconsConfigurable : Configurable {
    private var previewPanel: JPanel? = null
    private var preserveNativeIcons: JCheckBox? = null
    private var colorServiceFolders: JCheckBox? = null
    private var moduleAwareColors: JCheckBox? = null
    private var folderPalette: JComboBox<FolderIconPalette>? = null
    private var highContrast: JCheckBox? = null
    private var monochrome: JCheckBox? = null
    private var reducedFolderColor: JCheckBox? = null
    private val categoryBoxes = linkedMapOf<IconRuleCategory, JCheckBox>()

    override fun getDisplayName(): String = "Emberkai Icons"

    override fun createComponent(): JComponent {
        val root = JPanel()
        root.layout = BoxLayout(root, BoxLayout.Y_AXIS)

        root.add(sectionTitle("Icon behavior"))
        preserveNativeIcons = checkBox("Keep IntelliJ's native Java and Kotlin file icons").also {
            it.addActionListener { updatePreview() }
        }
        colorServiceFolders = checkBox("Color service folders by build or package manifest").also {
            it.addActionListener { updatePreview() }
        }
        moduleAwareColors = checkBox("Use module-aware accent colors and folder badges").also { checkbox ->
            checkbox.toolTipText =
                "Assign a stable color to every IntelliJ module and carry it into source roots and packages."
            checkbox.addActionListener { updatePreview() }
        }
        root.add(preserveNativeIcons)
        root.add(colorServiceFolders)
        root.add(moduleAwareColors)

        root.add(sectionTitle("Folder accessibility"))
        folderPalette = JComboBox(DefaultComboBoxModel(FolderIconPalette.entries.toTypedArray())).apply {
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
                ): Component = super.getListCellRendererComponent(
                    list, (value as? FolderIconPalette)?.displayName ?: value, index, isSelected, cellHasFocus,
                )
            }
            accessibleContext.accessibleName = "Service-folder color palette"
            accessibleContext.accessibleDescription = "Choose the color order used for service-root folders."
            addActionListener { updatePreview() }
        }
        root.add(labeledControl("Service-folder palette", folderPalette!!))
        highContrast = checkBox("Use high-contrast, shape-coded service-folder markers").also { checkbox ->
            checkbox.addActionListener { updatePreview() }
        }
        monochrome = checkBox("Use monochrome service-folder icons").also { checkbox ->
            checkbox.addActionListener { updatePreview() }
        }
        reducedFolderColor = checkBox("Reduce service-folder color").also { checkbox ->
            checkbox.addActionListener { updatePreview() }
        }
        root.add(highContrast)
        root.add(monochrome)
        root.add(reducedFolderColor)
        root.add(Box.createVerticalStrut(8))

        root.add(sectionTitle("Live preview"))
        root.add(JLabel("This sample updates as you change options.").apply {
            alignmentX = Component.LEFT_ALIGNMENT
        })
        previewPanel = JPanel(BorderLayout()).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            preferredSize = Dimension(420, 230)
            minimumSize = Dimension(300, 160)
            border = BorderFactory.createEtchedBorder()
            accessibleContext.accessibleName = "Live project tree preview"
            accessibleContext.accessibleDescription = "Example project tree showing how current icon options will look."
        }
        root.add(previewPanel)

        root.add(sectionTitle("Context icon categories"))
        root.add(JLabel("Apply refreshes project trees and open editor tabs.").apply {
            alignmentX = Component.LEFT_ALIGNMENT
        })
        for (category in IconRuleCategory.entries) {
            val checkbox = checkBox(category.displayName)
            categoryBoxes[category] = checkbox
            checkbox.addActionListener { updatePreview() }
            root.add(checkbox)
        }
        root.add(Box.createVerticalGlue())

        reset()
        updatePreview()
        return root
    }

    override fun isModified(): Boolean {
        val settings = EmberkaiIconsSettings.getInstance()
        return preserveNativeIcons?.isSelected != settings.preserveNativeJavaKotlinIcons ||
            colorServiceFolders?.isSelected != settings.colorServiceFolders ||
            moduleAwareColors?.isSelected != settings.moduleAwareColors ||
            selectedPalette() != settings.serviceFolderPalette ||
            highContrast?.isSelected != settings.highContrastFolderIcons ||
            monochrome?.isSelected != settings.monochromeFolderIcons ||
            reducedFolderColor?.isSelected != settings.reducedFolderColor ||
            IconRuleCategory.entries.any { category ->
                categoryBoxes[category]?.isSelected != settings.isEnabled(category)
            }
    }

    override fun apply() {
        val settings = EmberkaiIconsSettings.getInstance()
        val changed = isModified()
        preserveNativeIcons?.let { settings.preserveNativeJavaKotlinIcons = it.isSelected }
        colorServiceFolders?.let { settings.colorServiceFolders = it.isSelected }
        moduleAwareColors?.let { settings.moduleAwareColors = it.isSelected }
        selectedPalette()?.let { settings.serviceFolderPalette = it }
        highContrast?.let { settings.highContrastFolderIcons = it.isSelected }
        monochrome?.let { settings.monochromeFolderIcons = it.isSelected }
        reducedFolderColor?.let { settings.reducedFolderColor = it.isSelected }
        for (category in IconRuleCategory.entries) {
            categoryBoxes[category]?.let { settings.setEnabled(category, it.isSelected) }
        }
        if (changed) RefreshProjectIconPresentations.refresh()
    }

    override fun reset() {
        val settings = EmberkaiIconsSettings.getInstance()
        preserveNativeIcons?.isSelected = settings.preserveNativeJavaKotlinIcons
        colorServiceFolders?.isSelected = settings.colorServiceFolders
        moduleAwareColors?.isSelected = settings.moduleAwareColors
        folderPalette?.selectedItem = settings.serviceFolderPalette
        highContrast?.isSelected = settings.highContrastFolderIcons
        monochrome?.isSelected = settings.monochromeFolderIcons
        reducedFolderColor?.isSelected = settings.reducedFolderColor
        for (category in IconRuleCategory.entries) {
            categoryBoxes[category]?.isSelected = settings.isEnabled(category)
        }
        updatePreview()
    }

    override fun disposeUIResources() {
        previewPanel = null
        preserveNativeIcons = null
        colorServiceFolders = null
        moduleAwareColors = null
        folderPalette = null
        highContrast = null
        monochrome = null
        reducedFolderColor = null
        categoryBoxes.clear()
    }

    private fun selectedPalette(): FolderIconPalette? = folderPalette?.selectedItem as? FolderIconPalette

    private fun updatePreview() {
        val panel = previewPanel ?: return
        val mappings = IconMappings()
        val settings = PreviewSettings(
            palette = selectedPalette() ?: FolderIconPalette.EMBERKAI,
            highContrast = highContrast?.isSelected == true,
            monochrome = monochrome?.isSelected == true,
            reducedFolderColor = reducedFolderColor?.isSelected == true,
            moduleAware = moduleAwareColors?.isSelected == true,
        )
        val nodes = DefaultMutableTreeNode(PreviewItem("sample-project", mappings.get("folder")))
        val src = DefaultMutableTreeNode(PreviewItem("src", mappings.get("folder")))
        val main = DefaultMutableTreeNode(PreviewItem("main", mappings.get("folder.role.source")))
        val packageIcon = mappings.get("folder.role.package")?.let {
            if (settings.moduleAware) {
                ModuleAccentIcon(it, ModuleAccentColor.defaultFor("preview-module", settings.palette))
            } else {
                it
            }
        }
        val packageNode = DefaultMutableTreeNode(PreviewItem("com.example", packageIcon))
        val javaIcon = if (preserveNativeIcons?.isSelected != false) AllIcons.FileTypes.Java else mappings.get("extension.java")
        packageNode.add(DefaultMutableTreeNode(PreviewItem("Application.java", javaIcon)))
        main.add(packageNode)
        src.add(main)
        src.add(DefaultMutableTreeNode(PreviewItem("test", mappings.get("folder.role.test-source"))))
        nodes.add(src)

        nodes.add(DefaultMutableTreeNode(PreviewItem("build.gradle.kts", mappings.get("extension.gradle.kts"))))
        val helm = categoryBoxes[IconRuleCategory.HELM]?.isSelected != false
        nodes.add(DefaultMutableTreeNode(PreviewItem("Chart.yaml", mappings.get(if (helm) "context.helm" else "extension.yaml"))))
        val kubernetes = categoryBoxes[IconRuleCategory.KUBERNETES]?.isSelected != false
        nodes.add(DefaultMutableTreeNode(PreviewItem("deployment.yaml", mappings.get(if (kubernetes) "context.kubernetes" else "extension.yaml"))))
        val serviceIcon = if (colorServiceFolders?.isSelected != false) {
            mappings.get(previewServiceIconKey(settings))
        } else {
            mappings.get("folder")
        }
        nodes.add(DefaultMutableTreeNode(PreviewItem("service-api", serviceIcon)))

        val tree = JTree(nodes).apply {
            isRootVisible = true
            showsRootHandles = true
            rowHeight = 22
            accessibleContext.accessibleName = "Icon preview project tree"
            accessibleContext.accessibleDescription = "Java, Gradle, Helm, Kubernetes, source, test, package, and service-root icon examples."
            cellRenderer = object : DefaultTreeCellRenderer() {
                override fun getTreeCellRendererComponent(
                    tree: JTree?, value: Any?, selected: Boolean, expanded: Boolean,
                    leaf: Boolean, row: Int, hasFocus: Boolean,
                ): Component {
                    val item = (value as? DefaultMutableTreeNode)?.userObject as? PreviewItem
                    val component = super.getTreeCellRendererComponent(
                        tree, item?.label ?: value?.toString().orEmpty(), selected, expanded, leaf, row, hasFocus,
                    )
                    if (item != null) icon = item.icon
                    return component
                }
            }
            expandRow(0)
            expandRow(1)
            expandRow(2)
            expandRow(3)
        }
        panel.removeAll()
        panel.add(JScrollPane(tree), BorderLayout.CENTER)
        panel.revalidate()
        panel.repaint()
    }

    private fun previewServiceIconKey(settings: PreviewSettings): String {
        val color = if (settings.moduleAware) {
            ModuleAccentColor.defaultFor("preview-service", settings.palette)
        } else {
            ModuleAccentColor.fromId(ServiceFolderIconStyle.colorNames(settings.palette).first())
                ?: ModuleAccentColor.BLUE
        }
        return ServiceFolderIconStyle.styleKey(
            baseKey = color.iconKey,
            paletteChoice = settings.palette,
            highContrast = settings.highContrast,
            monochrome = settings.monochrome,
            reducedFolderColor = settings.reducedFolderColor,
        )
    }

    private fun sectionTitle(text: String): JLabel = JLabel(text).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        border = BorderFactory.createEmptyBorder(8, 0, 4, 0)
    }

    private fun checkBox(text: String): JCheckBox = JCheckBox(text).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        accessibleContext.accessibleName = text
    }

    private fun labeledControl(label: String, component: JComponent): JPanel = JPanel(BorderLayout(12, 0)).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        add(JLabel(label).apply { labelFor = component }, BorderLayout.WEST)
        add(component, BorderLayout.CENTER)
    }

    private data class PreviewItem(val label: String, val icon: javax.swing.Icon?)

    private data class PreviewSettings(
        val palette: FolderIconPalette,
        val highContrast: Boolean,
        val monochrome: Boolean,
        val reducedFolderColor: Boolean,
        val moduleAware: Boolean,
    )
}
