package com.github.khopland.symbols.core

import com.github.khopland.symbols.accents.ModuleAccentIcon
import com.github.khopland.symbols.accents.ModuleAccentStore
import com.github.khopland.symbols.badges.EnvironmentVariant
import com.github.khopland.symbols.badges.EnvironmentVariantBadgeIcon
import com.github.khopland.symbols.badges.FlywayMigrations
import com.github.khopland.symbols.badges.MigrationConflictBadgeIcon
import com.github.khopland.symbols.badges.SecretFileExposure
import com.github.khopland.symbols.badges.SecretFileNames
import com.github.khopland.symbols.badges.UnignoredSecretBadgeIcon
import com.github.khopland.symbols.folders.ProjectFolderIcons
import com.github.khopland.symbols.folders.ServiceFolderIcons
import com.github.khopland.symbols.folders.SourceSetFolders
import com.github.khopland.symbols.overrides.ProjectIconOverride
import com.github.khopland.symbols.overrides.ProjectIconOverrideStore
import com.github.khopland.symbols.rules.AnsibleIconRule
import com.github.khopland.symbols.rules.ApiSpecificationIconRule
import com.github.khopland.symbols.rules.CiConfigIconRule
import com.github.khopland.symbols.rules.GitOpsIconRule
import com.github.khopland.symbols.rules.HelmIconRule
import com.github.khopland.symbols.rules.InfrastructureIconRule
import com.github.khopland.symbols.rules.JvmArchitectureFolderIconRule
import com.github.khopland.symbols.rules.JvmFrameworkIconRule
import com.github.khopland.symbols.rules.KubernetesIconRule
import com.github.khopland.symbols.rules.LiquibaseIconRule
import com.github.khopland.symbols.rules.MigrationIconRule
import com.github.khopland.symbols.rules.SpringBootIconRule
import com.github.khopland.symbols.rules.TerraformIconRule
import com.github.khopland.symbols.run.RunningServiceIcon
import com.github.khopland.symbols.run.RunningServices
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.isEnabled
import com.github.khopland.symbols.spring.ActiveSpringProfileIcon
import com.github.khopland.symbols.spring.ActiveSpringProfiles
import com.github.khopland.symbols.spring.SpringXmlConfigDetector
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import javax.swing.Icon

/** Defines icon precedence; add new tool detectors to contextRules in priority order. */
internal object IconResolver {
    private val architectureRule = JvmArchitectureFolderIconRule()
    private val contextRules = listOf(
        CiConfigIconRule(),
        LiquibaseIconRule(),
        HelmIconRule(),
        AnsibleIconRule(),
        TerraformIconRule(),
        InfrastructureIconRule(),
        GitOpsIconRule(),
        MigrationIconRule(),
        ApiSpecificationIconRule(),
        KubernetesIconRule(),
        SpringBootIconRule(),
        JvmFrameworkIconRule(),
        architectureRule,
    )

    fun resolve(file: VirtualFile, project: Project?): Icon? = resolveWithDetails(file, project).icon

    fun resolveWithDetails(file: VirtualFile, project: Project?): IconResolution {
        val name = file.name.lowercase(Locale.ROOT)
        if (file.isDirectory) return folderIcon(file, name, project)
        val resolution = fileIcon(file, name, project)
        if (resolution.precedence == IconPrecedence.NATIVE_POLICY) return resolution
        val badged = applySecretBadge(applyEnvironmentBadge(resolution, file), file, project)
        return applyMigrationConflict(applyActiveProfile(badged, name, project), file, project)
    }

    /**
     * The first context rule that recognizes [file], in icon precedence order. Category toggles are
     * ignored: they only choose artwork, while scopes describe what a file is.
     */
    fun recognizedCategory(file: VirtualFile, project: Project?): IconRuleCategory? {
        if (file.isDirectory) return null
        val name = file.name.lowercase(Locale.ROOT)
        return contextRules.firstOrNull { it.recognizesFile(file, name, project) }?.category
    }

    private fun applyEnvironmentBadge(resolution: IconResolution, file: VirtualFile): IconResolution {
        if (!EmberkaiIconsSettings.getInstance().isEnabled(IconRuleCategory.ENVIRONMENT_VARIANTS)) return resolution
        val variant = EnvironmentVariant.fromFile(file) ?: return resolution
        val baseIcon = resolution.icon ?: FileTypeManager.getInstance().getFileTypeByFile(file).icon ?: return resolution
        val reason = EnvironmentVariant.environmentFolderOf(file)
            ?.takeIf { EnvironmentVariant.isInherited(file) }
            ?.let { "This configuration file inherits it from the ${it.parent.name}/${it.name}/ environment folder." }
            ?: "It marks this configuration variant."
        return resolution.copy(
            icon = EnvironmentVariantBadgeIcon(baseIcon, variant),
            environmentVariant = variant,
            detail = "${resolution.detail} A ${variant.displayName} environment badge is shown. $reason",
        )
    }

    /**
     * Warns about a Flyway version that a sibling migration also uses, which Flyway refuses to run, or
     * about a migration this branch added below the default branch's latest version, which Flyway
     * rejects as out of order once that branch's migrations have run.
     */
    private fun applyMigrationConflict(resolution: IconResolution, file: VirtualFile, project: Project?): IconResolution {
        val settings = EmberkaiIconsSettings.getInstance()
        if (!settings.isEnabled(IconRuleCategory.MIGRATION_CONFLICTS)) return resolution
        val conflict = FlywayMigrations.conflict(file)
        val outOfOrder = if (conflict == null) FlywayMigrations.outOfOrder(file, project) else null
        if (conflict == null && outOfOrder == null) return resolution
        val baseIcon = resolution.icon ?: FileTypeManager.getInstance().getFileTypeByFile(file).icon ?: return resolution
        val icon = MigrationConflictBadgeIcon(baseIcon, settings.highContrastFolderIcons || settings.monochromeFolderIcons)
        if (conflict != null) {
            val (version, others) = conflict
            return resolution.copy(
                icon = icon,
                migrationConflict = version,
                detail = "${resolution.detail} A warning badge marks a duplicate Flyway version: V$version is also used by " +
                    "${others.joinToString()}. Flyway fails validation until one of them is renumbered.",
            )
        }
        val migration = outOfOrder!!
        return resolution.copy(
            icon = icon,
            migrationOutOfOrder = migration,
            detail = "${resolution.detail} A warning badge marks a migration this branch added at V${migration.version}, " +
                "but ${migration.baseRef} already has migrations up to V${migration.baseLatest} in this folder. " +
                "Once those have run, Flyway rejects it as out of order; renumber it above V${migration.baseLatest}.",
        )
    }

    /** A folder with duplicate or out-of-order Flyway versions carries the warning so it is visible while collapsed. */
    private fun applyFolderMigrationConflict(
        resolution: IconResolution,
        directory: VirtualFile,
        project: Project?,
    ): IconResolution {
        val icon = resolution.icon ?: return resolution
        val settings = EmberkaiIconsSettings.getInstance()
        if (!settings.isEnabled(IconRuleCategory.MIGRATION_CONFLICTS)) return resolution
        val summary = FlywayMigrations.summary(directory) ?: return resolution
        val outOfOrder = FlywayMigrations.outOfOrderIn(directory, project)
        if (summary.conflicts.isEmpty() && outOfOrder.isEmpty()) return resolution
        val problems = listOfNotNull(
            summary.conflicts.takeIf { it.isNotEmpty() }
                ?.let { "duplicate Flyway versions in this folder: " + it.keys.joinToString { version -> "V$version" } },
            outOfOrder.takeIf { it.isNotEmpty() }?.let {
                "migrations this branch added at or below V${it.values.first().baseLatest}, the latest on " +
                    "${it.values.first().baseRef}: ${it.keys.sorted().joinToString()}"
            },
        )
        return resolution.copy(
            icon = MigrationConflictBadgeIcon(icon, settings.highContrastFolderIcons || settings.monochromeFolderIcons),
            detail = "${resolution.detail} A warning badge marks ${problems.joinToString("; and ")}.",
        )
    }

    /** Environment folders such as `overlays/production/` carry the same badge as the files inside them. */
    private fun applyFolderEnvironmentBadge(resolution: IconResolution, directory: VirtualFile): IconResolution {
        val icon = resolution.icon ?: return resolution
        if (!EmberkaiIconsSettings.getInstance().isEnabled(IconRuleCategory.ENVIRONMENT_VARIANTS)) return resolution
        val variant = EnvironmentVariant.fromDirectory(directory) ?: return resolution
        return resolution.copy(
            icon = EnvironmentVariantBadgeIcon(icon, variant),
            environmentVariant = variant,
            detail = "${resolution.detail} A ${variant.displayName} environment badge marks an environment folder; " +
                "configuration files inside it inherit the badge.",
        )
    }

    /** Only files the Spring Boot rule recognized can carry the live marker; the lookup is a set test. */
    private fun applyActiveProfile(resolution: IconResolution, name: String, project: Project?): IconResolution {
        if (resolution.contextRule != IconRuleCategory.SPRING_BOOT) return resolution
        if (project == null || project.isDisposed) return resolution
        val settings = EmberkaiIconsSettings.getInstance()
        if (!settings.isEnabled(IconRuleCategory.ACTIVE_SPRING_PROFILES)) return resolution
        val profile = ActiveSpringProfiles.profileOf(name) ?: return resolution
        val configuration = ActiveSpringProfiles.getIfCreated(project)?.configurationFor(profile) ?: return resolution
        val icon = resolution.icon ?: return resolution
        return resolution.copy(
            icon = ActiveSpringProfileIcon(icon, settings.highContrastFolderIcons, settings.monochromeFolderIcons),
            activeProfileConfiguration = configuration,
            detail = "${resolution.detail} A live marker shows \"$profile\" is an active profile in run configuration '$configuration'.",
        )
    }

    /** Warns about likely credentials that VCS would pick up; the name check runs before any VCS lookup. */
    private fun applySecretBadge(resolution: IconResolution, file: VirtualFile, project: Project?): IconResolution {
        val settings = EmberkaiIconsSettings.getInstance()
        if (!settings.isEnabled(IconRuleCategory.UNIGNORED_SECRETS) || !SecretFileNames.isCandidate(file)) {
            return resolution
        }
        if (project == null || project.isDisposed || project.isDefault) return resolution
        val exposure = SecretFileExposure.getInstance(project) ?: return resolution
        if (!exposure.isExposed(file)) return resolution
        val baseIcon = resolution.icon ?: FileTypeManager.getInstance().getFileTypeByFile(file).icon ?: return resolution
        return resolution.copy(
            icon = UnignoredSecretBadgeIcon(
                baseIcon,
                highContrast = settings.highContrastFolderIcons || settings.monochromeFolderIcons,
            ),
            secretExposed = true,
            detail = "${resolution.detail} A red warning badge marks a file that usually holds credentials " +
                "or private keys and is inside a VCS root without being ignored, so it could be committed. " +
                "Ignore it in VCS (for example in .gitignore) to clear the badge.",
        )
    }

    private fun folderIcon(directory: VirtualFile, name: String, project: Project?): IconResolution =
        applyRunningService(
            applyModuleAccent(
                applyFolderMigrationConflict(
                    applyFolderEnvironmentBadge(baseFolderIcon(directory, name, project), directory),
                    directory,
                    project,
                ),
                directory,
                project,
            ),
            directory,
            project,
        )

    /**
     * A module folder with a live run or debug session carries the running marker. It shares the
     * top-right corner with module accents, which module roots rarely carry, so it replaces the dot.
     */
    private fun applyRunningService(resolution: IconResolution, directory: VirtualFile, project: Project?): IconResolution {
        if (project == null || project.isDisposed) return resolution
        val sessions = RunningServices.getIfCreated(project)?.sessionsFor(directory)?.takeIf { it.isNotEmpty() }
            ?: return resolution
        val settings = EmberkaiIconsSettings.getInstance()
        if (!settings.isEnabled(IconRuleCategory.RUNNING_SERVICES)) return resolution
        val icon = resolution.icon ?: return resolution
        val base = (icon as? ModuleAccentIcon)?.baseIcon ?: icon
        return resolution.copy(
            icon = RunningServiceIcon(
                base,
                debug = sessions.all { it.debug },
                highContrast = settings.highContrastFolderIcons,
                monochrome = settings.monochromeFolderIcons,
            ),
            runningSessions = sessions,
            detail = "${resolution.detail} A running marker shows a live session: " +
                sessions.joinToString("; ") { it.describe() } + ".",
        )
    }

    private fun baseFolderIcon(directory: VirtualFile, name: String, project: Project?): IconResolution {
        if (NativeIconPolicy.usesFolderIcon(directory, name, project)) {
            return IconResolution(
                icon = null,
                precedence = IconPrecedence.NATIVE_POLICY,
                detail = "Native icon policy protects this folder from Emberkai mappings.",
            )
        }

        val projectRole = ProjectFolderIcons.role(directory, project)
        if (projectRole == ProjectFolderIcons.Role.NATIVE) {
            return IconResolution(
                icon = null,
                projectRole = projectRole.displayName(),
                precedence = IconPrecedence.PROJECT_ROLE,
                detail = "The project root model leaves this folder to its native language or generated-source icon.",
            )
        }
        if (project != null && !project.isDisposed) {
            ProjectIconOverrideStore.getInstance(project).folderOverride(directory, name)?.let {
                return it.toResolution(
                    precedence = IconPrecedence.MAPPING,
                    detail = "A project icon override matched this folder.",
                )
            }
        }
        if (projectRole == ProjectFolderIcons.Role.PACKAGE ||
            projectRole == ProjectFolderIcons.Role.TEST_PACKAGE
        ) {
            architectureResolution(directory, project)?.let { return it }
        }
        if (projectRole != null && projectRole != ProjectFolderIcons.Role.CONTENT_ROOT) {
            return roleResolution(projectRole, "The project root model")
        }
        if (projectRole == ProjectFolderIcons.Role.CONTENT_ROOT &&
            EmberkaiIconsSettings.getInstance().colorServiceFolders
        ) {
            IconMappings.resolve(ServiceFolderIcons.iconKey(directory, project))?.let {
                return it.toResolution(
                    precedence = IconPrecedence.PROJECT_ROLE,
                    projectRole = "Service root color",
                    detail = "The project content root is a detected service root.",
                )
            }
        }
        val role = projectRole ?: SourceSetFolders.role(directory)
        if (role != null) return roleResolution(role, "The project layout")

        IconMappings.contextualMatch(directory, "folder.", name)?.let {
            return it.toResolution(
                precedence = IconPrecedence.MAPPING,
                detail = "A path-specific folder mapping matched.",
            )
        }
        val settings = EmberkaiIconsSettings.getInstance()
        for (rule in contextRules) {
            if (!settings.isEnabled(rule.category)) continue
            val key = rule.folderIconKey(directory, project) ?: continue
            val mapping = IconMappings.resolve(key) ?: continue
            return mapping.toResolution(
                precedence = IconPrecedence.CONTEXT_RULE,
                contextRule = rule.category,
                detail = "The ${rule.category.displayName} context rule matched this folder.",
            )
        }
        if (settings.colorServiceFolders) {
            IconMappings.resolve(ServiceFolderIcons.iconKey(directory, project))?.let {
                return it.toResolution(
                    precedence = IconPrecedence.MAPPING,
                    detail = "The folder is a detected service root and received a palette color.",
                )
            }
        }
        IconMappings.resolve("folder.$name")?.let {
            return it.toResolution(IconPrecedence.MAPPING, detail = "A folder-name mapping matched.")
        }
        IconMappings.resolve("folder")?.let {
            return it.toResolution(IconPrecedence.FALLBACK, detail = "No more specific folder rule matched.")
        }
        return IconResolution(
            icon = null,
            precedence = IconPrecedence.IDE_DEFAULT,
            detail = "No Emberkai folder mapping matched.",
        )
    }

    /** [source] names who assigned the role, such as the project root model or the source-set layout. */
    private fun roleResolution(role: ProjectFolderIcons.Role, source: String): IconResolution {
        val detail = "$source assigns the ${role.displayName()} role"
        return IconMappings.resolve(role.iconKey)?.toResolution(
            precedence = IconPrecedence.PROJECT_ROLE,
            projectRole = role.displayName(),
            detail = "$detail.",
        ) ?: IconResolution(
            icon = null,
            mappingKey = role.iconKey,
            projectRole = role.displayName(),
            precedence = IconPrecedence.PROJECT_ROLE,
            detail = "$detail, but its mapping is unavailable.",
        )
    }

    /**
     * Module accents ride on top of the selected Emberkai folder artwork. Service roots already use a
     * full palette color, so they are left alone; content roots are skipped to keep the project root
     * quiet. Everything below a module (source roots, packages, and other folders) gets the small dot.
     */
    private fun applyModuleAccent(
        resolution: IconResolution,
        directory: VirtualFile,
        project: Project?,
    ): IconResolution {
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
        return resolution.copy(
            icon = ModuleAccentIcon(icon, accent.color),
            moduleAccent = accent,
            detail = "${resolution.detail} The module accent follows ${accent.moduleName}.",
        )
    }

    private fun fileIcon(file: VirtualFile, name: String, project: Project?): IconResolution {
        if (NativeIconPolicy.usesFileIcon(file, name)) {
            return IconResolution(
                icon = null,
                precedence = IconPrecedence.NATIVE_POLICY,
                detail = "Native icon policy preserves this source file's IDE language icon.",
            )
        }

        if (project != null && !project.isDisposed) {
            ProjectIconOverrideStore.getInstance(project).fileOverride(file, name)?.let {
                return it.toResolution(
                    precedence = IconPrecedence.MAPPING,
                    detail = "A project icon override matched this file.",
                )
            }
            springXmlResolution(file, project)?.let { return it }
        }

        IconMappings.contextualMatch(file, "file.", name)?.let {
            return it.toResolution(IconPrecedence.MAPPING, detail = "A path-specific file mapping matched.")
        }
        IconMappings.resolve("file.$name")?.let {
            return it.toResolution(IconPrecedence.MAPPING, detail = "A file-name mapping matched.")
        }
        IconMappings.contextualExtensionMatch(file, name)?.let {
            return it.toResolution(IconPrecedence.MAPPING, detail = "A path-specific extension mapping matched.")
        }
        val settings = EmberkaiIconsSettings.getInstance()
        for (rule in contextRules) {
            if (!settings.isEnabled(rule.category)) continue
            val key = rule.fileIconKey(file, name, project) ?: continue
            val mapping = IconMappings.resolve(key) ?: continue
            return mapping.toResolution(
                precedence = IconPrecedence.CONTEXT_RULE,
                contextRule = rule.category,
                detail = "The ${rule.category.displayName} context rule matched this file.",
            )
        }
        IconMappings.extensionMatch(name)?.let {
            return it.toResolution(IconPrecedence.FALLBACK, detail = "No more specific file rule matched.")
        }
        return IconResolution(
            icon = null,
            precedence = IconPrecedence.IDE_DEFAULT,
            detail = "No Emberkai file mapping matched.",
        )
    }

    /** Spring XML is recognized from its DOM description, which wins over XML file-name mappings. */
    private fun springXmlResolution(file: VirtualFile, project: Project): IconResolution? {
        if (!EmberkaiIconsSettings.getInstance().isEnabled(IconRuleCategory.SPRING_BOOT)) return null
        if (DumbService.isDumb(project)) return null
        val detector = SpringXmlConfigDetector.getInstance() ?: return null
        if (!detector.isSpringConfig(file, project)) return null
        return IconMappings.resolve("context.spring")?.toResolution(
            precedence = IconPrecedence.CONTEXT_RULE,
            contextRule = IconRuleCategory.SPRING_BOOT,
            detail = "The Spring XML DOM description selected the Spring icon.",
        )
    }

    private fun MappedIcon.toResolution(
        precedence: IconPrecedence,
        contextRule: IconRuleCategory? = null,
        projectRole: String? = null,
        detail: String,
    ): IconResolution = IconResolution(
        icon = icon,
        mappingKey = key,
        mappingSource = source,
        resourcePath = resourcePath,
        contextRule = contextRule,
        projectRole = projectRole,
        precedence = precedence,
        detail = detail,
    )

    private fun ProjectIconOverride.toResolution(precedence: IconPrecedence, detail: String): IconResolution =
        IconResolution(
            icon = icon,
            mappingKey = key,
            mappingSource = MappingSource.PROJECT_OVERRIDE,
            resourcePath = resourcePath,
            precedence = precedence,
            detail = detail,
        )

    private fun architectureResolution(directory: VirtualFile, project: Project?): IconResolution? {
        if (!EmberkaiIconsSettings.getInstance().isEnabled(architectureRule.category)) return null
        val mapping = IconMappings.resolve(architectureRule.folderIconKey(directory, project)) ?: return null
        return mapping.toResolution(
            precedence = IconPrecedence.CONTEXT_RULE,
            contextRule = architectureRule.category,
            detail = "The ${architectureRule.category.displayName} rule matched a package folder in a Java or Kotlin source root.",
        )
    }

    private fun ProjectFolderIcons.Role.displayName(): String =
        name.lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { it.uppercase(Locale.ROOT) }
}
