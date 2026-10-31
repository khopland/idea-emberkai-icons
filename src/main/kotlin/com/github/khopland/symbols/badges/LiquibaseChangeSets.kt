package com.github.khopland.symbols.badges

import com.github.khopland.symbols.core.FileContentCache
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.io.StringReader
import java.util.Locale
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode

/**
 * Finds changeSets that repeat an `id` and `author` within one Liquibase changelog, which Liquibase
 * rejects during validation because together with the file path they identify a changeSet. Reads
 * XML, YAML, JSON, and formatted SQL changelogs named `db.changelog*` or kept under a `changelog`,
 * `changelogs`, or `liquibase` folder, and caches the answer until the file changes.
 */
internal object LiquibaseChangeSets {
    private val CACHE = Key.create<FileContentCache.Inspection<List<String>>>("emberkai.liquibaseDuplicates")
    private val EXTENSIONS = setOf("xml", "yaml", "yml", "json", "sql")
    private val FOLDERS = setOf("changelog", "changelogs", "liquibase")
    private const val FOLDER_DEPTH = 4

    private val SQL_HEADER = Regex("""^\s*--\s*liquibase\s+formatted\s+sql""", RegexOption.IGNORE_CASE)
    private val SQL_CHANGESET = Regex("""^\s*--\s*changeset\s+("[^"]+"|[^\s:]+):(\S+)""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val XML_COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    private val XML_CHANGESET = Regex("""<(?:\w+:)?changeSet\b([^>]*)>""")
    private val XML_ATTRIBUTE = Regex("""\b(id|author)\s*=\s*("([^"]*)"|'([^']*)')""")

    /** The `id::author` identifiers [file] uses more than once, sorted; empty for anything else. */
    fun duplicates(file: VirtualFile): List<String> {
        if (file.isDirectory || !isCandidate(file)) return emptyList()
        val extension = file.extension?.lowercase(Locale.ROOT) ?: return emptyList()
        return FileContentCache.inspect(file, CACHE, emptyList()) { duplicates(identifiers(it, extension)) }
    }

    fun isCandidate(file: VirtualFile): Boolean {
        if (file.extension?.lowercase(Locale.ROOT) !in EXTENSIONS) return false
        if (file.name.lowercase(Locale.ROOT).startsWith("db.changelog")) return true
        return generateSequence(file.parent) { it.parent }.take(FOLDER_DEPTH)
            .any { it.name.lowercase(Locale.ROOT) in FOLDERS }
    }

    fun duplicates(identifiers: List<String>): List<String> =
        identifiers.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()

    /** Every changeSet's `id::author` in order, or an empty list when [text] is not a changelog. */
    fun identifiers(text: String, extension: String): List<String> = when (extension) {
        "sql" -> if (SQL_HEADER.containsMatchIn(text)) sqlIdentifiers(text) else emptyList()
        "xml" -> if ("databaseChangeLog" in text) xmlIdentifiers(text) else emptyList()
        else -> if ("databaseChangeLog" in text) yamlIdentifiers(text) else emptyList()
    }

    private fun sqlIdentifiers(text: String): List<String> =
        SQL_CHANGESET.findAll(text).map { "${it.groupValues[2]}::${it.groupValues[1].trim('"')}" }.toList()

    private fun xmlIdentifiers(text: String): List<String> =
        XML_CHANGESET.findAll(XML_COMMENT.replace(text, "")).mapNotNull { tag ->
            val attributes = XML_ATTRIBUTE.findAll(tag.groupValues[1])
                .associate { it.groupValues[1] to it.groupValues[3].ifEmpty { it.groupValues[4] } }
            attributes["id"]?.let { "$it::${attributes["author"].orEmpty()}" }
        }.toList()

    /** YAML and JSON changelogs: a `databaseChangeLog` list whose `changeSet` entries name an id and author. */
    private fun yamlIdentifiers(text: String): List<String> {
        val options = LoaderOptions().apply {
            codePointLimit = FileContentCache.MAX_FILE_SIZE
            nestingDepthLimit = 64
            maxAliasesForCollections = 25
        }
        val root = try {
            Yaml(SafeConstructor(options)).compose(StringReader(text))
        } catch (_: YAMLException) {
            return emptyList()
        }
        val entries = (root as? MappingNode)?.field("databaseChangeLog") as? SequenceNode ?: return emptyList()
        return entries.value.mapNotNull { entry ->
            val changeSet = (entry as? MappingNode)?.field("changeSet") as? MappingNode ?: return@mapNotNull null
            val id = (changeSet.field("id") as? ScalarNode)?.value ?: return@mapNotNull null
            "$id::${(changeSet.field("author") as? ScalarNode)?.value.orEmpty()}"
        }
    }

    private fun MappingNode.field(name: String): Node? =
        value.firstOrNull { (it.keyNode as? ScalarNode)?.value == name }?.valueNode
}
