package com.github.khopland.symbols.rules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.regex.Pattern

/**
 * The JVM application frameworks that own `application` and `bootstrap` configuration files. One
 * shared walk decides between them: the nearest folder whose build files or version catalog
 * declare any framework wins, so a service that declares Quarkus is not claimed by Spring Boot
 * through a monorepo's shared catalog further up. Within one folder, earlier frameworks win.
 */
internal enum class JvmFramework(val mappingKey: String, val marker: BuildMarker) {
    SPRING_BOOT(
        "context.spring",
        BuildMarker(
            "springBoot",
            Pattern.compile(
                "[\"']org\\.springframework\\.boot(?::[^\"']*)?[\"']" +
                    "|<groupId>\\s*org\\.springframework\\.boot\\s*</groupId>",
            ),
        ),
    ),
    QUARKUS("context.quarkus", marker("quarkus", "io.quarkus", "(?:\\.platform)?")),
    MICRONAUT("context.micronaut", marker("micronaut", "io.micronaut", "(?:\\.[a-z][a-z0-9-]*)*")),
    KTOR("context.ktor", marker("ktor", "io.ktor", "(?:\\.plugin)?")),
    ;

    companion object {
        private val MARKERS = entries.map { it.marker }

        /** The framework declared nearest to [file], or null when no build file above it declares one. */
        fun nearest(file: VirtualFile, project: Project?): JvmFramework? {
            val marker = BuildFiles.firstDeclared(file, project, MARKERS) ?: return null
            return entries.first { it.marker === marker }
        }
    }
}

/** Matches a quoted Gradle coordinate or plugin ID, or a Maven groupId, in [group] or its subgroups. */
private fun marker(name: String, group: String, subgroups: String) = BuildMarker(
    name,
    Pattern.compile(
        "[\"']${Pattern.quote(group)}$subgroups(?::[^\"']*)?[\"']" +
            "|<groupId>\\s*${Pattern.quote(group)}$subgroups\\s*</groupId>",
    ),
)
