package com.github.khopland.symbols.badges

import com.github.khopland.symbols.core.FileContentCache
import com.github.khopland.symbols.core.RefreshProjectIconPresentations
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.io.IOException
import java.io.StringReader
import java.util.Locale
import java.util.Properties
import javax.swing.Icon
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode

/** Keys a config variant lacks, each with the sibling variants that define it. */
internal data class ConfigDrift(val missing: Map<String, List<String>>)

/**
 * Compares sibling config variants: Spring `application-<profile>` and `bootstrap-<profile>` files
 * in YAML or properties, Helm `values-<env>.yaml`, and `.env.<variant>`. A variant drifts when it
 * lacks a key that every other variant in its folder and family defines, the usual result of adding
 * a setting to all environments but one. Developer-only variants such as `local` and `test`, and
 * the base files without a variant, take no part. Spring keys are compared the way relaxed binding
 * reads them, ignoring case, dashes, and underscores.
 */
internal object ConfigVariants {
    private val KEYS = Key.create<FileContentCache.Inspection<Set<String>?>>("emberkai.configKeys")
    private val SPRING = Regex("""^(application|bootstrap)-([^.]+)\.(ya?ml|properties)$""")
    private val HELM = Regex("""^values[-.]([^.]+)\.ya?ml$""")
    private val DOTENV = Regex("""^\.env\.(.+)$""")
    private val DOTENV_KEY = Regex("""^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_.]*)\s*=""")
    private val IGNORED_VARIANTS = setOf("local", "test", "tests", "vault", "keys", "me")
    private const val MAX_SIBLINGS = 12

    private enum class Format { YAML, PROPERTIES, DOTENV }

    private data class Variant(val family: String, val variant: String, val format: Format)

    /** What [file] lacks compared with its sibling variants, or null when nothing is missing. */
    fun of(file: VirtualFile): ConfigDrift? {
        if (file.isDirectory) return null
        val self = variant(file.name) ?: return null
        val siblings = file.parent?.children.orEmpty()
            .filter { it != file && !it.isDirectory && variant(it.name)?.family == self.family }
            .take(MAX_SIBLINGS)
        if (siblings.isEmpty()) return null
        val own = keys(file, self) ?: return null
        val others = siblings.associate { sibling -> sibling.name to (keys(sibling, variant(sibling.name)!!) ?: return null) }
        return compare(own, others)
    }

    /** Keys every one of [others] defines and [own] lacks, mapped to the files that define them. */
    fun compare(own: Set<String>, others: Map<String, Set<String>>): ConfigDrift? {
        if (others.isEmpty()) return null
        val ownNormalized = own.mapTo(HashSet(), ::normalize)
        val byKey = LinkedHashMap<String, String>()
        others.values.first().forEach { byKey.putIfAbsent(normalize(it), it) }
        val missing = byKey.filter { (normalized, _) ->
            normalized !in ownNormalized && others.values.all { keys -> keys.any { normalize(it) == normalized } }
        }.values.sorted()
        if (missing.isEmpty()) return null
        return ConfigDrift(missing.associateWith { others.keys.sorted() })
    }

    fun isVariant(name: String): Boolean = variant(name) != null

    private fun variant(name: String): Variant? {
        SPRING.matchEntire(name)?.let {
            val format = if (it.groupValues[3] == "properties") Format.PROPERTIES else Format.YAML
            return Variant(it.groupValues[1], it.groupValues[2], format).takeIf(::counts)
        }
        HELM.matchEntire(name)?.let { return Variant("values", it.groupValues[1], Format.YAML).takeIf(::counts) }
        DOTENV.matchEntire(name)?.let { return Variant(".env", it.groupValues[1], Format.DOTENV).takeIf(::counts) }
        return null
    }

    private fun counts(variant: Variant): Boolean {
        val name = variant.variant.lowercase(Locale.ROOT)
        return name !in IGNORED_VARIANTS && !name.endsWith(".local") && !name.endsWith("-local")
    }

    /** Spring relaxed binding treats `server.max-http-header-size` and `server.maxHttpHeaderSize` alike. */
    private fun normalize(key: String): String = key.lowercase(Locale.ROOT).replace("-", "").replace("_", "")

    private fun keys(file: VirtualFile, variant: Variant): Set<String>? =
        FileContentCache.inspect(file, KEYS, null) { text ->
            when (variant.format) {
                Format.YAML -> yamlKeys(text)
                Format.PROPERTIES -> propertiesKeys(text)
                Format.DOTENV -> text.lineSequence().mapNotNull { DOTENV_KEY.find(it)?.groupValues?.get(1) }.toSet()
            }
        }

    internal fun propertiesKeys(text: String): Set<String>? = try {
        Properties().apply { load(StringReader(text)) }.stringPropertyNames()
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Leaf keys as dotted paths across every document, or null when [text] is not YAML. */
    internal fun yamlKeys(text: String): Set<String>? {
        val options = LoaderOptions().apply {
            codePointLimit = FileContentCache.MAX_FILE_SIZE
            nestingDepthLimit = 32
            maxAliasesForCollections = 25
        }
        val keys = LinkedHashSet<String>()
        try {
            for (document in Yaml(SafeConstructor(options)).composeAll(StringReader(text))) {
                if (document is MappingNode) collect(document, "", keys)
            }
        } catch (_: YAMLException) {
            return null
        }
        return keys
    }

    private fun collect(node: Node, prefix: String, keys: MutableSet<String>) {
        if (node !is MappingNode) {
            if (prefix.isNotEmpty()) keys += prefix
            return
        }
        if (node.value.isEmpty() && prefix.isNotEmpty()) keys += prefix
        for (tuple in node.value) {
            val key = (tuple.keyNode as? ScalarNode)?.value ?: continue
            collect(tuple.valueNode, if (prefix.isEmpty()) key else "$prefix.$key", keys)
        }
    }
}

/** A config variant that lacks keys its siblings define. */
internal class ConfigDriftBadgeIcon(baseIcon: Icon, highContrast: Boolean) :
    WarningTriangleBadgeIcon(baseIcon, highContrast) {
    override fun withBase(base: Icon): BadgedIcon = ConfigDriftBadgeIcon(base, highContrast)
}

/** Repaints icons when a config variant changes; each variant's badge depends on its siblings. */
internal class ConfigDriftListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        if (events.any { ConfigVariants.isVariant(it.path.substringAfterLast('/')) }) {
            ApplicationManager.getApplication().invokeLater { RefreshProjectIconPresentations.refresh() }
        }
    }
}
