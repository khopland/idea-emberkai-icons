package com.github.khopland.symbols.badges

import com.github.khopland.symbols.core.IconDecorator
import com.github.khopland.symbols.core.IconFact
import com.github.khopland.symbols.core.IconResolution
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.decorated
import com.github.khopland.symbols.core.iconOrFileTypeIcon
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.isEnabled
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

internal data class EnvironmentVariantFact(val variant: EnvironmentVariant) : IconFact {
    override val label: String get() = variant.displayName
    override val tooltipOrder: Int get() = 10

    /** Variants whose base icon is an ordinary mapping still say what kind of file they are. */
    override fun baseLabel(file: VirtualFile): String {
        val name = file.name.lowercase(Locale.ROOT)
        return VARIANT_LABELS.firstOrNull { (prefix, _) -> name.startsWith(prefix) }?.second
            ?: if (file.isDirectory) "Environment folder" else "Environment configuration"
    }

    private companion object {
        val VARIANT_LABELS = listOf(
            "application" to "Configuration profile",
            "bootstrap" to "Configuration profile",
            "docker-compose" to "Docker Compose file",
            "values" to "Values file",
            ".env" to "Environment file",
        )
    }
}

internal class EnvironmentVariantDecorator : IconDecorator {
    override fun decorateFile(resolution: IconResolution, file: VirtualFile, project: Project?): IconResolution {
        if (!EmberkaiIconsSettings.getInstance().isEnabled(IconRuleCategory.ENVIRONMENT_VARIANTS)) return resolution
        val variant = EnvironmentVariant.fromFile(file) ?: return resolution
        val baseIcon = resolution.iconOrFileTypeIcon(file) ?: return resolution
        val reason = EnvironmentVariant.environmentFolderOf(file)
            ?.takeIf { EnvironmentVariant.isInherited(file) }
            ?.let { "This configuration file inherits it from the ${it.parent.name}/${it.name}/ environment folder." }
            ?: "It marks this configuration variant."
        return resolution.decorated(
            EnvironmentVariantBadgeIcon(baseIcon, variant),
            EnvironmentVariantFact(variant),
            "A ${variant.displayName} environment badge is shown. $reason",
        )
    }

    /** Environment folders such as `overlays/production/` carry the same badge as the files inside them. */
    override fun decorateFolder(resolution: IconResolution, directory: VirtualFile, project: Project?): IconResolution {
        val icon = resolution.icon ?: return resolution
        if (!EmberkaiIconsSettings.getInstance().isEnabled(IconRuleCategory.ENVIRONMENT_VARIANTS)) return resolution
        val variant = EnvironmentVariant.fromDirectory(directory) ?: return resolution
        return resolution.decorated(
            EnvironmentVariantBadgeIcon(icon, variant),
            EnvironmentVariantFact(variant),
            "A ${variant.displayName} environment badge marks an environment folder; " +
                "configuration files inside it inherit the badge.",
        )
    }
}
