package com.github.khopland.symbols.rules

import com.github.khopland.symbols.core.FileContentCache
import com.github.khopland.symbols.core.ProjectPathUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.util.regex.Pattern

/** A framework declaration in Maven or Gradle build files, matched outside comments. Create once per framework. */
internal class BuildMarker(name: String, private val pattern: Pattern) {
    private val cacheKey: Key<FileContentCache.Inspection<Boolean>> = Key.create("emberkai.buildMarker.$name")

    fun declaredIn(file: VirtualFile?): Boolean =
        FileContentCache.inspect(file, cacheKey, false) { text ->
            pattern.matcher(BUILD_COMMENTS.matcher(text).replaceAll("")).find()
        }

    private companion object {
        val BUILD_COMMENTS: Pattern = Pattern.compile(
            "(?s:<!--.*?-->|/\\*.*?\\*/)|(?m:^[\\t ]*(?://|#)[^\\r\\n]*$)",
        )
    }
}

/**
 * Walks from a file up to the project base and checks each folder's build files and Gradle version
 * catalogs. Contents come from [FileContentCache], so no dependency resolution or build runs.
 */
internal object BuildFiles {
    private val NAMES = arrayOf("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")

    /** The first of [markers], in order, declared by the nearest folder that declares any of them. */
    fun firstDeclared(file: VirtualFile, project: Project?, markers: List<BuildMarker>): BuildMarker? {
        var dir = file.parent
        while (dir != null) {
            val candidates = buildFilesIn(dir)
            for (marker in markers) {
                if (candidates.any(marker::declaredIn)) return marker
            }
            if (project != null && ProjectPathUtil.isProjectBase(dir, project)) break
            dir = dir.parent
        }
        return null
    }

    private fun buildFilesIn(dir: VirtualFile): List<VirtualFile> {
        val files = NAMES.mapNotNull(dir::findChild).toMutableList()
        val gradle = dir.findChild("gradle")
        if (gradle != null && gradle.isDirectory) {
            gradle.children.filterTo(files) { it.name.endsWith(".versions.toml") }
        }
        return files
    }
}
