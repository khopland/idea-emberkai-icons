package com.github.khopland.symbols

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import java.util.ArrayDeque
import java.util.Locale

internal enum class CoverageFindingKind(val displayName: String) {
    GENERIC_ICON("Generic format icon"),
    NO_MAPPING("No Emberkai mapping"),
}

internal data class CoverageAuditFinding(
    val file: VirtualFile,
    val relativePath: String,
    val kind: CoverageFindingKind,
    val extension: String?,
    val parentDirectory: String,
    val mappingKey: String?,
    val mappingSource: MappingSource?,
    val resourcePath: String?,
) {
    override fun toString(): String = relativePath.substringAfterLast('/').ifEmpty { file.name }

    val suggestedMappingKey: String
        get() {
            val fileExtension = extension
            val relativeParent = parentDirectory.takeUnless { it == "." }.orEmpty().lowercase(Locale.ROOT)
            return if (fileExtension != null) {
                "extension.${if (relativeParent.isEmpty()) "" else "$relativeParent/"}$fileExtension"
            } else {
                "file.${relativePath.lowercase(Locale.ROOT)}"
            }
        }

    val suggestedMapping: String
        get() {
            val slug = file.name.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9._-]+"), "-")
                .trim('-', '.')
                .ifEmpty { "custom" }
            return "${escapePropertyKey(suggestedMappingKey)}=/emberkai/icons/$slug-icon.svg"
        }

    private fun escapePropertyKey(key: String): String = key
        .replace("\\", "\\\\")
        .replace(" ", "\\ ")
        .replace("=", "\\=")
        .replace(":", "\\:")
        .replace("#", "\\#")
        .replace("!", "\\!")
}

internal data class CoverageAuditGroup(
    val label: String,
    val count: Int,
    val findings: List<CoverageAuditFinding>,
)

internal data class CoverageAuditResult(
    val root: VirtualFile,
    val scannedFiles: Int,
    val totalFindings: Int,
    val omittedFindings: Int,
    val groups: List<CoverageAuditGroup>,
)

/** Walks one content subtree and evaluates files through the production resolver. */
internal object IconCoverageAudit {
    private const val MAX_RETAINED_FINDINGS = 5_000
    private val genericIconNames = setOf("code-gray.svg", "document.svg", "json.svg", "text.svg", "xml.svg", "yaml.svg")
    private val skippedDirectoryNames = setOf(
        ".cache", ".git", ".gradle", ".idea", ".intellijplatform", ".kotlin", ".venv",
        "build", "dist", "node_modules", "out", "pods", "target", "vendor",
    )

    private data class GroupKey(
        val kind: CoverageFindingKind,
        val extension: String?,
        val parentDirectory: String,
    )

    private class MutableGroup {
        var count = 0
        val findings = mutableListOf<CoverageAuditFinding>()
    }

    fun run(
        project: Project,
        root: VirtualFile,
        indicator: ProgressIndicator,
        wholeProject: Boolean = false,
    ): CoverageAuditResult {
        val fileIndex = ProjectFileIndex.getInstance(project)
        val resolver = IconResolver()
        val pendingDirectories = ArrayDeque<VirtualFile>()
        val visitedDirectories = hashSetOf<String>()
        val groups = linkedMapOf<GroupKey, MutableGroup>()
        val scanRoots = if (wholeProject) {
            computeReadAction<List<VirtualFile>> {
                buildList {
                    add(root)
                    ModuleManager.getInstance(project).modules.forEach { module ->
                        addAll(ModuleRootManager.getInstance(module).contentRoots)
                    }
                }.distinctBy { it.canonicalPath ?: it.path }
            }
        } else {
            listOf(root)
        }
        scanRoots.forEach(pendingDirectories::addLast)
        var scannedFiles = 0
        var totalFindings = 0
        var retainedFindings = 0

        indicator.isIndeterminate = true
        indicator.text = "Scanning ${root.path}"
        while (pendingDirectories.isNotEmpty()) {
            indicator.checkCanceled()
            val directory = pendingDirectories.removeLast()
            val canonicalPath = directory.canonicalPath ?: directory.path
            if (!visitedDirectories.add(canonicalPath)) continue
            if (directory != root && (
                    directory.name.lowercase(Locale.ROOT) in skippedDirectoryNames || !isProjectContent(fileIndex, directory)
                )
            ) continue

            val children = directory.children
            for (child in children.reversed()) {
                indicator.checkCanceled()
                if (child.isDirectory) {
                    pendingDirectories.addLast(child)
                    continue
                }
                if (!isProjectContent(fileIndex, child)) continue
                scannedFiles++
                if (scannedFiles % 128 == 0) {
                    indicator.text2 = "$scannedFiles files scanned · $totalFindings candidates"
                }

                val resolution = computeReadAction<IconResolution> {
                    resolver.resolveWithDetails(child, project)
                }
                val kind = findingKind(resolution) ?: continue
                val path = ProjectPathUtil.relativePath(child, project) ?: child.path
                val relativeParent = path.substringBeforeLast('/', missingDelimiterValue = ".").ifEmpty { "." }
                val extension = child.extension?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
                val key = GroupKey(kind, extension, relativeParent)
                val group = groups.getOrPut(key) { MutableGroup() }
                group.count++
                totalFindings++
                if (retainedFindings < MAX_RETAINED_FINDINGS) {
                    group.findings += CoverageAuditFinding(
                        file = child,
                        relativePath = path,
                        kind = kind,
                        extension = extension,
                        parentDirectory = relativeParent,
                        mappingKey = resolution.mappingKey,
                        mappingSource = resolution.mappingSource,
                        resourcePath = resolution.resourcePath,
                    )
                    retainedFindings++
                }
            }
        }

        val resultGroups = groups.entries
            .sortedWith(compareBy({ it.key.kind.ordinal }, { it.key.extension.orEmpty() }, { it.key.parentDirectory }))
            .map { (key, group) ->
                val extensionLabel = key.extension?.let { ".$it" } ?: "extensionless files"
                val typeLabel = when (key.kind) {
                    CoverageFindingKind.GENERIC_ICON -> "Generic $extensionLabel"
                    CoverageFindingKind.NO_MAPPING -> "Unmapped $extensionLabel"
                }
                val directoryLabel = key.parentDirectory.takeUnless { it == "." } ?: "project root"
                CoverageAuditGroup(
                    label = "$typeLabel in $directoryLabel (${group.count})",
                    count = group.count,
                    findings = group.findings.sortedBy { it.relativePath.lowercase(Locale.ROOT) },
                )
            }
        return CoverageAuditResult(
            root = root,
            scannedFiles = scannedFiles,
            totalFindings = totalFindings,
            omittedFindings = (totalFindings - retainedFindings).coerceAtLeast(0),
            groups = resultGroups,
        )
    }

    private fun isProjectContent(fileIndex: ProjectFileIndex, file: VirtualFile): Boolean =
        computeReadAction<Boolean> {
            fileIndex.isInContent(file) && !fileIndex.isExcluded(file) && !fileIndex.isInLibrary(file)
        }

    private fun findingKind(resolution: IconResolution): CoverageFindingKind? = when {
        resolution.precedence == IconPrecedence.IDE_DEFAULT -> CoverageFindingKind.NO_MAPPING
        resolution.resourcePath?.substringAfterLast('/') in genericIconNames -> CoverageFindingKind.GENERIC_ICON
        else -> null
    }
}
