package com.github.khopland.emberkai.badges

import com.github.khopland.emberkai.core.IconDecorator
import com.github.khopland.emberkai.core.IconFact
import com.github.khopland.emberkai.core.IconResolution
import com.github.khopland.emberkai.core.IconRuleCategory
import com.github.khopland.emberkai.core.decorated
import com.github.khopland.emberkai.core.iconOrFileTypeIcon
import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.github.khopland.emberkai.settings.isEnabled
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** The image and action references in a file that are not pinned. */
internal data class UnpinnedReferenceFact(val references: List<UnpinnedReference>) : IconFact {
    override val label: String
        get() = if (references.size == 1) "unpinned ${references.single().reference}" else "${references.size} unpinned references"
    override val tooltipOrder: Int get() = 65
}

/**
 * Warns about workflows, Dockerfiles, Kubernetes manifests, and Compose files whose actions or
 * images can change without the file changing.
 */
internal class UnpinnedReferenceDecorator : IconDecorator {
    override fun decorateFile(resolution: IconResolution, file: VirtualFile, project: Project?): IconResolution {
        val settings = EmberkaiIconsSettings.getInstance()
        if (!settings.isEnabled(IconRuleCategory.UNPINNED_REFERENCES)) return resolution
        val references = UnpinnedReferences.find(file).takeIf { it.isNotEmpty() } ?: return resolution
        val baseIcon = resolution.iconOrFileTypeIcon(file) ?: return resolution
        return resolution.decorated(
            UnpinnedReferenceBadgeIcon(baseIcon, settings.highContrastFolderIcons || settings.monochromeFolderIcons),
            UnpinnedReferenceFact(references),
            "A warning badge marks references that can change without this file changing: " +
                references.joinToString("; ") { "line ${it.line}: ${it.reference} ${it.reason}" } +
                ". Pin actions to a commit SHA and images to a version tag or digest.",
        )
    }
}
