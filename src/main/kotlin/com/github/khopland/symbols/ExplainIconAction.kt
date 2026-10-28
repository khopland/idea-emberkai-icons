package com.github.khopland.symbols

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.net.JarURLConnection
import java.nio.file.Paths
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import javax.swing.BorderFactory
import java.awt.GridLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection

/** Project view action that describes how Emberkai selected an icon. */
internal class ExplainIconAction : AnAction("Explain This Icon"), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.getData(CommonDataKeys.VIRTUAL_FILE) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf { it.isValid } ?: return
        val resolution = computeReadAction<IconResolution> {
            IconResolver().resolveWithDetails(file, project)
        }
        ExplainIconDialog(project, file, resolution).show()
    }
}

private class ExplainIconDialog(
    private val project: Project,
    private val file: VirtualFile,
    private val resolution: IconResolution,
) : DialogWrapper(project) {
    private val status = JLabel(" ")

    init {
        title = "Explain This Icon"
        isResizable = false
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 12)).apply {
        border = EmptyBorder(12, 16, 8, 16)
        preferredSize = Dimension(520, 360)
        add(JLabel("<html><b>${escape(file.name)}</b><br>${escape(file.path)}</html>"), BorderLayout.NORTH)

        val body = JPanel(BorderLayout(0, 12))
        body.add(previewPanel(), BorderLayout.NORTH)
        body.add(detailsPanel(), BorderLayout.CENTER)
        add(body, BorderLayout.CENTER)

        val actions = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        val copyKey = JButton("Copy mapping key")
        copyKey.isEnabled = resolution.mappingKey != null
        copyKey.addActionListener {
            resolution.mappingKey?.let {
                CopyPasteManager.getInstance().setContents(StringSelection(it))
                status.text = "Copied ${it}"
            }
        }
        val revealArtwork = JButton("Reveal artwork")
        revealArtwork.isEnabled = resolution.resourcePath != null
        revealArtwork.addActionListener {
            val artwork = resolution.resourcePath?.let(::findArtwork)
            if (artwork != null) {
                OpenFileDescriptor(project, artwork).navigate(true)
            } else {
                status.text = "Artwork resource could not be opened."
            }
        }
        actions.add(copyKey)
        actions.add(revealArtwork)
        actions.add(status)
        add(actions, BorderLayout.SOUTH)
    }

    private fun previewPanel(): JComponent {
        val icon = resolution.icon ?: if (file.isDirectory) {
            AllIcons.Nodes.Folder
        } else {
            FileTypeManager.getInstance().getFileTypeByFile(file).icon ?: AllIcons.Nodes.Folder
        }
        val lightIcon = IconLoader.getDarkIcon(icon, false)
        val darkIcon = IconLoader.getDarkIcon(icon, true)
        return JPanel(GridLayout(1, 2, 8, 0)).apply {
            add(preview("Light", lightIcon))
            add(preview("Dark", darkIcon))
            border = BorderFactory.createTitledBorder("Rendered icon")
        }
    }

    private fun preview(label: String, icon: javax.swing.Icon): JComponent =
        JPanel(BorderLayout()).apply {
            val title = JLabel(label, SwingConstants.CENTER)
            val image = JLabel(icon, SwingConstants.CENTER)
            image.preferredSize = Dimension(120, 52)
            image.accessibleContext.accessibleDescription = "$label theme icon preview"
            add(title, BorderLayout.NORTH)
            add(image, BorderLayout.CENTER)
        }

    private fun detailsPanel(): JComponent = JPanel(GridBagLayout()).apply {
        val mapping = resolution.mappingKey ?: "No Emberkai mapping"
        val source = resolution.mappingSource?.displayName ?: "None; the IDE supplies the icon"
        val contextRule = resolution.contextRule ?: "None"
        val projectRole = resolution.projectRole?.let { "Took precedence — $it" }
            ?: "No project role took precedence"
        val nativePolicy = if (resolution.precedence == IconPrecedence.NATIVE_POLICY) {
            "Took precedence — ${resolution.detail}"
        } else {
            "Did not take precedence"
        }
        addRow("Resolution", "${resolution.precedence.displayName}: ${resolution.detail}")
        addRow("Mapping key", mapping)
        addRow("Mapping source", source)
        addRow("Context rule", contextRule)
        addRow("Native policy", nativePolicy)
        addRow("Project role", projectRole)
    }

    private fun JPanel.addRow(name: String, value: String) {
        val row = getComponentCount() / 2
        val labelConstraints = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.NORTHWEST
            insets = Insets(4, 0, 4, 12)
        }
        val valueConstraints = GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTHWEST
            insets = Insets(4, 0, 4, 0)
        }
        add(JLabel("$name:"), labelConstraints)
        add(JTextField(value).apply {
            isEditable = false
            border = null
            isOpaque = false
            caretPosition = 0
        }, valueConstraints)
    }

    private fun findArtwork(resourcePath: String): VirtualFile? {
        val resource = ExplainIconAction::class.java.getResource(resourcePath) ?: return null
        return when (resource.protocol) {
            "file" -> LocalFileSystem.getInstance()
                .findFileByPath(Paths.get(resource.toURI()).toString())
            "jar" -> {
                val connection = resource.openConnection() as? JarURLConnection ?: return null
                val jarPath = Paths.get(connection.jarFileURL.toURI()).toString()
                val jarFile = LocalFileSystem.getInstance().findFileByPath(jarPath) ?: return null
                JarFileSystem.getInstance().getJarRootForLocalFile(jarFile)
                    ?.findFileByRelativePath(connection.entryName)
            }
            else -> null
        }
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
