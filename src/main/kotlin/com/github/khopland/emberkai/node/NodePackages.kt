package com.github.khopland.emberkai.node

import com.github.khopland.emberkai.core.FileContentCache
import com.github.khopland.emberkai.core.RefreshProjectIconPresentations
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.concurrent.atomic.AtomicBoolean

/** A folder with a `package.json`, and the names of the packages it depends on. */
internal class NodePackage(val root: VirtualFile, val dependencies: Set<String>) {
    fun dependsOnAny(names: Set<String>): Boolean = names.any { it in dependencies }
}

/**
 * Finds the `package.json` that owns a file, and the Next.js app a file belongs to. Dependency
 * names are read with a pattern rather than a JSON parser: dependency objects never nest, and a
 * pattern also copes with tab indentation and a half-typed manifest. Results are cached per
 * manifest modification stamp. Searches stop at the file's content root, so a stray `package.json`
 * above the project never counts.
 *
 * Where loading from disk is a slow operation (see [FileContentCache.mayLoad]), only folders whose
 * children are already loaded and manifests already read are used; a miss answers "no package"
 * until a background caller, such as the Project view, has read them.
 */
internal object NodePackages {
    const val MANIFEST = "package.json"
    val NEXT_CONFIGS = setOf("next.config.js", "next.config.mjs", "next.config.cjs", "next.config.ts", "next.config.mts")

    private const val MAX_DEPTH = 24
    private val DEPENDENCIES = Key.create<FileContentCache.Inspection<Set<String>>>("emberkai.packageJsonDependencies")
    private val DEPENDENCY_BLOCK =
        Regex(""""(?:dependencies|devDependencies|peerDependencies|optionalDependencies)"\s*:\s*\{([^{}]*)}""")
    private val DEPENDENCY_NAME = Regex(""""([^"\\]+)"\s*:""")

    /** The package [file] belongs to; null outside any package and for installed packages. */
    fun nearest(file: VirtualFile, project: Project): NodePackage? {
        val load = FileContentCache.mayLoad()
        val manifestDirectory = ancestors(file, project).firstOrNull { child(it, MANIFEST, load) != null } ?: return null
        val manifest = child(manifestDirectory, MANIFEST, load) ?: return null
        val dependencies = if (load) {
            dependencies(manifest)
        } else {
            FileContentCache.peek(manifest, DEPENDENCIES)?.value ?: return null
        }
        return NodePackage(manifestDirectory, dependencies)
    }

    /** The nearest folder with a `next.config.*`, which roots a Next.js app even when the monorepo root lists `next`. */
    fun nextAppRoot(file: VirtualFile, project: Project): VirtualFile? {
        val load = FileContentCache.mayLoad()
        return ancestors(file, project).firstOrNull { directory -> NEXT_CONFIGS.any { child(directory, it, load) != null } }
    }

    fun child(directory: VirtualFile, name: String, load: Boolean = FileContentCache.mayLoad()): VirtualFile? =
        lookup(directory, name, load)?.takeUnless { it.isDirectory }

    fun childDirectory(directory: VirtualFile, name: String, load: Boolean = FileContentCache.mayLoad()): Boolean =
        lookup(directory, name, load)?.isDirectory == true

    private fun lookup(directory: VirtualFile, name: String, load: Boolean): VirtualFile? =
        if (load) directory.findChild(name) else (directory as? NewVirtualFile)?.findChildIfCached(name)

    /** [file]'s folders, innermost first, up to its content root; none for installed packages. */
    private fun ancestors(file: VirtualFile, project: Project): Sequence<VirtualFile> {
        if ("/node_modules/" in file.path) return emptySequence()
        val contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file) ?: return emptySequence()
        val start = if (file.isDirectory) file else file.parent ?: return emptySequence()
        return generateSequence(start) { directory -> directory.takeUnless { it == contentRoot }?.parent }.take(MAX_DEPTH)
    }

    private fun dependencies(manifest: VirtualFile): Set<String> =
        FileContentCache.inspect(manifest, DEPENDENCIES, emptySet()) { text ->
            DEPENDENCY_BLOCK.findAll(text)
                .flatMap { block -> DEPENDENCY_NAME.findAll(block.groupValues[1]).map { it.groupValues[1] } }
                .toSet()
        }
}

/**
 * Repaints icons when a `package.json` or `next.config.*` changes, appears, or goes away: whether a
 * source file is a route or a controller depends on its package's dependencies and the app's config.
 */
internal class NodePackageListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        // An install writes thousands of manifests in many batches; repaint once per burst.
        if (isRelevant(events) && refreshPending.compareAndSet(false, true)) {
            ApplicationManager.getApplication().invokeLater {
                refreshPending.set(false)
                RefreshProjectIconPresentations.refresh()
            }
        }
    }

    /** Manifests and configs of the project's own packages; installed packages never decide a badge. */
    internal fun isRelevant(events: List<VFileEvent>): Boolean = events.any { event ->
        val name = event.path.substringAfterLast('/')
        (name == NodePackages.MANIFEST || name in NodePackages.NEXT_CONFIGS) && "/node_modules/" !in event.path
    }

    private companion object {
        val refreshPending = AtomicBoolean(false)
    }
}
