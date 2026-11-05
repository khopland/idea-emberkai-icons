package com.github.khopland.symbols.core

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * Layers a badge or marker on top of the icon [IconResolver] chose, and records why as an [IconFact].
 * Decorators run in the order they are registered in plugin.xml, so later ones wrap earlier badges.
 * Files whose native icon policy took precedence are never decorated; folders are, but carry no icon.
 */
internal interface IconDecorator {
    fun decorateFile(resolution: IconResolution, file: VirtualFile, project: Project?): IconResolution = resolution

    fun decorateFolder(resolution: IconResolution, directory: VirtualFile, project: Project?): IconResolution = resolution

    companion object {
        val EP_NAME = ExtensionPointName.create<IconDecorator>("com.github.khopland.symbols.iconDecorator")
    }
}

/** The chosen icon, or the file type's icon when Emberkai left the file to the IDE. */
internal fun IconResolution.iconOrFileTypeIcon(file: VirtualFile): Icon? =
    icon ?: FileTypeManager.getInstance().getFileTypeByFile(file).icon

/**
 * Replaces the icon with [icon], records [fact], and appends [detail] to the explanation. A badge
 * that loses its corner to a more urgent one is not drawn, but its fact and detail are kept, so
 * tooltips and Explain This Icon still report it.
 */
internal fun IconResolution.decorated(icon: Icon, fact: IconFact?, detail: String): IconResolution {
    val badge = icon as? BadgedIcon
    val shown = badge?.claimSlot() ?: icon
    val hidden = if (badge != null && shown === badge.baseIcon) " A more urgent badge is drawn in its place." else ""
    return copy(icon = shown, facts = facts + listOfNotNull(fact), detail = "${this.detail} $detail$hidden")
}
