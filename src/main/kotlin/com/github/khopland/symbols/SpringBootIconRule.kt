package com.github.khopland.symbols

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.util.regex.Pattern

/** Detects Spring Boot configuration using cached declarations in nearby build files. */
internal class SpringBootIconRule : ContextIconRule {
    override val category = IconRuleCategory.SPRING_BOOT

    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? =
        if (CONFIG_FILE.matcher(name).matches() && isSpringBootProject(file, project)) "context.spring" else null

    private fun isSpringBootProject(file: VirtualFile, project: Project?): Boolean {
        var dir = file.parent
        while (dir != null) {
            for (name in BUILD_FILES) {
                if (hasSpringBootDeclaration(dir.findChild(name))) return true
            }
            val gradle = dir.findChild("gradle")
            if (gradle != null && gradle.isDirectory) {
                for (catalog in gradle.children) {
                    if (catalog.name.endsWith(".versions.toml") && hasSpringBootDeclaration(catalog)) return true
                }
            }
            if (project != null && ProjectPathUtil.isProjectBase(dir, project)) break
            dir = dir.parent
        }
        return false
    }

    private fun hasSpringBootDeclaration(file: VirtualFile?): Boolean =
        FileContentCache.inspect(file, BOOT_MARKER, false) { text ->
            SPRING_BOOT.matcher(BUILD_COMMENTS.matcher(text).replaceAll("")).find()
        }

    private companion object {
        val CONFIG_FILE: Pattern = Pattern.compile("(?:application|bootstrap)(?:-.+)?\\.(?:yaml|yml|properties)")
        val BUILD_FILES = arrayOf(
            "pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
        )
        val BUILD_COMMENTS: Pattern = Pattern.compile(
            "(?s:<!--.*?-->|/\\*.*?\\*/)|(?m:^[\\t ]*(?://|#)[^\\r\\n]*$)",
        )
        val SPRING_BOOT: Pattern = Pattern.compile(
            "[\"']org\\.springframework\\.boot(?::[^\"']*)?[\"']" +
                "|<groupId>\\s*org\\.springframework\\.boot\\s*</groupId>",
        )
        val BOOT_MARKER: Key<FileContentCache.Inspection<Boolean>> = Key.create("emberkai.springBootMarker")
    }
}
