package com.github.khopland.symbols

import com.github.khopland.symbols.EmberkaiIconsSettings.State
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.LayeredIcon
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.IOException
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import kotlin.reflect.KMutableProperty1

class EmberkaiIconsConfigurable : Configurable {
    private var previewPanel: JPanel? = null
    private var folderPalette: JComboBox<FolderIconPalette>? = null
    private var filterField: JBTextField? = null
    private var statusLabel: JLabel? = null
    /** Every boolean setting's checkbox, keyed by the state field it edits. */
    private val flagBoxes = linkedMapOf<KMutableProperty1<State, Boolean>, JCheckBox>()
    private val searchableRows = mutableListOf<SearchableRow>()

    override fun getDisplayName(): String = "Emberkai Icons"

    override fun createComponent(): JComponent {
        val root = JPanel()
        root.layout = BoxLayout(root, BoxLayout.Y_AXIS)

        root.add(sectionTitle("Find settings"))
        filterField = JBTextField().apply {
            emptyText.text = "Filter settings…"
            accessibleContext.accessibleName = "Filter settings"
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent) = applyFilter()
                override fun removeUpdate(event: DocumentEvent) = applyFilter()
                override fun changedUpdate(event: DocumentEvent) = applyFilter()
            })
        }
        root.add(filterField!!)
        statusLabel = JLabel(" ").apply { alignmentX = Component.LEFT_ALIGNMENT }

        root.add(sectionTitle("Icon behavior"))
        addFlag(root, State::preserveNativeJavaKotlinIcons, "Keep IntelliJ's native Java and Kotlin file icons")
        addFlag(root, State::colorServiceFolders, "Color service folders by build or package manifest")
        addFlag(
            root, State::moduleAwareColors, "Use module-aware accent colors and folder badges",
            "Assign a stable color to every IntelliJ module and carry it into source roots and packages.",
        )
        addFlag(
            root, State::semanticTooltips, "Describe recognized files and folders in Project view tooltips",
            "Show what Emberkai recognized, such as a Kubernetes manifest or a production profile. " +
                "Screen readers announce it as the node description.",
        )

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
        val paletteRow = labeledControl("Service-folder palette", folderPalette!!)
        addSearchable(root, paletteRow, "Service-folder palette")
        addFlag(root, State::highContrastFolderIcons, "Use high-contrast, shape-coded service-folder markers")
        addFlag(root, State::monochromeFolderIcons, "Use monochrome service-folder icons")
        addFlag(root, State::reducedFolderColor, "Reduce service-folder color")
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
            addFlag(root, EmberkaiIconsSettings.flag(category), category.displayName)
        }

        root.add(sectionTitle("Project icon overrides"))
        root.add(JLabel(
            "Overrides are stored in .idea/${ProjectIconOverrideStore.PROPERTIES_NAME} and can be shared with the project.",
        ).apply {
            alignmentX = Component.LEFT_ALIGNMENT
        })
        val overrideButtons = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            add(JButton("Open override file").apply { addActionListener { openOverridesFile() } })
            add(JButton("Browse bundled icons").apply { addActionListener { browseIcons() } })
            add(JButton("Reload overrides").apply {
                addActionListener {
                    val project = activeProject()
                    project?.let { ProjectIconOverrideStore.getInstance(it).reload() }
                    statusLabel?.text = if (project == null) "Open a project to reload its icon overrides." else "Project icon overrides reloaded."
                }
            })
        }
        addSearchable(root, overrideButtons, "Project icon overrides")

        root.add(sectionTitle("Settings file"))
        val settingsButtons = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            add(JButton("Export…").apply { addActionListener { exportSettings() } })
            add(JButton("Import…").apply { addActionListener { importSettings() } })
            add(JButton("Reset to defaults").apply { addActionListener { resetSettings() } })
        }
        addSearchable(root, settingsButtons, "Export import reset settings")
        root.add(statusLabel!!)
        root.add(Box.createVerticalGlue())

        reset()
        updatePreview()
        return root
    }

    override fun isModified(): Boolean = formState() != EmberkaiIconsSettings.getInstance().state

    override fun apply() {
        val changed = isModified()
        EmberkaiIconsSettings.getInstance().loadState(formState())
        if (changed) RefreshProjectIconPresentations.refresh()
    }

    override fun reset() {
        loadForm(EmberkaiIconsSettings.getInstance().state)
    }

    /** Shows [state] in the form without applying it; Apply or Cancel decides. */
    private fun loadForm(state: State) {
        flagBoxes.forEach { (flag, box) -> box.isSelected = flag.get(state) }
        folderPalette?.selectedItem = FolderIconPalette.fromId(state.serviceFolderPalette)
        updatePreview()
    }

    private fun formState(): State = EmberkaiIconsSettings.getInstance().state.also { state ->
        flagBoxes.forEach { (flag, box) -> flag.set(state, box.isSelected) }
        selectedPalette()?.let { state.serviceFolderPalette = it.name }
    }

    override fun disposeUIResources() {
        previewPanel = null
        folderPalette = null
        filterField = null
        statusLabel = null
        flagBoxes.clear()
        searchableRows.clear()
    }

    private fun selectedPalette(): FolderIconPalette? = folderPalette?.selectedItem as? FolderIconPalette

    /** The form value of [flag], or [default] before the form exists. */
    private fun isChecked(flag: KMutableProperty1<State, Boolean>, default: Boolean): Boolean =
        flagBoxes[flag]?.isSelected ?: default

    private fun isEnabled(category: IconRuleCategory): Boolean = isChecked(EmberkaiIconsSettings.flag(category), true)

    private fun addFlag(root: JPanel, flag: KMutableProperty1<State, Boolean>, text: String, tooltip: String? = null) {
        val checkbox = checkBox(text)
        checkbox.toolTipText = tooltip
        checkbox.addActionListener { updatePreview() }
        flagBoxes[flag] = checkbox
        addSearchable(root, checkbox, text)
    }

    private fun addSearchable(root: JPanel, component: JComponent, text: String) {
        component.alignmentX = Component.LEFT_ALIGNMENT
        searchableRows += SearchableRow(component, text)
        root.add(component)
    }

    private fun applyFilter() {
        val query = filterField?.text?.trim()?.lowercase(Locale.ROOT).orEmpty()
        searchableRows.forEach { row ->
            row.component.isVisible = query.isEmpty() || row.text.lowercase(Locale.ROOT).contains(query)
        }
        filterField?.parent?.revalidate()
        filterField?.parent?.repaint()
    }

    private fun activeProject(): Project? {
        val component = filterField
        val fromContext = component?.let { CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(it)) }
        return (fromContext ?: ProjectUtil.getActiveProject())?.takeUnless { it.isDisposed || it.isDefault }
    }

    private fun openOverridesFile() {
        val project = activeProject()
        if (project == null) {
            statusLabel?.text = "Open a project to edit its icon overrides."
            return
        }
        val file = ProjectIconOverrideStore.getInstance(project).ensurePropertiesFile()
        if (file == null) {
            Messages.showErrorDialog(project, "Could not create the project overrides file.", "Emberkai Icons")
            return
        }
        OpenFileDescriptor(project, file).navigate(true)
        statusLabel?.text = "Opened .idea/${ProjectIconOverrideStore.PROPERTIES_NAME} in ${project.name}."
    }

    private fun browseIcons() {
        val project = activeProject()
        if (project == null) {
            statusLabel?.text = "Open a project to browse bundled icons."
            return
        }
        IconCatalogDialog(project, selectable = false).show()
    }

    private fun exportSettings() {
        val parent = filterField ?: return
        val descriptor = FileSaverDescriptor(
            "Export Emberkai Icons Settings",
            "Save the settings shown on this page",
            "properties",
        )
        val target = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, parent)
            .save(null as java.nio.file.Path?, "emberkai-icons.properties") ?: return
        try {
            EmberkaiIconsSettings.export(formState(), target.file.toPath())
            statusLabel?.text = "Exported settings to ${target.file.name}."
        } catch (exception: IOException) {
            Messages.showErrorDialog(filterField, "Could not export settings: ${exception.message}", "Emberkai Icons")
        }
    }

    private fun importSettings() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("properties")
            .withTitle("Import Emberkai Icons Settings")
        val file = FileChooser.chooseFile(descriptor, filterField, activeProject(), null) ?: return
        try {
            loadForm(EmberkaiIconsSettings.import(file.toNioPath()))
            statusLabel?.text = "Imported ${file.name}. Apply to keep these settings."
        } catch (exception: IOException) {
            Messages.showErrorDialog(filterField, "Could not import settings: ${exception.message}", "Emberkai Icons")
        } catch (exception: IllegalArgumentException) {
            Messages.showErrorDialog(filterField, "Could not import settings: ${exception.message}", "Emberkai Icons")
        }
    }

    private fun resetSettings() {
        loadForm(EmberkaiIconsSettings.State())
        statusLabel?.text = "Defaults restored. Apply to keep them."
    }

    private fun updatePreview() {
        val panel = previewPanel ?: return
        val mappings = IconMappings()
        val settings = PreviewSettings(
            palette = selectedPalette() ?: FolderIconPalette.EMBERKAI,
            highContrast = isChecked(State::highContrastFolderIcons, false),
            monochrome = isChecked(State::monochromeFolderIcons, false),
            reducedFolderColor = isChecked(State::reducedFolderColor, false),
            moduleAware = isChecked(State::moduleAwareColors, false),
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
        val javaIcon = when {
            !isChecked(State::preserveNativeJavaKotlinIcons, true) -> mappings.get("extension.java")
            isEnabled(IconRuleCategory.STEREOTYPE_BADGES) ->
                LayeredIcon.layeredIcon(arrayOf(AllIcons.Nodes.Class, ClassStereotype.APPLICATION.icon))
            else -> AllIcons.Nodes.Class
        }
        packageNode.add(DefaultMutableTreeNode(PreviewItem("Application.java", javaIcon)))
        main.add(packageNode)
        src.add(main)
        src.add(DefaultMutableTreeNode(PreviewItem("test", mappings.get("folder.role.test-source"))))
        nodes.add(src)

        nodes.add(DefaultMutableTreeNode(PreviewItem("build.gradle.kts", mappings.get("extension.gradle.kts"))))
        val helm = isEnabled(IconRuleCategory.HELM)
        nodes.add(DefaultMutableTreeNode(PreviewItem("Chart.yaml", mappings.get(if (helm) "context.helm" else "extension.yaml"))))
        val kubernetes = isEnabled(IconRuleCategory.KUBERNETES)
        nodes.add(DefaultMutableTreeNode(PreviewItem("deployment.yaml", mappings.get(if (kubernetes) "context.kubernetes.deployment" else "extension.yaml"))))
        val serviceIcon = if (isChecked(State::colorServiceFolders, true)) {
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
            settings.palette.colors.first()
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

    private data class SearchableRow(val component: JComponent, val text: String)

    private data class PreviewItem(val label: String, val icon: javax.swing.Icon?)

    private data class PreviewSettings(
        val palette: FolderIconPalette,
        val highContrast: Boolean,
        val monochrome: Boolean,
        val reducedFolderColor: Boolean,
        val moduleAware: Boolean,
    )
}
