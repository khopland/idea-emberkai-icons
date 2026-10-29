package com.github.khopland.symbols.settings

import com.github.khopland.symbols.badges.EnvironmentVariant
import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.ownership.CodeOwners
import com.github.khopland.symbols.ownership.CodeOwnersRule
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.scope.packageSet.AbstractPackageSet
import com.intellij.psi.search.scope.packageSet.CustomScopesProvider
import com.intellij.psi.search.scope.packageSet.NamedScope
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder

/** IDE scopes built from the resolver's context rules; folders are never members themselves. */
internal enum class EmberkaiScope(val displayName: String, private val categories: Set<IconRuleCategory>) {
    MIGRATIONS("Database migrations", setOf(IconRuleCategory.MIGRATION)),
    INFRASTRUCTURE("Infrastructure", setOf(IconRuleCategory.INFRASTRUCTURE, IconRuleCategory.ANSIBLE)),
    KUBERNETES(
        "Kubernetes & GitOps",
        setOf(IconRuleCategory.KUBERNETES, IconRuleCategory.HELM, IconRuleCategory.GITOPS),
    ),
    API_CONTRACTS("API contracts", setOf(IconRuleCategory.API_SPECIFICATION)),
    CI_PIPELINES("CI pipelines", setOf(IconRuleCategory.CI)),
    SPRING_CONFIGURATION("Spring configuration", setOf(IconRuleCategory.SPRING_BOOT)),
    PRODUCTION_CONFIG("Production config", emptySet()),
    ;

    val scopeName: String get() = "Emberkai: $displayName"

    fun contains(file: VirtualFile, project: Project): Boolean {
        if (file.isDirectory || !file.isValid || project.isDisposed) return false
        if (this == PRODUCTION_CONFIG) return EnvironmentVariant.fromFile(file) == EnvironmentVariant.PRODUCTION
        // Rules read VFS children and cached documents; Find in Files may call from a pooled thread.
        return runReadActionBlocking { IconResolver.recognizedCategory(file, project) } in categories
    }
}

/**
 * The fixed Emberkai scopes, plus one "Owned by" scope per owner named in the project's CODEOWNERS
 * file. Owner scopes are rebuilt only when the parsed CODEOWNERS rules change, and capped so a
 * large organization does not flood scope pickers.
 */
class EmberkaiScopesProvider(private val project: Project) : CustomScopesProvider {
    private val scopes: List<NamedScope> = EmberkaiScope.entries.map { scope ->
        NamedScope(scope.scopeName, EmberkaiPackageSet(scope))
    }

    @Volatile
    private var ownerScopes: Pair<List<CodeOwnersRule>, List<NamedScope>>? = null

    override fun getCustomScopes(): List<NamedScope> = scopes + ownerScopes()

    private fun ownerScopes(): List<NamedScope> {
        if (project.isDisposed) return emptyList()
        val codeOwners = project.guessProjectDir()?.let(CodeOwners::fileAt) ?: return emptyList()
        val rules = runReadActionBlocking { CodeOwners.rules(codeOwners) }
        ownerScopes?.takeIf { it.first === rules }?.let { return it.second }
        val built = rules.flatMap { it.owners }.distinct()
            .sortedBy { it.lowercase() }
            .take(MAX_OWNER_SCOPES)
            .map { owner -> NamedScope(ownerScopeName(owner), OwnerPackageSet(owner)) }
        ownerScopes = rules to built
        return built
    }

    internal companion object {
        const val MAX_OWNER_SCOPES = 50

        fun ownerScopeName(owner: String): String = "Emberkai: Owned by $owner"
    }
}

private class OwnerPackageSet(private val owner: String) : AbstractPackageSet("emberkai:owner:$owner") {
    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean {
        if (file.isDirectory || !file.isValid || project.isDisposed) return false
        return runReadActionBlocking { CodeOwners.ownership(file) }?.owners?.contains(owner) == true
    }
}

private class EmberkaiPackageSet(
    private val scope: EmberkaiScope,
) : AbstractPackageSet("emberkai:${scope.name.lowercase()}") {
    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean =
        scope.contains(file, project)
}
