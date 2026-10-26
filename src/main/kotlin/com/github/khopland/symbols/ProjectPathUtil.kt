package com.github.khopland.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Paths

/** Canonical project path helpers; the canonical project root is cached per project. */
internal object ProjectPathUtil {
    private val BASE_PATH_CACHE = Key.create<CachedBasePath>("emberkai.canonicalProjectBasePath")

    private data class CachedBasePath(val original: String, val canonical: String)

    fun isProjectBase(directory: VirtualFile, project: Project): Boolean {
        val basePath = basePath(project) ?: return false
        return FileUtil.pathsEqual(basePath, systemPath(directory.canonicalPath ?: directory.path))
    }

    fun isUnderProject(file: VirtualFile, project: Project): Boolean {
        val basePath = basePath(project) ?: return false
        return FileUtil.isAncestor(basePath, systemPath(file.canonicalPath ?: file.path), false)
    }

    fun relativePath(file: VirtualFile, project: Project): String? {
        val basePath = basePath(project) ?: return null
        val filePath = systemPath(file.canonicalPath ?: file.path)
        if (!FileUtil.isAncestor(basePath, filePath, false)) return null
        return FileUtil.getRelativePath(basePath, filePath, '/')
    }

    fun basePath(project: Project): String? {
        val original = project.basePath ?: return null
        val cached = project.getUserData(BASE_PATH_CACHE)
        if (cached?.original == original) return cached.canonical

        val canonical = canonicalPath(original)
        project.putUserData(BASE_PATH_CACHE, CachedBasePath(original, canonical))
        return canonical
    }

    fun canonicalPath(path: String): String {
        val resolved = try {
            Paths.get(path).toRealPath().toString()
        } catch (_: IOException) {
            FileUtil.toCanonicalPath(path, true) ?: path
        } catch (_: InvalidPathException) {
            path
        } catch (_: SecurityException) {
            FileUtil.toCanonicalPath(path, true) ?: path
        }
        return FileUtil.toSystemIndependentName(resolved)
    }

    fun isSamePath(first: String, second: String): Boolean =
        FileUtil.pathsEqual(canonicalPath(first), canonicalPath(second))

    fun isAncestorPath(ancestor: String, file: String): Boolean =
        FileUtil.isAncestor(canonicalPath(ancestor), canonicalPath(file), false)

    private fun systemPath(path: String): String = FileUtil.toSystemIndependentName(path)
}
