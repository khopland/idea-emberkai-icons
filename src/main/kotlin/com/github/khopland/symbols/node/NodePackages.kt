package com.github.khopland.symbols.node

import com.github.khopland.symbols.core.FileContentCache
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile

/** A folder with a `package.json`, and the names of the packages it depends on. */
internal class NodePackage(val root: VirtualFile, val dependencies: Set<String>) {
    fun dependsOnAny(names: Set<String>): Boolean = names.any { it in dependencies }
}

/**
 * Finds the `package.json` that owns a file. Dependency names are read with a pattern rather than
 * a JSON parser: dependency objects never nest, and a pattern also copes with tab indentation and
 * a half-typed manifest. Results are cached per manifest modification stamp.
 */
internal object NodePackages {
    private const val MAX_DEPTH = 24
    private val DEPENDENCIES = Key.create<FileContentCache.Inspection<Set<String>>>("emberkai.packageJsonDependencies")
    private val DEPENDENCY_BLOCK =
        Regex(""""(?:dependencies|devDependencies|peerDependencies|optionalDependencies)"\s*:\s*\{([^{}]*)}""")
    private val DEPENDENCY_NAME = Regex(""""([^"\\]+)"\s*:""")

    /** The package [file] belongs to; null outside any package and for installed packages. */
    fun nearest(file: VirtualFile): NodePackage? {
        if ("/node_modules/" in file.path) return null
        var directory = if (file.isDirectory) file else file.parent
        var depth = 0
        while (directory != null && depth++ < MAX_DEPTH) {
            val manifest = directory.findChild("package.json")
            if (manifest != null && !manifest.isDirectory) return NodePackage(directory, dependencies(manifest))
            directory = directory.parent
        }
        return null
    }

    private fun dependencies(manifest: VirtualFile): Set<String> =
        FileContentCache.inspect(manifest, DEPENDENCIES, emptySet()) { text ->
            DEPENDENCY_BLOCK.findAll(text)
                .flatMap { block -> DEPENDENCY_NAME.findAll(block.groupValues[1]).map { it.groupValues[1] } }
                .toSet()
        }
}
