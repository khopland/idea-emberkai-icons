package com.github.khopland.symbols.core

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.io.StringReader
import java.util.Locale
import java.util.regex.Pattern
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.Tag

/** Reads only top-level scalar metadata from YAML/JSON; never constructs tagged objects. */
internal enum class StructuredFileKind {
    NONE, OPEN_API, KUBERNETES, KUSTOMIZE, FLUX, ARGOCD;

    companion object {
        private val CACHE: Key<FileContentCache.Inspection<Metadata>> = Key.create("emberkai.structuredKind")
        private val UNKNOWN = Metadata(NONE, null)
        private val EXTENSIONS = setOf("yaml", "yml", "json")
        private val OPEN_API_VERSION = Pattern.compile("3\\.\\d+(?:\\.\\d+)?(?:[-+][A-Za-z0-9.-]+)?")
        private val API_VERSION = Pattern.compile("(?:[A-Za-z0-9][A-Za-z0-9.-]*/)?v\\d+(?:(?:alpha|beta)\\d+)?")
        private val KIND = Pattern.compile("[A-Za-z][A-Za-z0-9]*")
        private val ARGOCD_KINDS = setOf("Application", "ApplicationSet", "AppProject")

        /** Resource kinds with dedicated artwork, keyed by kind and accepted API groups ("" is core). */
        private val RESOURCES = mapOf(
            "Deployment" to setOf("apps", "extensions"),
            "StatefulSet" to setOf("apps"),
            "DaemonSet" to setOf("apps", "extensions"),
            "Service" to setOf(""),
            "Ingress" to setOf("networking.k8s.io", "extensions"),
            "ConfigMap" to setOf(""),
            "Secret" to setOf(""),
            "Job" to setOf("batch"),
            "CronJob" to setOf("batch"),
            "CustomResourceDefinition" to setOf("apiextensions.k8s.io"),
            "PersistentVolumeClaim" to setOf(""),
            "Namespace" to setOf(""),
        )
        private const val BUNDLE = "bundle"

        fun of(file: VirtualFile): StructuredFileKind = metadata(file).kind

        /**
         * Lowercase resource kind for [KUBERNETES] files with dedicated artwork, `bundle` when
         * documents declare different kinds, or null for the generic Kubernetes icon.
         */
        fun kubernetesResource(file: VirtualFile): String? = metadata(file).resource

        private fun metadata(file: VirtualFile): Metadata {
            val extension = file.extension
            if (extension == null || extension.lowercase(Locale.ROOT) !in EXTENSIONS) return UNKNOWN
            return FileContentCache.inspect(file, CACHE, UNKNOWN, ::parse)
        }

        /** Names a document's resource; kinds without artwork keep a `~` prefix so they still compare. */
        private fun resourceOf(apiVersion: String, kind: String): String {
            val group = if ('/' in apiVersion) apiVersion.substringBefore('/') else ""
            return if (RESOURCES[kind]?.contains(group) == true) kind.lowercase(Locale.ROOT) else "~$kind"
        }

        private fun parse(text: String): Metadata {
            val options = LoaderOptions()
            options.codePointLimit = FileContentCache.MAX_FILE_SIZE
            options.nestingDepthLimit = 32
            options.maxAliasesForCollections = 25
            options.isAllowDuplicateKeys = false
            val yaml = Yaml(SafeConstructor(options))
            // The first classified document decides the kind; later documents only refine Kubernetes artwork.
            var resource: String? = null
            try {
                var documents = 0
                for (document in yaml.composeAll(StringReader(text))) {
                    if (++documents > 32) break
                    if (document !is MappingNode || document.tag != Tag.MAP) continue
                    val fields = HashMap<String, String>()
                    val keys = HashSet<String>()
                    for (tuple in document.value) {
                        val key = tuple.keyNode as? ScalarNode ?: continue
                        if (!keys.add(key.value)) return resource?.let { Metadata(KUBERNETES, null) } ?: UNKNOWN
                        val value = tuple.valueNode as? ScalarNode ?: continue
                        fields[key.value] = value.value
                    }
                    val apiVersion = fields["apiVersion"] ?: ""
                    val kind = fields["kind"] ?: ""
                    val isResource = API_VERSION.matcher(apiVersion).matches() && KIND.matcher(kind).matches()
                    if (resource != null) {
                        if (isResource && resourceOf(apiVersion, kind) != resource) {
                            resource = BUNDLE
                            break
                        }
                        continue
                    }
                    val openApi = fields["openapi"] ?: ""
                    val swagger = fields["swagger"] ?: ""
                    if (OPEN_API_VERSION.matcher(openApi).matches() || swagger == "2.0") return Metadata(OPEN_API, null)
                    if (apiVersion.startsWith("argoproj.io/") && kind in ARGOCD_KINDS) return Metadata(ARGOCD, null)
                    if (apiVersion.substringBefore('/').endsWith(".toolkit.fluxcd.io")) return Metadata(FLUX, null)
                    if (isResource) {
                        if (kind == "Kustomization" && apiVersion.startsWith("kustomize.config.k8s.io/")) {
                            return Metadata(KUSTOMIZE, null)
                        }
                        resource = resourceOf(apiVersion, kind)
                    }
                }
            } catch (_: YAMLException) {
                // Incomplete documents retain their format icon, or the generic icon after a Kubernetes match.
                return resource?.let { Metadata(KUBERNETES, null) } ?: UNKNOWN
            }
            return resource?.let { Metadata(KUBERNETES, it.takeUnless { it.startsWith('~') }) } ?: UNKNOWN
        }
    }

    private class Metadata(val kind: StructuredFileKind, val resource: String?)
}
