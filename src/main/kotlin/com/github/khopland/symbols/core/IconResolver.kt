package com.github.khopland.symbols.core

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
import com.github.khopland.symbols.rules.SpringXmlConfigDetector
import com.github.khopland.symbols.rules.TerraformIconRule
import com.github.khopland.symbols.settings.EmberkaiIconsSettings
import com.github.khopland.symbols.settings.isEnabled
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import javax.swing.Icon

/**
 * Defines icon precedence; add new tool detectors to contextRules in priority order. Badges and
 * markers that sit on top of the chosen icon are [IconDecorator]s registered by their features.
 */
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
        return IconDecorator.EP_NAME.extensionList.fold(resolution) { current, decorator ->
            decorator.decorateFile(current, file, project)
        }
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

    private fun folderIcon(directory: VirtualFile, name: String, project: Project?): IconResolution =
        IconDecorator.EP_NAME.extensionList.fold(baseFolderIcon(directory, name, project)) { current, decorator ->
            decorator.decorateFolder(current, directory, project)
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
