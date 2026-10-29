package com.github.khopland.symbols.overrides

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent

/** Creates, edits, or removes one project-local icon override. */
internal class IconOverrideDialog(
    private val project: Project,
    private val file: VirtualFile?,
    initialKey: String,
    initialIconPath: String? = null,
    private val onSaved: () -> Unit = {},
) : DialogWrapper(project) {
    private val store = ProjectIconOverrideStore.getInstance(project)
    private val keyField = JBTextField(initialKey)
    private val iconPathField = JBTextField(initialIconPath.orEmpty())
    private val status = JLabel(" ")
    private var selectedFile: VirtualFile? = null
    private val remove = JButton("Remove override").apply {
        addActionListener { removeOverride() }
    }

    init {
        title = "Override This Icon"
        setOKButtonText("Save Override")
        keyField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) = updateRemoveButton()
        })
        updateRemoveButton()
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent = iconPathField

    override fun createCenterPanel(): JComponent {
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(12, 12, 8, 12)
            preferredSize = Dimension(600, 260)
        }
        content.add(JLabel(
            "<html>Project overrides live in <b>.idea/${ProjectIconOverrideStore.PROPERTIES_NAME}</b> " +
                "and can be committed with the repository. They win over bundled mappings, but native " +
                "IDE icons still take precedence.</html>",
        ))
        if (file != null) {
            content.add(Box.createVerticalStrut(4))
            content.add(JLabel("Target: ${file.path}"))
        }
        content.add(Box.createVerticalStrut(12))
        content.add(row("Mapping key", keyField))
        content.add(Box.createVerticalStrut(8))
        content.add(row("Icon", JPanel(BorderLayout(6, 0)).apply {
            add(iconPathField, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                add(JButton("Browse SVG…").apply {
                    addActionListener { chooseArtwork() }
                })
                add(JButton("Bundled Icon…").apply {
                    addActionListener { chooseBundledIcon() }
                })
            }, BorderLayout.EAST)
        }))
        content.add(Box.createVerticalStrut(8))
        content.add(JLabel(
            "<html>Use a bundled path such as <tt>/symbols/icons/files/github.svg</tt>, or put an SVG in " +
                "<b>.idea/${ProjectIconOverrideStore.ICONS_DIRECTORY}/</b> and reference it by relative name.</html>",
        ))
        content.add(Box.createVerticalStrut(10))
        content.add(JPanel(BorderLayout()).apply {
            add(status, BorderLayout.CENTER)
            add(remove, BorderLayout.EAST)
        })
        return content
    }

    override fun doOKAction() {
        val key = ProjectIconOverrideFile.normalizeKey(keyField.text)
        if (!ProjectIconOverrideFile.isSupportedKey(key)) {
            status.text = "Keys start with file., extension., or folder., like the bundled icons.properties."
            return
        }
        if (file != null && !IconOverrideKeySuggestions.matches(file, key)) {
            status.text = "That key does not match ${file.name}."
            return
        }
        var value = iconPathField.text.trim()
        if (value.isEmpty()) {
            status.text = "Choose an SVG or a bundled icon."
            return
        }

        // A browsed file is only used while the field still shows its name.
        val external = selectedFile?.takeIf { it.name == value }
        if (external != null) {
            value = store.importArtwork(external) ?: run {
                status.text = "Could not copy the selected SVG into the project."
                return
            }
        } else if (ProjectIconOverrideFile.isBundledPath(value)) {
            if (!ProjectIconOverrideFile.isBundledArtwork(value)) {
                status.text = "That bundled icon path does not exist. Choose one with Bundled Icon…"
                return
            }
        } else {
            val relative = ProjectIconOverrideFile.relativeArtworkPath(value)
            if (relative == null) {
                status.text = "Use a path inside .idea/${ProjectIconOverrideStore.ICONS_DIRECTORY}/ or a bundled path."
                return
            }
            val artwork = store.iconsDirectory()?.findFileByRelativePath(relative)
            if (artwork == null || artwork.isDirectory) {
                status.text = "Add the SVG to .idea/${ProjectIconOverrideStore.ICONS_DIRECTORY}/ or choose a bundled icon."
                return
            }
            value = relative
        }

        if (!store.putOverride(key, value)) {
            status.text = "Could not update the overrides file."
            return
        }
        onSaved()
        super.doOKAction()
    }

    private fun chooseArtwork() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor("svg")
            .withTitle("Choose SVG Artwork")
            .withDescription("The SVG is copied into .idea/${ProjectIconOverrideStore.ICONS_DIRECTORY}/.")
        val chosen = FileChooser.chooseFile(descriptor, project, null) ?: return
        selectedFile = chosen
        iconPathField.text = chosen.name
        status.text = "Will copy ${chosen.name} into the project overrides folder."
    }

    private fun chooseBundledIcon() {
        IconCatalogDialog(project, selectable = true) { entry ->
            selectedFile = null
            iconPathField.text = entry.resourcePath
            status.text = "Selected ${entry.key}."
        }.show()
    }

    private fun removeOverride() {
        val key = ProjectIconOverrideFile.normalizeKey(keyField.text)
        if (key.isEmpty()) return
        if (!store.removeOverride(key)) {
            status.text = "Could not update the overrides file."
            return
        }
        onSaved()
        close(CANCEL_EXIT_CODE)
    }

    private fun updateRemoveButton() {
        remove.isEnabled = ProjectIconOverrideFile.normalizeKey(keyField.text) in store.overrides()
    }

    private fun row(label: String, component: JComponent): JComponent = JPanel(BorderLayout(8, 0)).apply {
        alignmentX = JComponent.LEFT_ALIGNMENT
        add(JLabel(label).apply { labelFor = component }, BorderLayout.WEST)
        add(component, BorderLayout.CENTER)
    }
}
