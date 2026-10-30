package com.github.khopland.symbols.spring

import com.github.khopland.symbols.core.IconDecorator
import com.github.khopland.symbols.core.IconFact
import com.github.khopland.symbols.core.IconResolution
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.decorated
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.isEnabled
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/** The run configuration that activates this Spring profile file. */
internal data class ActiveProfileFact(val configuration: String) : IconFact {
    override val label: String get() = "active in '$configuration'"
    override val tooltipOrder: Int get() = 20
}

/** Only files the Spring Boot rule recognized can carry the live marker; the lookup is a set test. */
internal class ActiveSpringProfileDecorator : IconDecorator {
    override fun decorateFile(resolution: IconResolution, file: VirtualFile, project: Project?): IconResolution {
        if (resolution.contextRule != IconRuleCategory.SPRING_BOOT) return resolution
        if (project == null || project.isDisposed) return resolution
        val settings = EmberkaiIconsSettings.getInstance()
        if (!settings.isEnabled(IconRuleCategory.ACTIVE_SPRING_PROFILES)) return resolution
        val profile = ActiveSpringProfiles.profileOf(file.name.lowercase(Locale.ROOT)) ?: return resolution
        val configuration = ActiveSpringProfiles.getIfCreated(project)?.configurationFor(profile) ?: return resolution
        val icon = resolution.icon ?: return resolution
        return resolution.decorated(
            ActiveSpringProfileIcon(icon, settings.highContrastFolderIcons, settings.monochromeFolderIcons),
            ActiveProfileFact(configuration),
            "A live marker shows \"$profile\" is an active profile in run configuration '$configuration'.",
        )
    }
}
