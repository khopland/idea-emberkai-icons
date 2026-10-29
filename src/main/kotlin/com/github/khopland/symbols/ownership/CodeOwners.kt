package com.github.khopland.symbols.ownership

import com.github.khopland.symbols.core.FileContentCache
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * One CODEOWNERS line. Patterns follow GitHub's rules: a leading or inner `/` anchors the pattern
 * to the repository root, otherwise it matches at any depth; a trailing `/` matches a directory
 * and everything in it; a name without wildcards also matches everything below it, while a
 * wildcard in the last segment, as in `docs/` + `*`, matches only that level. An empty owner list
 * marks the path as explicitly unowned.
 */
internal class CodeOwnersRule private constructor(
    val pattern: String,
    val owners: List<String>,
    /** One-based line number in the CODEOWNERS file. */
    val line: Int,
    private val self: Regex,
    private val directoryOnly: Boolean,
    private val includesDescendants: Boolean,
) {
    /** [path] is relative to the repository root and uses `/` separators. */
    fun matches(path: String, isDirectory: Boolean): Boolean = when {
        directoryOnly -> (isDirectory && self.matches(path)) || descendant(path)
        includesDescendants -> self.matches(path) || descendant(path)
        else -> self.matches(path)
    }

    /** Whether some ancestor directory of [path] matches the pattern itself. */
    private fun descendant(path: String): Boolean {
        var end = path.indexOf('/')
        while (end > 0) {
            if (self.matches(path.substring(0, end))) return true
            end = path.indexOf('/', end + 1)
        }
        return false
    }

    companion object {
        fun compile(pattern: String, owners: List<String>, line: Int): CodeOwnersRule? {
            val directoryOnly = pattern.endsWith("/")
            var body = pattern.trimEnd('/')
            if (body.isEmpty()) return null
            val anchored = body.startsWith("/") || '/' in body
            body = body.removePrefix("/")
            val last = body.substringAfterLast('/')
            val regex = (if (anchored) "" else "(?:.*/)?") + glob(body)
            return CodeOwnersRule(
                pattern, owners, line, Regex(regex),
                directoryOnly = directoryOnly,
                includesDescendants = '*' !in last && '?' !in last,
            )
        }

        private fun glob(pattern: String): String = buildString {
            var index = 0
            while (index < pattern.length) {
                when {
                    pattern.startsWith("**/", index) -> { append("(?:.*/)?"); index += 3 }
                    pattern.startsWith("/**", index) && index + 3 == pattern.length -> { append("/.*"); index += 3 }
                    pattern.startsWith("**", index) -> { append(".*"); index += 2 }
                    else -> {
                        when (val char = pattern[index]) {
                            '*' -> append("[^/]*")
                            '?' -> append("[^/]")
                            else -> append(Regex.escape(char.toString()))
                        }
                        index++
                    }
                }
            }
        }
    }
}

/** The owners of a file or folder and the CODEOWNERS line that assigned them. */
internal class Ownership(
    val owners: List<String>,
    val rule: CodeOwnersRule,
    val codeOwnersFile: VirtualFile,
)

/**
 * Reads CODEOWNERS the way GitHub does: the first of `.github/CODEOWNERS`, `CODEOWNERS`, and
 * `docs/CODEOWNERS` in the repository root (GitLab's `.gitlab/CODEOWNERS` is also accepted), and
 * the last matching line wins. GitLab section headers are skipped, so their rules read as one list.
 * Parsed rules are cached by modification stamp, including unsaved edits.
 */
internal object CodeOwners {
    private val LOCATIONS = listOf(".github/CODEOWNERS", "CODEOWNERS", "docs/CODEOWNERS", ".gitlab/CODEOWNERS")
    private val LOCATION_FOLDERS = setOf(".github", "docs", ".gitlab")
    private val RULES: Key<FileContentCache.Inspection<List<CodeOwnersRule>>> = Key.create("emberkai.codeOwners")
    private val SEPARATOR = Regex("(?<!\\\\)\\s+")

    /** The CODEOWNERS file for a repository rooted at [root]. */
    fun fileAt(root: VirtualFile): VirtualFile? = LOCATIONS.firstNotNullOfOrNull { location ->
        root.findFileByRelativePath(location)?.takeIf { it.isValid && !it.isDirectory }
    }

    fun rules(codeOwnersFile: VirtualFile): List<CodeOwnersRule> =
        FileContentCache.inspect(codeOwnersFile, RULES, emptyList(), ::parse)

    /** Every owner named in [codeOwnersFile], in first-mention order. */
    fun owners(codeOwnersFile: VirtualFile): List<String> = rules(codeOwnersFile).flatMap { it.owners }.distinct()

    /**
     * Who owns [file], or null when no CODEOWNERS governs it or no line matches. The search walks up
     * to the nearest folder with a CODEOWNERS file and stops at a Git repository root.
     */
    fun ownership(file: VirtualFile): Ownership? {
        var directory: VirtualFile? = if (file.isDirectory) file else file.parent
        while (directory != null) {
            val root = repositoryRoot(directory)
            if (root != null) return match(file, root.first, root.second)
            if (directory.findChild(".git") != null) return null
            directory = directory.parent
        }
        return null
    }

    /** The repository root that [directory] holds CODEOWNERS for, and that file. */
    private fun repositoryRoot(directory: VirtualFile): Pair<VirtualFile, VirtualFile>? {
        val codeOwners = fileAt(directory) ?: return null
        // `.github/CODEOWNERS` is found from `.github` itself too; the repository is its parent.
        val parent = directory.parent
        if (directory.name in LOCATION_FOLDERS && parent != null && fileAt(parent) == codeOwners) {
            return parent to codeOwners
        }
        return directory to codeOwners
    }

    private fun match(file: VirtualFile, root: VirtualFile, codeOwners: VirtualFile): Ownership? {
        val path = VfsUtilCore.getRelativePath(file, root, '/')?.takeIf { it.isNotEmpty() } ?: return null
        val rule = rules(codeOwners).lastOrNull { it.matches(path, file.isDirectory) } ?: return null
        return Ownership(rule.owners, rule, codeOwners)
    }

    internal fun parse(text: String): List<CodeOwnersRule> = text.lineSequence().withIndex().mapNotNull { (index, raw) ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("[") || line.startsWith("^[")) {
            return@mapNotNull null
        }
        val tokens = line.substringBefore(" #").trim().split(SEPARATOR)
        val pattern = tokens.first().replace("\\ ", " ").replace("\\#", "#")
        val owners = tokens.drop(1).filter { '@' in it }
        CodeOwnersRule.compile(pattern, owners, index + 1)
    }.toList()
}
