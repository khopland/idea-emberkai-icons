package com.github.khopland.symbols.ownership

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Action
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.ListSelectionModel
import javax.swing.border.EmptyBorder

/** Lists each owner a branch needs review from; selecting one shows the changed files they own. */
internal class BranchReviewersDialog(
    private val project: Project,
    private val reviewers: BranchReviewers,
    private val bases: List<String>,
) : DialogWrapper(project) {
    private val groups: List<Pair<String, List<ChangedPath>>> = reviewers.owners.map { (owner, paths) ->
        "$owner — ${files(paths.size)}" to paths
    } + listOfNotNull(
        reviewers.unowned.takeIf { it.isNotEmpty() }?.let { "No owner — ${files(it.size)}" to it },
        reviewers.ungoverned.takeIf { it.isNotEmpty() }?.let { "No CODEOWNERS file — ${files(it.size)}" to it },
    )
    private val paths = DefaultListModel<String>()
    private var shown: List<ChangedPath> = emptyList()

    init {
        title = "Reviewers for This Branch"
        isResizable = true
        setOKButtonText("Close")
        init()
    }

    override fun createActions(): Array<Action> {
        val copy = object : DialogWrapperAction("Copy Owners") {
            override fun doAction(event: ActionEvent?) {
                CopyPasteManager.getInstance().setContents(StringSelection(reviewers.owners.joinToString(" ") { it.first }))
            }
        }
        copy.isEnabled = reviewers.owners.isNotEmpty()
        return arrayOf(copy, okAction)
    }

    override fun createCenterPanel(): JComponent {
        val ownerList = JBList(groups.map { it.first }).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            emptyText.text = "This branch changes no files"
            addListSelectionListener { show(groups.getOrNull(selectedIndex)?.second.orEmpty()) }
        }
        val pathList = JBList(paths).apply {
            emptyText.text = "Select an owner to see their files"
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount != 2) return
                    val file = shown.getOrNull(selectedIndex)?.file() ?: return
                    OpenFileDescriptor(project, file).navigate(true)
                }
            })
        }
        if (groups.isNotEmpty()) ownerList.selectedIndex = 0
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JBScrollPane(ownerList), JBScrollPane(pathList)).apply {
            resizeWeight = 0.4
        }
        return JPanel(BorderLayout(0, 8)).apply {
            add(JLabel(summary()).apply { border = EmptyBorder(0, 0, 4, 0) }, BorderLayout.NORTH)
            add(split, BorderLayout.CENTER)
            preferredSize = Dimension(720, 420)
        }
    }

    private fun show(changed: List<ChangedPath>) {
        shown = changed
        paths.clear()
        changed.forEach { paths.addElement(if (it.file() == null) "${it.path} (deleted)" else it.path) }
    }

    private fun summary(): String {
        val compared = if (bases.isEmpty()) "uncommitted changes (no default branch found)" else bases.joinToString()
        val count = reviewers.changedCount
        if (count == 0) return "Nothing changed compared with $compared."
        val owners = reviewers.owners.size
        val who = if (owners == 0) "no CODEOWNERS owner" else if (owners == 1) "1 owner" else "$owners owners"
        return "${files(count).replaceFirstChar { it.uppercase() }} changed compared with $compared need review from $who."
    }

    private fun files(count: Int): String = if (count == 1) "1 file" else "$count files"
}
