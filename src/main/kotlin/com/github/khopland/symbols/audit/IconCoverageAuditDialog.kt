package com.github.khopland.symbols.audit

import com.github.khopland.symbols.overrides.IconOverrideDialog
import com.intellij.ide.BrowserUtil
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil.escapeXmlEntities
import com.intellij.openapi.vfs.VirtualFile
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTree
import javax.swing.border.EmptyBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeSelectionModel

internal class IconCoverageAuditDialog(
    private val project: Project,
    private val result: CoverageAuditResult,
) : DialogWrapper(project) {
    private var selectedFinding: CoverageAuditFinding? = null
    private val details = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        border = EmptyBorder(8, 8, 8, 8)
        text = "Select a file to see its resolution and suggested mapping."
    }
    private val status = JLabel(" ")
    private val copyMapping = JButton("Copy Suggested Mapping").apply {
        isEnabled = false
        addActionListener { copySelectedMapping() }
    }
    private val reportMissing = JButton("Report Missing Icon").apply {
        isEnabled = false
        addActionListener { reportSelectedFinding() }
    }
    private val createOverride = JButton("Create Override…").apply {
        isEnabled = false
        addActionListener { createSelectedOverride() }
    }
    private val openFile = JButton("Open File").apply {
        isEnabled = false
        addActionListener { openSelectedFile() }
    }
    private lateinit var tree: JTree

    init {
        title = "Rule-Aware Icon Coverage"
        isResizable = true
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = DefaultMutableTreeNode("Coverage findings")
        result.groups.forEach { group ->
            val groupNode = DefaultMutableTreeNode(group.label)
            group.findings.forEach { finding -> groupNode.add(DefaultMutableTreeNode(finding)) }
            root.add(groupNode)
        }
        if (result.groups.isEmpty()) root.add(DefaultMutableTreeNode("No generic or unmapped file icons found."))

        tree = JTree(root).apply {
            isRootVisible = false
            showsRootHandles = true
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            addTreeSelectionListener { updateSelection() }
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount == 2) openSelectedFile()
                }
            })
        }

        val summary = buildString {
            append("Scanned ${result.scannedFiles} files under ${result.root.path}. ")
            append("Found ${result.totalFindings} coverage candidates.")
            if (result.omittedFindings > 0) append(" Showing the first ${result.totalFindings - result.omittedFindings}.")
        }
        val content = JPanel(BorderLayout(0, 8)).apply {
            border = EmptyBorder(12, 12, 8, 12)
            preferredSize = Dimension(900, 520)
            add(JLabel("<html>${escapeXmlEntities(summary)}<br>Native policy and project-role icons are omitted.</html>"), BorderLayout.NORTH)
            val split = JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                JScrollPane(tree),
                JScrollPane(details),
            ).apply {
                resizeWeight = 0.62
                dividerLocation = 540
            }
            add(split, BorderLayout.CENTER)
            val bottom = JPanel(BorderLayout(0, 6))
            bottom.add(status, BorderLayout.NORTH)
            bottom.add(JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
                add(openFile)
                add(createOverride)
                add(copyMapping)
                add(reportMissing)
            }, BorderLayout.SOUTH)
            add(bottom, BorderLayout.SOUTH)
        }
        return content
    }

    private fun updateSelection() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        selectedFinding = node?.userObject as? CoverageAuditFinding
        val finding = selectedFinding
        details.text = if (finding == null) {
            "Select a file to see its resolution and suggested mapping."
        } else {
            buildString {
                appendLine(finding.relativePath)
                appendLine()
                appendLine("Result: ${finding.kind.displayName}")
                appendLine("Current mapping: ${finding.mappingKey ?: "none"}")
                appendLine("Mapping source: ${finding.mappingSource?.displayName ?: "none"}")
                appendLine()
                appendLine("Suggested override key:")
                appendLine(finding.suggestedMappingKey)
                appendLine()
                appendLine("Suggested mapping for icons.properties:")
                append(finding.suggestedMapping)
            }
        }
        details.caretPosition = 0
        openFile.isEnabled = finding != null
        createOverride.isEnabled = finding != null
        copyMapping.isEnabled = finding != null
        reportMissing.isEnabled = finding != null
        status.text = finding?.kind?.displayName ?: " "
    }

    private fun copySelectedMapping() {
        val finding = selectedFinding ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(finding.suggestedMapping))
        status.text = "Suggested mapping copied. Replace the artwork path with an existing or new SVG."
    }

    private fun createSelectedOverride() {
        val finding = selectedFinding ?: return
        IconOverrideDialog(
            project = project,
            file = finding.file,
            initialKey = finding.suggestedMappingKey,
            initialIconPath = null,
            onSaved = {
                status.text = "Override saved. Re-run the audit to verify the new icon."
            },
        ).show()
    }

    private fun reportSelectedFinding() {
        val finding = selectedFinding ?: return
        val title = "Missing Emberkai icon: ${finding.file.name}"
        val body = buildString {
            appendLine("The Rule-Aware Icon Coverage audit found this file.")
            appendLine()
            appendLine("- File: `${finding.relativePath}`")
            appendLine("- Result: ${finding.kind.displayName}")
            appendLine("- Current mapping: `${finding.mappingKey ?: "none"}`")
            appendLine()
            appendLine("Suggested mapping:")
            appendLine("```properties")
            appendLine(finding.suggestedMapping)
            appendLine("```")
        }
        val url = "https://github.com/khopland/idea-emberkai-icons/issues/new" +
            "?title=${encode(title)}&body=${encode(body)}"
        BrowserUtil.browse(url)
    }

    private fun openSelectedFile() {
        val file = selectedFinding?.file?.takeIf(VirtualFile::isValid) ?: return
        if (file.isDirectory) {
            ProjectView.getInstance(project).select(null, file, true)
        } else {
            OpenFileDescriptor(project, file).navigate(true)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
