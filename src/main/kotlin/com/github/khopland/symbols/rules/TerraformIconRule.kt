package com.github.khopland.symbols.rules

import com.github.khopland.symbols.core.FileContentCache
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.ProjectPathUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Tells OpenTofu configuration apart from Terraform. `.tofu` files are always OpenTofu; `.tf`,
 * `.tfvars`, and the lock file are OpenTofu when their folder or an ancestor up to the project base
 * shows OpenTofu: an `.opentofu-version` file, a sibling `.tofu` file, a lock file that pins
 * providers from `registry.opentofu.org`, or an `opentofu` entry in `.tool-versions` or mise
 * configuration. Everything else keeps the Terraform artwork from the static mappings.
 */
internal class TerraformIconRule : ContextIconRule {
    override val category = IconRuleCategory.INFRASTRUCTURE

    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? {
        if (TOFU_EXTENSIONS.any { name.endsWith(it) }) return OPENTOFU
        if (TERRAFORM_EXTENSIONS.none { name.endsWith(it) } && name != LOCK_FILE) return null
        if (usesOpenTofu(file.parent, project)) return OPENTOFU
        return if (name == LOCK_FILE) TERRAFORM else null
    }

    override fun folderIconKey(directory: VirtualFile, project: Project?): String? {
        val name = directory.name.lowercase(Locale.ROOT)
        if (name == "tofu" || name == "opentofu") return OPENTOFU_FOLDER
        if (name in TERRAFORM_FOLDERS && usesOpenTofu(directory, project)) return OPENTOFU_FOLDER
        return null
    }

    private fun usesOpenTofu(start: VirtualFile?, project: Project?): Boolean {
        var directory = start
        var depth = 0
        while (directory != null && depth++ < MAX_DEPTH) {
            if (OpenTofuSignals.isPresent(directory)) return true
            if (directory.findChild(".git") != null) return false
            if (project != null && ProjectPathUtil.isProjectBase(directory, project)) return false
            directory = directory.parent
        }
        return false
    }

    private companion object {
        const val OPENTOFU = "context.opentofu"
        const val OPENTOFU_FOLDER = "context.opentofu-folder"
        const val TERRAFORM = "context.terraform"
        const val LOCK_FILE = ".terraform.lock.hcl"
        const val MAX_DEPTH = 8
        val TOFU_EXTENSIONS = listOf(".tofu", ".tofu.json")
        val TERRAFORM_EXTENSIONS = listOf(".tf", ".tf.json", ".tfvars", ".tfvars.json")
        val TERRAFORM_FOLDERS = setOf("terraform", "terragrunt", "infra", "infrastructure")
    }
}

/**
 * Whether one folder shows OpenTofu, cached per folder. The cache is dropped when files are added,
 * removed, or renamed, or when a signal file such as the lock file is saved.
 */
internal object OpenTofuSignals {
    private val cache = ConcurrentHashMap<VirtualFile, Boolean>()
    private val LOCK_REGISTRY: Key<FileContentCache.Inspection<Boolean>> = Key.create("emberkai.openTofuLock")
    private val TOOL_VERSIONS: Key<FileContentCache.Inspection<Boolean>> = Key.create("emberkai.openTofuToolVersions")
    private val MISE: Key<FileContentCache.Inspection<Boolean>> = Key.create("emberkai.openTofuMise")
    private val TOOL_VERSIONS_ENTRY: Pattern = Pattern.compile("(?m)^\\s*opentofu\\s")
    private val MISE_ENTRY: Pattern = Pattern.compile("(?m)^\\s*[\"']?opentofu[\"']?\\s*=")
    internal val SIGNAL_FILES = setOf(".opentofu-version", ".terraform.lock.hcl", ".tool-versions", "mise.toml", ".mise.toml")

    fun isPresent(directory: VirtualFile): Boolean {
        if (!directory.isValid || !directory.isDirectory) return false
        return cache.computeIfAbsent(directory, ::scan)
    }

    fun invalidate() = cache.clear()

    private fun scan(directory: VirtualFile): Boolean {
        if (directory.findChild(".opentofu-version") != null) return true
        if (directory.children.orEmpty().any { !it.isDirectory && (it.name.endsWith(".tofu") || it.name.endsWith(".tofu.json")) }) {
            return true
        }
        if (matches(directory.findChild(".terraform.lock.hcl"), LOCK_REGISTRY) { "registry.opentofu.org/" in it }) return true
        if (matches(directory.findChild(".tool-versions"), TOOL_VERSIONS) { TOOL_VERSIONS_ENTRY.matcher(it).find() }) return true
        return listOf("mise.toml", ".mise.toml").any { name ->
            matches(directory.findChild(name), MISE) { MISE_ENTRY.matcher(it).find() }
        }
    }

    private fun matches(
        file: VirtualFile?,
        key: Key<FileContentCache.Inspection<Boolean>>,
        test: (String) -> Boolean,
    ): Boolean = FileContentCache.inspect(file, key, false, test)
}

/** Clears [OpenTofuSignals] after structural VFS changes and saved edits to its signal files. */
internal class OpenTofuSignalCacheListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        if (events.any { it !is VFileContentChangeEvent || it.file.name in OpenTofuSignals.SIGNAL_FILES }) {
            OpenTofuSignals.invalidate()
        }
    }
}
