package com.github.khopland.symbols.accents

import com.github.khopland.symbols.core.IconDecorator
import com.github.khopland.symbols.core.IconFact
import com.github.khopland.symbols.core.IconResolution
import com.github.khopland.symbols.core.decorated
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal data class ModuleAccentFact(val accent: ModuleAccent) : IconFact {
    override val label: String get() = "module accent: ${accent.moduleName}"
    override val tooltipOrder: Int get() = 30
}

/**
 * Module accents ride on top of the selected Emberkai folder artwork. Service roots already use a
 * full palette color, so they are left alone; content roots are skipped to keep the project root
 * quiet. Everything below a module (source roots, packages, and other folders) gets the small dot.
 */
internal class ModuleAccentDecorator : IconDecorator {
    override fun decorateFolder(resolution: IconResolution, directory: VirtualFile, project: Project?): IconResolution {
        val icon = resolution.icon ?: return resolution
        if (project == null || project.isDisposed) return resolution
        val settings = EmberkaiIconsSettings.getInstance()
        if (!settings.moduleAwareColors) return resolution
        val mappingKey = resolution.mappingKey
        if (mappingKey == null ||
            mappingKey == "folder.role.content-root" ||
            mappingKey.startsWith("folder.service.")
        ) {
            return resolution
        }
        val accent = ModuleAccentStore.getInstance(project)
            .accentFor(directory, settings.serviceFolderPalette) ?: return resolution
        return resolution.decorated(
            ModuleAccentIcon(icon, accent.color),
            ModuleAccentFact(accent),
            "The module accent follows ${accent.moduleName}.",
        )
    }
}
