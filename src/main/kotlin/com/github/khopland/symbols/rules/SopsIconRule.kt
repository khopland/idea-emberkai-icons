package com.github.khopland.symbols.rules

import com.github.khopland.symbols.core.FileContentCache
import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.core.StructuredFileKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/**
 * Recognizes SOPS configuration and files SOPS encrypted, such as Helm secrets or encrypted dotenv
 * files. Encrypted Kubernetes manifests stay Kubernetes manifests: [KubernetesIconRule] gives them
 * an encrypted kind badge instead, so they keep their place in the Kubernetes scope.
 */
internal class SopsIconRule : ContextIconRule {
    override val category = IconRuleCategory.SOPS

    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? {
        if (name in SopsFiles.CONFIG_NAMES) return "context.sops"
        if (!SopsFiles.isEncrypted(file, project) || StructuredFileKind.of(file, project) == StructuredFileKind.KUBERNETES) return null
        return "context.sops"
    }
}

/**
 * Files that SOPS encrypted: YAML and JSON carry a top-level `sops` entry with the key metadata,
 * dotenv and INI files a `sops_mac` line, and every encrypted value reads `ENC[…]`.
 */
internal object SopsFiles {
    private val CACHE = Key.create<FileContentCache.Inspection<Boolean>>("emberkai.sopsEncrypted")
    private val EXTENSIONS = setOf("yaml", "yml", "json", "env", "ini")
    private val METADATA = Regex("""(?m)^(?:sops:\s*$|sops_mac\s*=)|"sops"\s*:\s*\{""")
    private const val ENCRYPTED_VALUE = "ENC[AES256_GCM,"
    val CONFIG_NAMES = setOf(".sops.yaml", ".sops.yml")

    /** With a [project], background callers read the answer from [ContentIndex] instead of the file. */
    fun isEncrypted(file: VirtualFile, project: Project? = null): Boolean {
        if (!appliesTo(file)) return false
        ContentIndex.facts(file, project)?.let { return it.sopsEncrypted }
        return FileContentCache.inspect(file, CACHE, false, ::isEncrypted)
    }

    /** Whether [file]'s name allows SOPS encryption; the contents decide. */
    fun appliesTo(file: VirtualFile): Boolean {
        if (file.isDirectory) return false
        val name = file.name.lowercase(Locale.ROOT)
        if (name in CONFIG_NAMES) return false
        return file.extension?.lowercase(Locale.ROOT) in EXTENSIONS || name.startsWith(".env")
    }

    internal fun isEncrypted(text: String): Boolean = ENCRYPTED_VALUE in text && METADATA.containsMatchIn(text)
}
