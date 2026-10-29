package com.github.khopland.symbols.rules

import com.github.khopland.symbols.core.FileContentCache
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.folders.ProjectFolderIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import java.util.regex.Pattern

/** Recognizes conventional architecture packages inside imported Java and Kotlin source roots. */
internal class JvmArchitectureFolderIconRule : ContextIconRule {
    override val category = IconRuleCategory.ARCHITECTURE

    override fun folderIconKey(directory: VirtualFile, project: Project?): String? {
        val icon = ICONS[directory.name.lowercase(Locale.ROOT)] ?: return null
        if (!isJavaOrKotlinPackage(directory, project)) return null
        if (icon == "config" && !hasEvidence(directory, CONFIG_FILE, CONFIG_ANNOTATION, CONFIG_CONTENT)) return null
        if (icon == "client" && !hasEvidence(directory, CLIENT_FILE, CLIENT_ANNOTATION, CLIENT_CONTENT)) return null
        return "context.architecture.$icon"
    }

    private fun isJavaOrKotlinPackage(directory: VirtualFile, project: Project?): Boolean {
        if (project == null || project.isDisposed) return false
        val sourceRoot = ProjectFileIndex.getInstance(project).getSourceRootForFile(directory) ?: return false
        return when (ProjectFolderIcons.role(sourceRoot, project)) {
            ProjectFolderIcons.Role.SOURCE, ProjectFolderIcons.Role.TEST_SOURCE -> true
            else -> false
        }
    }

    /** Read only a bounded set of immediate JVM sources; FileContentCache tracks edits and stamps. */
    private fun hasEvidence(
        directory: VirtualFile,
        filename: Pattern,
        annotation: Pattern,
        cacheKey: Key<FileContentCache.Inspection<Boolean>>,
    ): Boolean {
        var inspectedSources = 0
        for (child in directory.children.take(MAX_CHILDREN)) {
            if (child.isDirectory || child.extension?.lowercase(Locale.ROOT) !in JVM_EXTENSIONS) continue
            if (filename.matcher(child.name).matches()) return true
            if (inspectedSources >= MAX_SOURCE_CONTENT_CHECKS) continue
            inspectedSources++
            val hasAnnotation = FileContentCache.inspect(child, cacheKey, false) { text ->
                annotation.matcher(text).find()
            }
            if (hasAnnotation) return true
        }
        return false
    }

    private companion object {
        const val MAX_CHILDREN = 32
        const val MAX_SOURCE_CONTENT_CHECKS = 8
        val JVM_EXTENSIONS = setOf("java", "kt")
        val ICONS = mapOf(
            "controller" to "controller",
            "controllers" to "controller",
            "service" to "service",
            "services" to "service",
            "repository" to "repository",
            "repositories" to "repository",
            "domain" to "domain",
            "domains" to "domain",
            "dto" to "dto",
            "dtos" to "dto",
            "mapper" to "mapper",
            "mappers" to "mapper",
            "config" to "config",
            "configs" to "config",
            "configuration" to "config",
            "configurations" to "config",
            "security" to "security",
            "client" to "client",
            "clients" to "client",
            "exception" to "exception",
            "exceptions" to "exception",
            "util" to "util",
            "utils" to "util",
            "utility" to "util",
            "utilities" to "util",
        )
        val CONFIG_FILE = Pattern.compile("(?i).*(?:config|configuration|properties)\\.(?:java|kt)")
        val CLIENT_FILE = Pattern.compile("(?i).*(?:client|api)\\.(?:java|kt)")
        val CONFIG_ANNOTATION = Pattern.compile(
            "@(?:[\\w$]+\\.)*(?:Configuration|ConfigurationProperties|EnableConfigurationProperties|PropertySource)\\b",
        )
        val CLIENT_ANNOTATION = Pattern.compile(
            "@(?:[\\w$]+\\.)*(?:FeignClient|HttpExchange|RegisterRestClient|RestClient)\\b",
        )
        val CONFIG_CONTENT = Key.create<FileContentCache.Inspection<Boolean>>("emberkai.architectureConfig")
        val CLIENT_CONTENT = Key.create<FileContentCache.Inspection<Boolean>>("emberkai.architectureClient")
    }
}
