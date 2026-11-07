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

internal data object ExposedSecretFact : IconFact {
    override val label: String get() = "not ignored by VCS — may contain secrets"
    override val tooltipOrder: Int get() = 90
}

/** Kubernetes Secrets in a file VCS would commit, whose values are readable by anyone with the file. */
internal data class PlaintextSecretFact(val secrets: List<PlaintextSecret>) : IconFact {
    override val label: String
        get() = if (secrets.size == 1) "plaintext Secret not ignored by VCS" else "${secrets.size} plaintext Secrets not ignored by VCS"
    override val tooltipOrder: Int get() = 90
}

/** npm or Yarn registry credentials in a file VCS would commit. */
internal data class RegistryTokenFact(val tokens: List<RegistryToken>) : IconFact {
    override val label: String
        get() = if (tokens.size == 1) "registry token not ignored by VCS" else "${tokens.size} registry tokens not ignored by VCS"
    override val tooltipOrder: Int get() = 90
}

/**
 * Warns about likely credentials that VCS would pick up: files named like keys and credentials,
 * Kubernetes manifests with plaintext Secrets, and `.npmrc` or `.yarnrc.yml` files with literal
 * registry tokens. Every check reads only the name or cached content before any VCS lookup.
 */
internal class UnignoredSecretDecorator : IconDecorator {
    override fun decorateFile(resolution: IconResolution, file: VirtualFile, project: Project?): IconResolution {
        val settings = EmberkaiIconsSettings.getInstance()
        if (!settings.isEnabled(IconRuleCategory.UNIGNORED_SECRETS)) return resolution
        val secrets = if (SecretFileNames.isCandidate(file)) null else KubernetesSecrets.plaintext(file)
        val tokens = if (secrets == null || secrets.isNotEmpty()) emptyList() else RegistryTokens.literal(file)
        if (secrets != null && secrets.isEmpty() && tokens.isEmpty()) return resolution
        if (project == null || project.isDisposed || project.isDefault) return resolution
        val exposure = SecretFileExposure.getInstance(project) ?: return resolution
        if (!exposure.isExposed(file)) return resolution
        val baseIcon = resolution.iconOrFileTypeIcon(file) ?: return resolution
        val badge = UnignoredSecretBadgeIcon(baseIcon, highContrast = settings.highContrastFolderIcons || settings.monochromeFolderIcons)
        if (secrets == null) {
            return resolution.decorated(
                badge,
                ExposedSecretFact,
                "A red warning badge marks a file that usually holds credentials " +
                    "or private keys and is inside a VCS root without being ignored, so it could be committed. " +
                    "Ignore it in VCS (for example in .gitignore) to clear the badge.",
            )
        }
        if (tokens.isNotEmpty()) {
            return resolution.decorated(
                badge,
                RegistryTokenFact(tokens),
                "A red warning badge marks package registry credentials written into this file, in a VCS " +
                    "root without being ignored: " +
                    tokens.joinToString("; ") { "line ${it.line}: ${it.key}" } +
                    ". Reference an environment variable instead, such as \${NPM_TOKEN}, or ignore the " +
                    "file in VCS to clear the badge.",
            )
        }
        return resolution.decorated(
            badge,
            PlaintextSecretFact(secrets),
            "A red warning badge marks Kubernetes Secrets whose values are in this file, in a VCS root " +
                "without being ignored: " +
                secrets.joinToString("; ") { secret ->
                    "line ${secret.line}: ${secret.name ?: "unnamed Secret"} (${secret.keys.joinToString(", ")})"
                } +
                ". Base64 in data is an encoding, not encryption. Encrypt the file with SOPS, use a Sealed " +
                "Secret or an External Secret, or ignore the file in VCS to clear the badge.",
        )
    }
}
