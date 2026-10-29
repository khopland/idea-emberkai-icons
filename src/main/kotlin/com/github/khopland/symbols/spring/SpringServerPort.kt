package com.github.khopland.symbols.spring

import com.github.khopland.symbols.core.FileContentCache
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException
import java.io.StringReader
import java.util.Properties
import java.util.regex.Pattern
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode

/**
 * Reads the `server.port` a Spring Boot run will listen on, the way Spring ranks its sources:
 * command-line arguments, then JVM system properties, then `SERVER_PORT`, then config files, where
 * profile files beat `application.*`, later profiles beat earlier ones, `config/` beats the
 * resource root, and `.properties` beats YAML. Only called when a session starts, never from lookup.
 */
internal object SpringServerPort {
    private const val PROGRAM_ARGUMENT = "--server.port="
    private const val SYSTEM_PROPERTY = "-Dserver.port="
    private const val ENVIRONMENT_VARIABLE = "SERVER_PORT"
    private val EXTENSIONS = listOf("properties", "yml", "yaml")
    private val PLACEHOLDER: Pattern = Pattern.compile("^\\$\\{([^:}]+)(?::([^}]*))?}$")
    private val PORT_PATH = listOf("server", "port")
    private val PROFILE_PATHS = listOf(
        listOf("spring", "config", "activate", "on-profile"),
        listOf("spring", "profiles"),
    )

    fun resolve(
        programArguments: String? = null,
        vmOptions: String? = null,
        environment: Map<String, String>? = null,
        profiles: Collection<String> = emptyList(),
        resourceDirectories: List<VirtualFile> = emptyList(),
    ): Int? {
        val env = environment.orEmpty()
        val explicit = SpringProfileArguments.tokens(programArguments).lastOrNull { it.startsWith(PROGRAM_ARGUMENT) }
            ?.removePrefix(PROGRAM_ARGUMENT)
            ?: SpringProfileArguments.tokens(vmOptions).lastOrNull { it.startsWith(SYSTEM_PROPERTY) }
                ?.removePrefix(SYSTEM_PROPERTY)
            ?: env[ENVIRONMENT_VARIABLE]
        if (explicit != null) return port(explicit, env)
        return fromFiles(profiles.toList(), resourceDirectories)?.let { port(it, env) }
    }

    private fun fromFiles(profiles: List<String>, resourceDirectories: List<VirtualFile>): String? {
        val baseNames = profiles.reversed().map { "application-$it" } + "application"
        val locations = resourceDirectories.flatMap { listOfNotNull(it.findChild("config"), it) }
        for (baseName in baseNames) {
            for (location in locations) {
                for (extension in EXTENSIONS) {
                    val file = location.findChild("$baseName.$extension") ?: continue
                    val text = text(file) ?: continue
                    val value = if (extension == "properties") fromProperties(text) else fromYaml(text, profiles)
                    if (value != null) return value
                }
            }
        }
        return null
    }

    internal fun fromProperties(text: String): String? = try {
        Properties().apply { load(StringReader(text)) }.getProperty("server.port")?.trim()
    } catch (_: IllegalArgumentException) {
        null
    }

    /** The last document that sets the port and applies to [profiles] wins, as in Spring. */
    internal fun fromYaml(text: String, profiles: Collection<String>): String? {
        val options = LoaderOptions()
        options.codePointLimit = FileContentCache.MAX_FILE_SIZE
        options.nestingDepthLimit = 32
        options.maxAliasesForCollections = 25
        var value: String? = null
        try {
            var documents = 0
            for (document in Yaml(SafeConstructor(options)).composeAll(StringReader(text))) {
                if (++documents > 32) break
                val activation = PROFILE_PATHS.firstNotNullOfOrNull { scalar(document, it) }
                if (activation != null && activation.split(',').none { it.trim() in profiles }) continue
                scalar(document, PORT_PATH)?.let { value = it }
            }
        } catch (_: YAMLException) {
            // A broken document leaves the port unknown rather than guessing from part of it.
            return null
        }
        return value
    }

    /** Follows [path] through nested maps, accepting dotted keys such as `server.port:`. */
    private fun scalar(node: Node, path: List<String>): String? {
        if (path.isEmpty()) return (node as? ScalarNode)?.value
        if (node !is MappingNode) return null
        var found: String? = null
        for (tuple in node.value) {
            val segments = (tuple.keyNode as? ScalarNode)?.value?.split('.') ?: continue
            if (segments.size > path.size || path.subList(0, segments.size) != segments) continue
            scalar(tuple.valueNode, path.drop(segments.size))?.let { found = it }
        }
        return found
    }

    /** A listening port, resolving `${NAME:default}` against the run environment; 0 means random. */
    private fun port(raw: String, environment: Map<String, String>): Int? {
        val trimmed = raw.trim()
        val matcher = PLACEHOLDER.matcher(trimmed)
        val value = if (matcher.matches()) environment[matcher.group(1)] ?: matcher.group(2) else trimmed
        return value?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 }
    }

    private fun text(file: VirtualFile): String? {
        if (!file.isValid || file.length > FileContentCache.MAX_FILE_SIZE) return null
        FileDocumentManager.getInstance().getCachedDocument(file)?.let { return it.text }
        return try {
            VfsUtilCore.loadText(file)
        } catch (_: IOException) {
            null
        }
    }
}
