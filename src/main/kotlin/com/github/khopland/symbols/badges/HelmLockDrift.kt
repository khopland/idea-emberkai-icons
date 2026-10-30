package com.github.khopland.symbols.badges

import com.github.khopland.symbols.core.FileContentCache
import com.github.khopland.symbols.core.RefreshProjectIconPresentations
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.io.StringReader
import javax.swing.Icon
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode

/** One entry of a `dependencies:` list in Chart.yaml or Chart.lock. */
internal data class HelmDependency(val name: String, val repository: String, val version: String)

/**
 * Finds a Chart.lock that no longer matches its Chart.yaml: a dependency added or removed, a
 * repository changed, or a locked version outside the declared range. Helm itself compares a
 * digest of both lists; reproducing it would tie the badge to Helm's JSON encoding, so this
 * compares the lists and stays quiet whenever it cannot tell, such as for repository aliases or
 * unreadable ranges.
 */
internal object HelmLockDrift {
    private val CHART_CACHE = Key.create<FileContentCache.Inspection<List<HelmDependency>?>>("emberkai.helmChartDeps")
    private val LOCK_CACHE = Key.create<FileContentCache.Inspection<List<HelmDependency>?>>("emberkai.helmLockDeps")

    /** What is out of date in [lock], or an empty list when it matches or cannot be compared. */
    fun problems(lock: VirtualFile): List<String> {
        if (lock.isDirectory || lock.name != LOCK) return emptyList()
        val chart = lock.parent?.findChild(CHART)?.takeIf { !it.isDirectory } ?: return emptyList()
        val declared = FileContentCache.inspect(chart, CHART_CACHE, null, ::dependencies) ?: return emptyList()
        val locked = FileContentCache.inspect(lock, LOCK_CACHE, null, ::dependencies) ?: return emptyList()
        return compare(declared, locked)
    }

    fun compare(declared: List<HelmDependency>, locked: List<HelmDependency>): List<String> {
        val problems = mutableListOf<String>()
        val remaining = locked.toMutableList()
        for (dependency in declared) {
            val sameName = remaining.filter { it.name == dependency.name }
            if (sameName.isEmpty()) {
                problems += "${dependency.name} is not locked"
                continue
            }
            val sameRepository = sameName.filter { sameRepository(dependency.repository, it.repository) }
            if (sameRepository.isEmpty()) {
                problems += "${dependency.name} moved to ${dependency.repository}"
                remaining.remove(sameName.first())
                continue
            }
            val match = sameRepository.firstOrNull { HelmVersionRange.satisfies(dependency.version, it.version) != false }
            if (match == null) {
                val stale = sameRepository.first()
                problems += "${dependency.name} is locked at ${stale.version}, outside ${dependency.version}"
                remaining.remove(stale)
            } else {
                remaining.remove(match)
            }
        }
        remaining.mapTo(problems) { "${it.name} is locked but no longer declared" }
        return problems
    }

    /** `@name` and `alias:name` refer to a local repository entry that the lock may record differently. */
    private fun sameRepository(declared: String, locked: String): Boolean {
        if (listOf(declared, locked).any { it.startsWith("@") || it.startsWith("alias:") }) return true
        return declared.trimEnd('/') == locked.trimEnd('/')
    }

    /** The `dependencies:` list, empty when there is none, or null when the file cannot be read as YAML. */
    internal fun dependencies(text: String): List<HelmDependency>? {
        val options = LoaderOptions().apply {
            codePointLimit = FileContentCache.MAX_FILE_SIZE
            nestingDepthLimit = 32
            maxAliasesForCollections = 25
        }
        val root = try {
            Yaml(SafeConstructor(options)).compose(StringReader(text))
        } catch (_: YAMLException) {
            return null
        } ?: return emptyList()
        if (root !is MappingNode) return null
        val list = root.value.firstOrNull { (it.keyNode as? ScalarNode)?.value == "dependencies" }?.valueNode
            ?: return emptyList()
        if (list !is SequenceNode) return null
        return list.value.mapNotNull { entry ->
            val fields = (entry as? MappingNode)?.value.orEmpty().mapNotNull { tuple ->
                val key = (tuple.keyNode as? ScalarNode)?.value ?: return@mapNotNull null
                val value = (tuple.valueNode as? ScalarNode)?.value ?: return@mapNotNull null
                key to value.trim()
            }.toMap()
            val name = fields["name"]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            HelmDependency(name, fields["repository"].orEmpty(), fields["version"].orEmpty())
        }
    }

    internal const val CHART = "Chart.yaml"
    internal const val LOCK = "Chart.lock"
}

/**
 * Helm's version ranges, as Masterminds semver reads them: exact and partial versions, `x`/`*`
 * wildcards, comparisons, `~`, `^`, hyphen ranges, `,` or space for "and", and `||` for "or".
 * Where readings could differ, the wider one is used, so a doubtful case never shows a warning.
 */
internal object HelmVersionRange {
    private val COMPARATOR = Regex("""(!=|>=|=>|<=|=<|~>|>|<|=|~|\^)?\s*v?([0-9xX*]+(?:\.[0-9xX*]+){0,2})""")
    private val HYPHEN = Regex("""^\s*v?(\S+)\s+-\s+v?(\S+)\s*$""")
    private val VERSION = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$""")

    /** True or false when both can be read; null otherwise, including prerelease versions. */
    fun satisfies(range: String, version: String): Boolean? {
        val match = VERSION.matchEntire(version.trim()) ?: return null
        if ('-' in version.substringBefore('+')) return null
        val v = Triple(match.groupValues[1].toInt(), match.groupValues[2].toIntOrNull() ?: 0, match.groupValues[3].toIntOrNull() ?: 0)
        if (range.isBlank()) return true
        var unknown = false
        for (alternative in range.split("||")) {
            when (all(alternative, v)) {
                true -> return true
                null -> unknown = true
                false -> Unit
            }
        }
        return if (unknown) null else false
    }

    private fun all(alternative: String, v: Triple<Int, Int, Int>): Boolean? {
        if (alternative.isBlank()) return true
        HYPHEN.matchEntire(alternative)?.let { hyphen ->
            val low = partial(hyphen.groupValues[1]) ?: return null
            val high = partial(hyphen.groupValues[2]) ?: return null
            return compare(v, low.floor()) >= 0 && (if (high.isExact) compare(v, high.floor()) <= 0 else compare(v, high.ceiling()) < 0)
        }
        val tokens = alternative.replace(",", " ").trim()
        var consumed = 0
        var result = true
        for (comparator in COMPARATOR.findAll(tokens)) {
            if (tokens.substring(consumed, comparator.range.first).isNotBlank()) return null
            consumed = comparator.range.last + 1
            val bound = partial(comparator.groupValues[2]) ?: return null
            result = result && test(comparator.groupValues[1], bound, v)
        }
        if (tokens.substring(consumed).isNotBlank()) return null
        return result
    }

    private fun test(operator: String, bound: Partial, v: Triple<Int, Int, Int>): Boolean = when (operator) {
        "", "=" -> if (bound.isExact) compare(v, bound.floor()) == 0 else compare(v, bound.floor()) >= 0 && compare(v, bound.ceiling()) < 0
        "!=" -> !test("=", bound, v)
        ">" -> compare(v, bound.floor()) > 0
        ">=", "=>" -> compare(v, bound.floor()) >= 0
        "<" -> compare(v, bound.floor()) < 0
        "<=", "=<" -> if (bound.isExact) compare(v, bound.floor()) <= 0 else compare(v, bound.ceiling()) < 0
        "~", "~>" -> compare(v, bound.floor()) >= 0 && compare(v, bound.tildeCeiling()) < 0
        "^" -> compare(v, bound.floor()) >= 0 && compare(v, bound.caretCeiling()) < 0
        else -> true
    }

    /** A version whose missing or wildcard parts are null. */
    private class Partial(val major: Int?, val minor: Int?, val patch: Int?) {
        val isExact: Boolean get() = major != null && minor != null && patch != null

        fun floor() = Triple(major ?: 0, minor ?: 0, patch ?: 0)

        /** The first version above everything this partial version covers. */
        fun ceiling(): Triple<Int, Int, Int> = when {
            major == null -> Triple(Int.MAX_VALUE, 0, 0)
            minor == null -> Triple(major + 1, 0, 0)
            patch == null -> Triple(major, minor + 1, 0)
            else -> Triple(major, minor, patch + 1)
        }

        fun tildeCeiling() = if (major != null && minor != null) Triple(major, minor + 1, 0) else ceiling()

        fun caretCeiling() = when {
            major == null -> Triple(Int.MAX_VALUE, 0, 0)
            major > 0 || minor == null -> Triple(major + 1, 0, 0)
            minor > 0 -> Triple(0, minor + 1, 0)
            // ^0.0.x readings differ between semver libraries; allow the whole 0.0 line.
            else -> Triple(0, 1, 0)
        }
    }

    private fun partial(text: String): Partial? {
        val parts = text.removePrefix("v").split('.')
        if (parts.size > 3) return null
        val numbers = parts.map { part ->
            when {
                part == "x" || part == "X" || part == "*" -> null
                part.all(Char::isDigit) && part.isNotEmpty() -> part.toInt()
                else -> return null
            }
        }
        // Everything after the first wildcard is a wildcard too.
        val firstWildcard = numbers.indexOf(null).takeIf { it >= 0 } ?: numbers.size
        val cleaned = numbers.mapIndexed { index, value -> if (index >= firstWildcard) null else value }
        return Partial(cleaned.getOrNull(0), cleaned.getOrNull(1), cleaned.getOrNull(2))
    }

    private fun compare(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int =
        compareValuesBy(a, b, { it.first }, { it.second }, { it.third })
}

/** The amber warning triangle on a Chart.lock that is out of date with Chart.yaml. */
internal class LockDriftBadgeIcon(baseIcon: Icon, highContrast: Boolean) :
    WarningTriangleBadgeIcon(baseIcon, highContrast) {
    override fun withBase(base: Icon): BadgedIcon = LockDriftBadgeIcon(base, highContrast)
}

/** Repaints icons when a chart's dependency lists change; the lock's badge depends on its sibling. */
internal class HelmLockDriftListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        val relevant = events.any { event ->
            val name = event.path.substringAfterLast('/')
            name == HelmLockDrift.CHART || name == HelmLockDrift.LOCK
        }
        if (relevant) ApplicationManager.getApplication().invokeLater { RefreshProjectIconPresentations.refresh() }
    }
}
