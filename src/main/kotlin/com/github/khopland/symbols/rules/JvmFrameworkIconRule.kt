package com.github.khopland.symbols.rules

import com.github.khopland.symbols.core.IconRuleCategory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.regex.Pattern

/**
 * Quarkus, Micronaut, and Ktor configuration, identified like Spring Boot's: an `application` or
 * `bootstrap` file whose nearest build files declare the framework. Spring Boot's rule runs first,
 * so a build that declares both keeps the Spring icon.
 */
internal class JvmFrameworkIconRule : ContextIconRule {
    override val category = IconRuleCategory.JVM_FRAMEWORKS

    override fun fileIconKey(file: VirtualFile, name: String, project: Project?): String? {
        if (!CONFIG_FILE.matcher(name).matches()) return null
        return when (BuildFiles.firstDeclared(file, project, MARKERS)) {
            QUARKUS -> "context.quarkus"
            MICRONAUT -> "context.micronaut"
            KTOR -> "context.ktor"
            else -> null
        }
    }

    private companion object {
        val CONFIG_FILE: Pattern = Pattern.compile("(?:application|bootstrap)(?:-.+)?\\.(?:properties|yaml|yml|conf|toml)")

        /** Matches a quoted Gradle coordinate or plugin ID, or a Maven groupId, in [group] or its subgroups. */
        private fun marker(name: String, group: String, subgroups: String = "") = BuildMarker(
            name,
            Pattern.compile(
                "[\"']${Pattern.quote(group)}$subgroups(?::[^\"']*)?[\"']" +
                    "|<groupId>\\s*${Pattern.quote(group)}$subgroups\\s*</groupId>",
            ),
        )

        val QUARKUS = marker("quarkus", "io.quarkus", "(?:\\.platform)?")
        val MICRONAUT = marker("micronaut", "io.micronaut", "(?:\\.[a-z][a-z0-9-]*)*")
        val KTOR = marker("ktor", "io.ktor", "(?:\\.plugin)?")
        val MARKERS = listOf(QUARKUS, MICRONAUT, KTOR)
    }
}
