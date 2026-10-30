package com.github.khopland.symbols.overrides

import com.github.khopland.symbols.core.IconCatalogEntry
import com.github.khopland.symbols.core.IconMappings
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.datatransfer.StringSelection
import java.util.Locale
import javax.swing.Action
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Searchable browser for every bundled Emberkai and upstream Symbols icon. */
internal class IconCatalogDialog(
    private val project: Project,
    private val selectable: Boolean,
    private val onSelect: ((IconCatalogEntry) -> Unit)? = null,
) : DialogWrapper(project) {
    private val allEntries = IconMappings.catalog()
    private val iconCache = mutableMapOf<String, Icon?>()
    private val model = DefaultListModel<IconCatalogEntry>()
    private val filter = JBTextField().apply {
        emptyText.text = "Filter by key or path…"
        accessibleContext.accessibleName = "Filter bundled icons"
    }
    private val count = JLabel(" ")
    private val lightPreview = JLabel("", SwingConstants.CENTER)
    private val darkPreview = JLabel("", SwingConstants.CENTER)
    private val list = JList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        accessibleContext.accessibleName = "Bundled icons"
        cellRenderer = CatalogRenderer()
        fixedCellHeight = 26
        addListSelectionListener { updatePreview() }
    }
    private var selected: IconCatalogEntry? = null

    init {
        title = if (selectable) "Choose a Bundled Icon" else "Browse Bundled Icons"
        isResizable = true
        if (selectable) setOKButtonText("Use Icon") else setCancelButtonText("Close")
        init()
        rebuild()
    }

    override fun createCenterPanel(): JComponent {
        val content = JPanel(BorderLayout(0, 8)).apply {
            border = EmptyBorder(12, 12, 8, 12)
            preferredSize = Dimension(760, 520)
            add(JPanel(BorderLayout(8, 0)).apply {
                add(JLabel("Filter:"), BorderLayout.WEST)
                add(filter, BorderLayout.CENTER)
                add(count, BorderLayout.EAST)
            }, BorderLayout.NORTH)

            val body = JPanel(BorderLayout(8, 0))
            body.add(JBScrollPane(list), BorderLayout.CENTER)

            val preview = JPanel(BorderLayout(0, 6)).apply {
                preferredSize = Dimension(190, 0)
                border = javax.swing.BorderFactory.createTitledBorder("Preview")
                add(JPanel(GridLayout(2, 1, 0, 4)).apply {
                    add(previewRow("Light", lightPreview))
                    add(previewRow("Dark", darkPreview))
                }, BorderLayout.CENTER)
                add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                    add(JButton("Copy key").apply {
                        addActionListener { copySelected { it.key } }
                    })
                    add(JButton("Copy path").apply {
                        addActionListener { copySelected { it.resourcePath } }
                    })
                }, BorderLayout.SOUTH)
            }
            body.add(preview, BorderLayout.EAST)
            add(body, BorderLayout.CENTER)
        }
        filter.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = rebuild()
            override fun removeUpdate(event: DocumentEvent) = rebuild()
            override fun changedUpdate(event: DocumentEvent) = rebuild()
        })
        return content
    }

    override fun createActions(): Array<Action> =
        if (selectable) arrayOf(okAction, cancelAction) else arrayOf(cancelAction)

    override fun getPreferredFocusedComponent(): JComponent = filter

    override fun doOKAction() {
        if (selectable) {
            val entry = selected ?: return
            onSelect?.invoke(entry)
        }
        super.doOKAction()
    }

    private fun rebuild() {
        val query = filter.text.trim().lowercase(Locale.ROOT)
        val matches = allEntries.filter {
            query.isEmpty() ||
                it.key.lowercase(Locale.ROOT).contains(query) ||
                it.resourcePath.lowercase(Locale.ROOT).contains(query)
        }
        model.clear()
        matches.forEach(model::addElement)
        count.text = "${matches.size} of ${allEntries.size}"
        if (matches.isNotEmpty()) list.selectedIndex = 0 else updatePreview()
    }

    private fun updatePreview() {
        selected = list.selectedValue
        val entry = selected
        val icon = entry?.let(::cachedIcon)
        lightPreview.icon = icon?.let { IconLoader.getDarkIcon(it, false) }
        darkPreview.icon = icon?.let { IconLoader.getDarkIcon(it, true) }
        lightPreview.text = if (icon == null) "Select an icon" else ""
    }

    private fun cachedIcon(entry: IconCatalogEntry): Icon? = synchronized(iconCache) {
        if (iconCache.containsKey(entry.resourcePath)) return iconCache[entry.resourcePath]
        val icon = entry.icon
        iconCache[entry.resourcePath] = icon
        icon
    }

    private fun copySelected(value: (IconCatalogEntry) -> String) {
        selected?.let { CopyPasteManager.getInstance().setContents(StringSelection(value(it))) }
    }

    private fun previewRow(label: String, image: JLabel): JComponent = JPanel(BorderLayout()).apply {
        add(JLabel(label), BorderLayout.NORTH)
        image.preferredSize = Dimension(160, 40)
        image.accessibleContext.accessibleDescription = "$label theme icon preview"
        add(image, BorderLayout.CENTER)
    }

    private inner class CatalogRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val entry = value as? IconCatalogEntry
            icon = entry?.let(::cachedIcon)
            text = entry?.key ?: value?.toString().orEmpty()
            toolTipText = entry?.let { "${it.resourcePath} · ${it.source.displayName}" }
            return this
        }
    }
}
