package com.github.khopland.symbols.core

import com.github.khopland.symbols.badges.FlywayMigrations
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/**
 * Short human descriptions of what Emberkai recognized, built from an [IconResolution]. Only
 * contextual decisions are described: plain extension or filename matches return null so tooltips
 * stay quiet. Add a label to [LABELS] when a new rule or notable mapping key is introduced.
 */
internal object IconDescriptions {
    private val LABELS = mapOf(
        "context.ansible" to "Ansible configuration",
        "context.ansible-folder" to "Ansible folder",
        "context.argocd" to "Argo CD resource",
        "context.argocd-folder" to "Argo CD folder",
        "context.ci.azurepipelines" to "Azure Pipelines configuration",
        "context.ci.bitbucket" to "Bitbucket Pipelines configuration",
        "context.flux" to "Flux resource",
        "context.flux-folder" to "Flux bootstrap folder",
        "context.helm" to "Helm chart template",
        "context.helm-folder" to "Helm chart folder",
        "context.infrastructure" to "Infrastructure as code",
        "context.opentofu" to "OpenTofu configuration",
        "context.opentofu-folder" to "OpenTofu folder",
        "context.kubernetes" to "Kubernetes manifest",
        "context.kustomize" to "Kustomization",
        "context.kustomize-folder" to "Kustomize folder",
        "context.migration" to "Database migration",
        "context.migration-folder" to "Database migrations folder",
        "context.openapi" to "OpenAPI contract",
        "context.openapi-folder" to "API contracts folder",
        "context.spring" to "Spring Boot configuration",
        "context.quarkus" to "Quarkus configuration",
        "context.micronaut" to "Micronaut configuration",
        "context.ktor" to "Ktor configuration",
        "context.asyncapi" to "AsyncAPI contract",
        "context.tekton" to "Tekton resource",
        "context.skaffold" to "Skaffold configuration",
        "file..circleci/config.yml" to "CircleCI configuration",
        "file..circleci/config.yaml" to "CircleCI configuration",
        "context.architecture.client" to "Client package",
        "context.architecture.config" to "Configuration package",
        "context.architecture.controller" to "Controller package",
        "context.architecture.domain" to "Domain package",
        "context.architecture.dto" to "DTO package",
        "context.architecture.exception" to "Exception package",
        "context.architecture.mapper" to "Mapper package",
        "context.architecture.repository" to "Repository package",
        "context.architecture.security" to "Security package",
        "context.architecture.service" to "Service package",
        "context.architecture.util" to "Utility package",
        "folder.role.content-root" to "Content root",
        "folder.role.source" to "Source root",
        "folder.role.test-source" to "Test source root",
        "folder.role.resources" to "Resources root",
        "folder.role.test-resources" to "Test resources root",
        "extension..github/workflows/yml" to "GitHub Actions workflow",
        "extension..github/workflows/yaml" to "GitHub Actions workflow",
        "folder..github/workflows" to "GitHub Actions workflows folder",
        "file.action.yml" to "GitHub Action metadata",
        "file.action.yaml" to "GitHub Action metadata",
        "file..github/dependabot.yml" to "Dependabot configuration",
        "file..github/dependabot.yaml" to "Dependabot configuration",
    )

    /** Labels for environment variants whose base icon is an ordinary mapping. */
    private val VARIANT_LABELS = listOf(
        "application" to "Configuration profile",
        "bootstrap" to "Configuration profile",
        "docker-compose" to "Docker Compose file",
        "values" to "Values file",
        ".env" to "Environment file",
    )

    private const val SERVICE_ROOT = "Service root"
    private const val ENVIRONMENT_FOLDER = "Environment folder"
    private const val ENVIRONMENT_CONFIGURATION = "Environment configuration"
    private const val PROJECT_OVERRIDE = "Project icon override"
    private const val SEPARATOR = " · "
    private const val SECRET_WARNING = "not ignored by VCS — may contain secrets"

    fun describe(file: VirtualFile, resolution: IconResolution): String? {
        val name = file.name.lowercase(Locale.ROOT)
        val variant = resolution.environmentVariant
        val base = label(file, name, resolution)
            ?: variant?.let { VARIANT_LABELS.firstOrNull { (prefix, _) -> name.startsWith(prefix) }?.second }
            ?: variant?.let { if (file.isDirectory) ENVIRONMENT_FOLDER else ENVIRONMENT_CONFIGURATION }
        val parts = listOfNotNull(
            base,
            variant?.displayName,
            resolution.activeProfileConfiguration?.let { "active in '$it'" },
            resolution.moduleAccent?.let { "module accent: ${it.moduleName}" },
            resolution.migrationConflict?.let { "duplicate version V$it" },
            migrationFolderSummary(file, resolution),
            if (resolution.secretExposed) SECRET_WARNING else null,
        )
        if (parts.isEmpty()) return null
        return parts.joinToString(SEPARATOR).replaceFirstChar { it.uppercase(Locale.ROOT) }
    }

    /** Migration folders summarize their Flyway versions, such as "12 versioned · latest V12 · 1 version conflict". */
    private fun migrationFolderSummary(file: VirtualFile, resolution: IconResolution): String? {
        if (resolution.mappingKey != "context.migration-folder") return null
        val summary = FlywayMigrations.summary(file) ?: return null
        val conflicts = summary.conflicts.size
        return listOfNotNull(
            "${summary.versionedCount} versioned",
            summary.latestVersion?.let { "latest V$it" },
            when (conflicts) {
                0 -> null
                1 -> "1 version conflict"
                else -> "$conflicts version conflicts"
            },
        ).joinToString(SEPARATOR)
    }

    private fun label(file: VirtualFile, name: String, resolution: IconResolution): String? {
        val key = resolution.mappingKey
        if (resolution.mappingSource == MappingSource.PROJECT_OVERRIDE) return PROJECT_OVERRIDE
        return when (resolution.precedence) {
            IconPrecedence.CONTEXT_RULE ->
                refined(key, file, name) ?: LABELS[key] ?: resolution.contextRule?.displayName
            IconPrecedence.PROJECT_ROLE, IconPrecedence.MAPPING ->
                if (key?.startsWith("folder.service.") == true) SERVICE_ROOT else LABELS[key]
            else -> null
        }
    }

    /** A few keys cover several tool files; the filename tells them apart. */
    private fun refined(key: String?, file: VirtualFile, name: String): String? =
        when (key) {
            "context.spring" -> if (name.endsWith(".xml")) "Spring XML configuration" else null
            "context.migration" -> when {
                name.startsWith("db.changelog") -> "Liquibase changelog"
                name.startsWith("r__") -> "Flyway repeatable migration"
                name.startsWith("v") && name.endsWith(".sql") -> "Flyway versioned migration"
                else -> null
            }
            "context.helm" -> when {
                name == "chart.yaml" -> "Helm chart"
                name == "chart.lock" -> "Helm chart lock"
                name.startsWith("values") -> "Helm chart values"
                file.parent?.name == "crds" -> "Helm chart CRD"
                else -> null
            }
            else -> if (key?.startsWith("context.kubernetes.") == true) LABELS["context.kubernetes"] else null
        }
}
