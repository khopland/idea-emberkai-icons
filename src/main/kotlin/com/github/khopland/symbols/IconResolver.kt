package com.github.khopland.symbols

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/** Defines icon precedence; add new tool detectors to contextRules in priority order. */
internal class IconResolver {
    private val mappings = IconMappings()
    private val projectFolders = ProjectFolderIcons()
    private val sourceSets = SourceSetFolders()
    private val serviceFolders = ServiceFolderIcons(mappings)
    private val architectureRule = JvmArchitectureFolderIconRule()
    private val contextRules = listOf(
        CiConfigIconRule(),
        LiquibaseIconRule(),
        HelmIconRule(),
        AnsibleIconRule(),
        InfrastructureIconRule(),
        GitOpsIconRule(),
        MigrationIconRule(),
        ApiSpecificationIconRule(),
        KubernetesIconRule(),
        SpringBootIconRule(),
        architectureRule,
    )

    /** Keep the long-standing API for callers that only need the selected icon. */
    fun resolve(file: VirtualFile, project: Project?): javax.swing.Icon? =
        resolveWithDetails(file, project).icon

    fun resolveWithDetails(file: VirtualFile, project: Project?): IconResolution {
        if (!file.isDirectory && project != null && !project.isDisposed &&
            EmberkaiIconsSettings.getInstance().isEnabled(IconRuleCategory.SPRING_BOOT) &&
            !DumbService.isDumb(project)
        ) {
            val springIcon = SpringDomIconSupport.iconFor(file, project)
            if (springIcon != null) {
                val mapping = mappings.resolve("context.spring")
                return IconResolution(
                    icon = springIcon,
                    mappingKey = mapping?.key,
                    mappingSource = mapping?.source,
                    resourcePath = mapping?.resourcePath,
                    contextRule = SPRING_XML_RULE,
                    precedence = IconPrecedence.CONTEXT_RULE,
                    detail = "The Spring XML DOM description selected the Spring icon.",
                )
            }
        }

        val name = file.name.lowercase(Locale.ROOT)
        val resolution = if (file.isDirectory) folderIcon(file, name, project) else fileIcon(file, name, project)
        if (file.isDirectory || resolution.precedence == IconPrecedence.NATIVE_POLICY) return resolution
        return applyActiveProfile(applySecretBadge(applyEnvironmentBadge(resolution, file), file, project), name, project)
    }

    private fun applyEnvironmentBadge(resolution: IconResolution, file: VirtualFile): IconResolution {
        if (!EmberkaiIconsSettings.getInstance().isEnabled(IconRuleCategory.ENVIRONMENT_VARIANTS)) return resolution
        val variant = EnvironmentVariant.fromFile(file) ?: return resolution
        val baseIcon = resolution.icon ?: FileTypeManager.getInstance().getFileTypeByFile(file).icon ?: return resolution
        return resolution.copy(
            icon = EnvironmentVariantBadgeIcon(baseIcon, variant),
            environmentVariant = variant,
            detail = "${resolution.detail} A ${variant.displayName} environment badge marks this configuration variant.",
        )
    }

    /** Only files the Spring Boot rule recognized can carry the live marker; the lookup is a set test. */
    private fun applyActiveProfile(resolution: IconResolution, name: String, project: Project?): IconResolution {
        if (resolution.contextRule != IconRuleCategory.SPRING_BOOT.displayName) return resolution
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

    /**
     * The first context rule that recognizes [file], in icon precedence order. Category toggles are
     * ignored: they only choose artwork, while scopes describe what a file is.
     */
    fun recognizedCategory(file: VirtualFile, project: Project?): IconRuleCategory? {
        if (file.isDirectory) return null
        val name = file.name.lowercase(Locale.ROOT)
        return contextRules.firstOrNull { it.recognizesFile(file, name, project) }?.category
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
        applyModuleAccent(baseFolderIcon(directory, name, project), directory, project)

    private fun baseFolderIcon(directory: VirtualFile, name: String, project: Project?): IconResolution {
        if (NativeIconPolicy.usesFolderIcon(directory, name, project)) {
            return IconResolution(
                icon = null,
                precedence = IconPrecedence.NATIVE_POLICY,
                detail = "Native icon policy protects this folder from Emberkai mappings.",
            )
        }

        val projectRole = projectFolders.role(directory, project)
        if (projectRole == ProjectFolderIcons.Role.NATIVE) {
            return IconResolution(
                icon = null,
                projectRole = projectRole.displayName(),
                precedence = IconPrecedence.PROJECT_ROLE,
                detail = "The project root model leaves this folder to its native language or generated-source icon.",
            )
        }
        if (projectRole == ProjectFolderIcons.Role.PACKAGE ||
            projectRole == ProjectFolderIcons.Role.TEST_PACKAGE
        ) {
            architectureResolution(directory, project)?.let { return it }
        }
        if (projectRole != null && projectRole != ProjectFolderIcons.Role.CONTENT_ROOT) {
            val mapping = mappings.resolve(projectRole.iconKey)
            return mapping?.toResolution(
                precedence = IconPrecedence.PROJECT_ROLE,
                projectRole = projectRole.displayName(),
                detail = "The project root model assigns the ${projectRole.displayName()} role.",
            ) ?: IconResolution(
                icon = null,
                mappingKey = projectRole.iconKey,
                projectRole = projectRole.displayName(),
                precedence = IconPrecedence.PROJECT_ROLE,
                detail = "The project root model assigns the ${projectRole.displayName()} role, but its mapping is unavailable.",
            )
        }
        if (projectRole == ProjectFolderIcons.Role.CONTENT_ROOT &&
            EmberkaiIconsSettings.getInstance().colorServiceFolders
        ) {
            val serviceKey = serviceFolders.iconKey(directory, project)
            val mapping = mappings.resolve(serviceKey)
            if (mapping != null) {
                return mapping.toResolution(
                    precedence = IconPrecedence.PROJECT_ROLE,
                    projectRole = "Service root color",
                    detail = "The project content root is a detected service root.",
                )
            }
        }
        val role = projectRole ?: sourceSets.role(directory)
        if (role != null) {
            val mapping = mappings.resolve(role.iconKey)
            return mapping?.toResolution(
                precedence = IconPrecedence.PROJECT_ROLE,
                projectRole = role.displayName(),
                detail = "The project layout assigns the ${role.displayName()} role.",
            ) ?: IconResolution(
                icon = null,
                mappingKey = role.iconKey,
                projectRole = role.displayName(),
                precedence = IconPrecedence.PROJECT_ROLE,
                detail = "The project layout assigns the ${role.displayName()} role, but its mapping is unavailable.",
            )
        }

        mappings.contextualMatch(directory, "folder.", name)?.let {
            return it.toResolution(
                precedence = IconPrecedence.MAPPING,
                detail = "A path-specific folder mapping matched.",
            )
        }
        val settings = EmberkaiIconsSettings.getInstance()
        for (rule in contextRules) {
            if (!settings.isEnabled(rule.category)) continue
            val key = rule.folderIconKey(directory, project) ?: continue
            val mapping = mappings.resolve(key) ?: continue
            return mapping.toResolution(
                precedence = IconPrecedence.CONTEXT_RULE,
                contextRule = rule.category.displayName,
                detail = "The ${rule.category.displayName} context rule matched this folder.",
            )
        }
        if (settings.colorServiceFolders) {
            val mapping = mappings.resolve(serviceFolders.iconKey(directory, project))
            if (mapping != null) {
                return mapping.toResolution(
                    precedence = IconPrecedence.MAPPING,
                    detail = "The folder is a detected service root and received a palette color.",
                )
            }
        }
        mappings.resolve("folder.$name")?.let {
            return it.toResolution(IconPrecedence.MAPPING, detail = "A folder-name mapping matched.")
        }
        mappings.resolve("folder")?.let {
            return it.toResolution(IconPrecedence.FALLBACK, detail = "No more specific folder rule matched.")
        }
        return IconResolution(
            icon = null,
            precedence = IconPrecedence.IDE_DEFAULT,
            detail = "No Emberkai folder mapping matched.",
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

        mappings.contextualMatch(file, "file.", name)?.let {
            return it.toResolution(IconPrecedence.MAPPING, detail = "A path-specific file mapping matched.")
        }
        mappings.resolve("file.$name")?.let {
            return it.toResolution(IconPrecedence.MAPPING, detail = "A file-name mapping matched.")
        }
        mappings.contextualExtensionMatch(file, name)?.let {
            return it.toResolution(IconPrecedence.MAPPING, detail = "A path-specific extension mapping matched.")
        }
        val settings = EmberkaiIconsSettings.getInstance()
        for (rule in contextRules) {
            if (!settings.isEnabled(rule.category)) continue
            val key = rule.fileIconKey(file, name, project) ?: continue
            val mapping = mappings.resolve(key) ?: continue
            return mapping.toResolution(
                precedence = IconPrecedence.CONTEXT_RULE,
                contextRule = rule.category.displayName,
                detail = "The ${rule.category.displayName} context rule matched this file.",
            )
        }
        mappings.extensionMatch(name)?.let {
            return it.toResolution(IconPrecedence.FALLBACK, detail = "No more specific file rule matched.")
        }
        return IconResolution(
            icon = null,
            precedence = IconPrecedence.IDE_DEFAULT,
            detail = "No Emberkai file mapping matched.",
        )
    }

    private fun MappedIcon.toResolution(
        precedence: IconPrecedence,
        contextRule: String? = null,
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

    private fun architectureResolution(directory: VirtualFile, project: Project?): IconResolution? {
        if (!EmberkaiIconsSettings.getInstance().isEnabled(architectureRule.category)) return null
        val mapping = mappings.resolve(architectureRule.folderIconKey(directory, project)) ?: return null
        return mapping.toResolution(
            precedence = IconPrecedence.CONTEXT_RULE,
            contextRule = architectureRule.category.displayName,
            detail = "The ${architectureRule.category.displayName} rule matched a package folder in a Java or Kotlin source root.",
        )
    }

    private fun ProjectFolderIcons.Role.displayName(): String =
        name.lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { it.uppercase(Locale.ROOT) }

    companion object {
        const val SPRING_XML_RULE = "Spring XML DOM description"
    }
}
