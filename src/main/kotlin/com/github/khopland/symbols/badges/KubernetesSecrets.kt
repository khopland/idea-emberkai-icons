package com.github.khopland.symbols.badges

import com.github.khopland.symbols.core.FileContentCache
import com.github.khopland.symbols.core.StructuredFileKind
import com.github.khopland.symbols.rules.SopsFiles
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.io.StringReader
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode

/** A core `v1` Secret whose [keys] hold values in the file itself; [line] is its `kind` line. */
internal data class PlaintextSecret(val line: Int, val name: String?, val keys: List<String>)

/**
 * Finds Kubernetes Secrets stored in plain text. A Secret's `data` is base64, an encoding anyone can
 * reverse, so a Secret counts when `data` or `stringData` has a value. Values built from Helm
 * templates or `${…}` variables only exist after rendering and are skipped, as are files that SOPS
 * encrypted. Sealed Secrets and External Secrets are other kinds, so they never match.
 */
internal object KubernetesSecrets {
    private val CACHE = Key.create<FileContentCache.Inspection<List<PlaintextSecret>>>("emberkai.plaintextSecrets")
    private val SECRET_RESOURCES = setOf("secret", "bundle")
    private const val MAX_DOCUMENTS = 32

    fun plaintext(file: VirtualFile): List<PlaintextSecret> {
        if (file.isDirectory || StructuredFileKind.of(file) != StructuredFileKind.KUBERNETES) return emptyList()
        if (StructuredFileKind.kubernetesResource(file) !in SECRET_RESOURCES || SopsFiles.isEncrypted(file)) return emptyList()
        return FileContentCache.inspect(file, CACHE, emptyList(), ::plaintext)
    }

    internal fun plaintext(text: String): List<PlaintextSecret> {
        val options = LoaderOptions().apply {
            codePointLimit = FileContentCache.MAX_FILE_SIZE
            nestingDepthLimit = 32
            maxAliasesForCollections = 25
        }
        val found = mutableListOf<PlaintextSecret>()
        try {
            var documents = 0
            for (document in Yaml(SafeConstructor(options)).composeAll(StringReader(text))) {
                if (++documents > MAX_DOCUMENTS) break
                if (document !is MappingNode) continue
                secretIn(document)?.let(found::add)
            }
        } catch (_: YAMLException) {
            // Keep what the readable documents showed; Helm templates usually stop here.
        }
        return found
    }

    private fun secretIn(document: MappingNode): PlaintextSecret? {
        val fields = document.value.associateBy { (it.keyNode as? ScalarNode)?.value }
        val kind = fields["kind"] ?: return null
        if (scalar(kind.valueNode) != "Secret" || scalar(fields["apiVersion"]?.valueNode) != "v1" || "sops" in fields) {
            return null
        }
        val keys = listOf("data", "stringData").flatMap { section ->
            (fields[section]?.valueNode as? MappingNode)?.value.orEmpty().mapNotNull { entry ->
                val key = scalar(entry.keyNode) ?: return@mapNotNull null
                scalar(entry.valueNode)?.takeIf(::isLiteral)?.let { key }
            }
        }
        if (keys.isEmpty()) return null
        val metadata = fields["metadata"]?.valueNode as? MappingNode
        val name = metadata?.value?.firstOrNull { scalar(it.keyNode) == "name" }?.let { scalar(it.valueNode) }
        return PlaintextSecret(kind.keyNode.startMark.line + 1, name, keys.distinct())
    }

    private fun scalar(node: Node?): String? = (node as? ScalarNode)?.value

    private fun isLiteral(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.isNotEmpty() && "{{" !in trimmed && "\${" !in trimmed && !trimmed.startsWith("ENC[")
    }
}
