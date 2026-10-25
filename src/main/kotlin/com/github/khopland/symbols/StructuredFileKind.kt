package com.github.khopland.symbols

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.Tag
import java.io.StringReader
import java.util.Locale
import java.util.regex.Pattern

/** Reads only top-level scalar metadata from YAML/JSON; never constructs tagged objects. */
internal enum class StructuredFileKind {
    NONE, OPEN_API, KUBERNETES, KUSTOMIZE;

    companion object {
        private val CACHE: Key<FileContentCache.Inspection<StructuredFileKind>> =
            Key.create("emberkai.structuredKind")
        private val EXTENSIONS = setOf("yaml", "yml", "json")
        private val OPEN_API_VERSION = Pattern.compile("3\\.\\d+(?:\\.\\d+)?(?:[-+][A-Za-z0-9.-]+)?")
        private val API_VERSION = Pattern.compile("(?:[A-Za-z0-9][A-Za-z0-9.-]*/)?v\\d+(?:(?:alpha|beta)\\d+)?")
        private val KIND = Pattern.compile("[A-Za-z][A-Za-z0-9]*")

        fun of(file: VirtualFile): StructuredFileKind {
            val extension = file.extension
            if (extension == null || extension.lowercase(Locale.ROOT) !in EXTENSIONS) return NONE
            return FileContentCache.inspect(file, CACHE, NONE, ::parse)
        }

        private fun parse(text: String): StructuredFileKind {
            val options = LoaderOptions()
            options.codePointLimit = FileContentCache.MAX_FILE_SIZE
            options.nestingDepthLimit = 32
            options.maxAliasesForCollections = 25
            options.isAllowDuplicateKeys = false
            val yaml = Yaml(SafeConstructor(options))
            try {
                var documents = 0
                for (document in yaml.composeAll(StringReader(text))) {
                    if (++documents > 32) break
                    if (document !is MappingNode || document.tag != Tag.MAP) continue
                    val fields = HashMap<String, String>()
                    val keys = HashSet<String>()
                    for (tuple in document.value) {
                        val key = tuple.keyNode as? ScalarNode ?: continue
                        if (!keys.add(key.value)) return NONE
                        val value = tuple.valueNode as? ScalarNode ?: continue
                        fields[key.value] = value.value
                    }
                    val openApi = fields["openapi"] ?: ""
                    val swagger = fields["swagger"] ?: ""
                    if (OPEN_API_VERSION.matcher(openApi).matches() || swagger == "2.0") return OPEN_API
                    val apiVersion = fields["apiVersion"] ?: ""
                    val kind = fields["kind"] ?: ""
                    if (API_VERSION.matcher(apiVersion).matches() && KIND.matcher(kind).matches()) {
                        return if (kind == "Kustomization" && apiVersion.startsWith("kustomize.config.k8s.io/")) {
                            KUSTOMIZE
                        } else {
                            KUBERNETES
                        }
                    }
                }
            } catch (_: YAMLException) {
                // Incomplete or unsupported documents retain their format icon.
            }
            return NONE
        }
    }
}
