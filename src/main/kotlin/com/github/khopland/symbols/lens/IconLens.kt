package com.github.khopland.symbols.lens

import com.github.khopland.symbols.badges.SecretFileExposure
import com.github.khopland.symbols.badges.SecretFileNames
import com.github.khopland.symbols.ownership.CodeOwners
import com.github.khopland.symbols.settings.EmberkaiScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * A question the Project view can answer. Files that answer it keep their full presentation, and
 * everything else is dimmed. Lenses compare by value, so selecting an equal lens is a no-op.
 */
internal interface Lens {
    val displayName: String

    /** Folders are never members; they stay highlighted when they contain a member. */
    fun contains(file: VirtualFile, project: Project): Boolean
}

/**
 * The fixed lenses. Membership comes from the Emberkai scopes, so a lens and the scopes it names
 * always agree; the blast-radius lens also includes secrets that VCS would pick up.
 */
internal enum class IconLens(
    override val displayName: String,
    private val scopes: Set<EmberkaiScope>,
    private val exposedSecrets: Boolean = false,
) : Lens {
    PRODUCTION_BLAST_RADIUS(
        "Production blast radius",
        setOf(EmberkaiScope.PRODUCTION_CONFIG, EmberkaiScope.MIGRATIONS),
        exposedSecrets = true,
    ),
    INFRASTRUCTURE("Infrastructure", setOf(EmberkaiScope.INFRASTRUCTURE, EmberkaiScope.KUBERNETES)),
    API_CONTRACTS("API contracts", setOf(EmberkaiScope.API_CONTRACTS)),
    CI_PIPELINES("CI pipelines", setOf(EmberkaiScope.CI_PIPELINES)),
    MIGRATIONS("Database migrations", setOf(EmberkaiScope.MIGRATIONS)),
    SPRING_CONFIGURATION("Spring configuration", setOf(EmberkaiScope.SPRING_CONFIGURATION)),
    ;

    override fun contains(file: VirtualFile, project: Project): Boolean {
        if (file.isDirectory) return false
        if (scopes.any { it.contains(file, project) }) return true
        return exposedSecrets &&
            SecretFileNames.isCandidate(file) &&
            SecretFileExposure.getInstance(project)?.isExposed(file) == true
    }
}

/** Files whose CODEOWNERS line names [owner]. */
internal data class OwnerLens(val owner: String) : Lens {
    override val displayName: String get() = "Owned by $owner"

    override fun contains(file: VirtualFile, project: Project): Boolean =
        !file.isDirectory && CodeOwners.ownership(file)?.owners?.contains(owner) == true
}
