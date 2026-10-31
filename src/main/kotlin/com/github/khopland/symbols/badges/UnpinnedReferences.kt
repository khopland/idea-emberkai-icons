package com.github.khopland.symbols.badges

import com.github.khopland.symbols.core.FileContentCache
import com.github.khopland.symbols.core.StructuredFileKind
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import javax.swing.Icon

/** A container image or GitHub Action that can change under the same reference. */
internal data class UnpinnedReference(val line: Int, val reference: String, val reason: String)

/**
 * Finds references that resolve to different content over time: GitHub Actions in workflows that
 * are not pinned to a commit SHA, and container images in Dockerfiles, Kubernetes manifests, and
 * Compose files that use `latest` or no tag at all. Actions published by GitHub itself, under the
 * `actions` and `github` owners, are left alone, as are references built from variables or
 * templates, which only resolve later. Lines are read as text, so files the YAML parser rejects
 * are still checked.
 */
internal object UnpinnedReferences {
    private val CACHE = Key.create<FileContentCache.Inspection<List<UnpinnedReference>>>("emberkai.unpinnedReferences")
    private val USES = Regex("""^\s*(?:-\s+)?uses:\s*["']?([^\s"'#]+)""")
    private val FROM = Regex("""^\s*FROM\s+(?:--\S+\s+)*(\S+)(?:\s+AS\s+(\S+))?""", RegexOption.IGNORE_CASE)
    private val IMAGE = Regex("""^\s*(?:-\s+)?image:\s*["']?([^\s"'#]+)""")
    private val COMMIT_SHA = Regex("^[0-9a-f]{40}$")
    private val TRUSTED_OWNERS = setOf("actions", "github")
    private val COMPOSE = Regex("""^(?:docker-)?compose(?:\.[\w.-]+)?\.ya?ml$""")

    enum class Kind { WORKFLOW, DOCKERFILE, MANIFEST }

    fun find(file: VirtualFile): List<UnpinnedReference> {
        if (file.isDirectory) return emptyList()
        val kind = kindOf(file) ?: return emptyList()
        return FileContentCache.inspect(file, CACHE, emptyList()) { find(it, kind) }
    }

    fun kindOf(file: VirtualFile): Kind? {
        val name = file.name.lowercase(Locale.ROOT)
        val extension = file.extension?.lowercase(Locale.ROOT)
        return when {
            name == "dockerfile" || name == "containerfile" || name.startsWith("dockerfile.") || extension == "dockerfile" ->
                Kind.DOCKERFILE
            extension != "yml" && extension != "yaml" -> null
            file.parent?.name == "workflows" && file.parent?.parent?.name == ".github" -> Kind.WORKFLOW
            COMPOSE.matches(name) -> Kind.MANIFEST
            StructuredFileKind.of(file) == StructuredFileKind.KUBERNETES -> Kind.MANIFEST
            else -> null
        }
    }

    fun find(text: String, kind: Kind): List<UnpinnedReference> {
        val stages = HashSet<String>()
        val found = mutableListOf<UnpinnedReference>()
        text.lineSequence().forEachIndexed { index, line ->
            val reference = when (kind) {
                Kind.WORKFLOW -> USES.find(line)?.groupValues?.get(1)?.let { uses -> actionProblem(uses)?.let { it to uses } }
                Kind.DOCKERFILE -> FROM.find(line)?.let { match ->
                    val image = match.groupValues[1]
                    val problem = if (image.lowercase(Locale.ROOT) in stages) null else imageProblem(image)
                    match.groupValues[2].takeIf { it.isNotEmpty() }?.let { stages += it.lowercase(Locale.ROOT) }
                    problem?.let { it to image }
                }
                Kind.MANIFEST -> IMAGE.find(line)?.groupValues?.get(1)?.let { image -> imageProblem(image)?.let { it to image } }
            }
            if (reference != null) found += UnpinnedReference(index + 1, reference.second, reference.first)
        }
        return found
    }

    /** Why [uses] can move, or null when it is pinned, local, or published by GitHub. */
    fun actionProblem(uses: String): String? {
        if (uses.startsWith("./") || uses.startsWith("docker://") || '$' in uses) return null
        val owner = uses.substringBefore('/').lowercase(Locale.ROOT)
        if (owner in TRUSTED_OWNERS) return null
        val ref = uses.substringAfter('@', "")
        return when {
            ref.isEmpty() -> "has no version"
            COMMIT_SHA.matches(ref) -> null
            else -> "uses @$ref, not a commit SHA"
        }
    }

    /** Why [image] can move, or null when it has a digest, a fixed tag, or is built from variables. */
    fun imageProblem(image: String): String? {
        if ('$' in image || "{{" in image || '@' in image || image.equals("scratch", ignoreCase = true)) return null
        val lastSegment = image.substringAfterLast('/')
        val tag = lastSegment.substringAfter(':', "")
        return when {
            ':' !in lastSegment -> "has no tag, so it means latest"
            tag.equals("latest", ignoreCase = true) -> "uses latest"
            else -> null
        }
    }
}

/** An image or action reference that is not pinned. */
internal class UnpinnedReferenceBadgeIcon(baseIcon: Icon, highContrast: Boolean) :
    WarningTriangleBadgeIcon(baseIcon, highContrast) {
    override fun withBase(base: Icon): BadgedIcon = UnpinnedReferenceBadgeIcon(base, highContrast)
}
