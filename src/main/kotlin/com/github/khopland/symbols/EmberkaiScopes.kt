package com.github.khopland.symbols

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
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

class EmberkaiScopesProvider : CustomScopesProvider {
    private val scopes: List<NamedScope> = EmberkaiScope.entries.map { scope ->
        NamedScope(scope.scopeName, EmberkaiPackageSet(scope))
    }

    override fun getCustomScopes(): List<NamedScope> = scopes
}

private class EmberkaiPackageSet(
    private val scope: EmberkaiScope,
) : AbstractPackageSet("emberkai:${scope.name.lowercase()}") {
    override fun contains(file: VirtualFile, project: Project, holder: NamedScopesHolder?): Boolean =
        scope.contains(file, project)
}
