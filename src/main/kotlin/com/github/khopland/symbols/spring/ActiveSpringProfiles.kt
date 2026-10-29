package com.github.khopland.symbols.spring

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import java.util.Locale
import java.util.regex.Pattern

/**
 * Spring profiles activated by the selected run configuration. The run-manager listener replaces
 * the snapshot when the selection or configuration changes, so icon lookup is a set membership test.
 */
@Service(Service.Level.PROJECT)
internal class ActiveSpringProfiles {
    internal data class Snapshot(val configurationName: String, val profiles: Set<String>)

    @Volatile
    var snapshot: Snapshot? = null
        private set

    /** Returns true when the visible profile set changed and icons need a refresh. */
    fun update(next: Snapshot?): Boolean {
        val normalized = next?.takeIf { it.profiles.isNotEmpty() }
        if (normalized == snapshot) return false
        snapshot = normalized
        return true
    }

    fun configurationFor(profile: String): String? =
        snapshot?.takeIf { profile in it.profiles }?.configurationName

    companion object {
        private val PROFILE_FILE: Pattern =
            Pattern.compile("^(?:application|bootstrap)-(.+)\\.(?:yml|yaml|properties)$")

        fun getInstance(project: Project): ActiveSpringProfiles =
            project.getService(ActiveSpringProfiles::class.java)

        /** Avoids creating the service from icon lookup before any run configuration was read. */
        fun getIfCreated(project: Project): ActiveSpringProfiles? =
            project.getServiceIfCreated(ActiveSpringProfiles::class.java)

        /** Profile named by a lowercase `application-<profile>` or `bootstrap-<profile>` file name. */
        fun profileOf(name: String): String? =
            PROFILE_FILE.matcher(name).takeIf { it.matches() }?.group(1)
    }
}

/**
 * Reads `spring.profiles.active` the way Spring Boot ranks its sources: command-line arguments,
 * then JVM system properties, then the `SPRING_PROFILES_ACTIVE` environment variable.
 */
internal object SpringProfileArguments {
    private const val PROGRAM_ARGUMENT = "--spring.profiles.active="
    private const val SYSTEM_PROPERTY = "-Dspring.profiles.active="
    private const val ENVIRONMENT_VARIABLE = "SPRING_PROFILES_ACTIVE"

    fun resolve(
        programArguments: String? = null,
        systemProperty: String? = null,
        vmOptions: String? = null,
        environment: Map<String, String>? = null,
    ): Set<String> =
        fromProgramArguments(programArguments)
            ?: split(systemProperty)
            ?: fromVmOptions(vmOptions)
            ?: fromEnvironment(environment)
            ?: emptySet()

    fun fromProgramArguments(arguments: String?): Set<String>? =
        split(tokens(arguments).filter { it.startsWith(PROGRAM_ARGUMENT) }
            .joinToString(",") { it.removePrefix(PROGRAM_ARGUMENT) })

    /** The JVM keeps the last `-D` value for a repeated property. */
    fun fromVmOptions(options: String?): Set<String>? =
        split(tokens(options).lastOrNull { it.startsWith(SYSTEM_PROPERTY) }?.removePrefix(SYSTEM_PROPERTY))

    fun fromEnvironment(environment: Map<String, String>?): Set<String>? =
        split(environment?.get(ENVIRONMENT_VARIABLE))

    /** Spring Boot's Gradle `bootRun` forwards `--args='...'` to the application. */
    fun fromGradleArguments(arguments: String?): Set<String>? {
        val tokens = tokens(arguments, supportSingleQuotes = true)
        val forwarded = tokens.mapIndexedNotNull { index, token ->
            when {
                token.startsWith("--args=") -> token.removePrefix("--args=")
                token == "--args" -> tokens.getOrNull(index + 1)
                else -> null
            }
        }
        return forwarded.firstNotNullOfOrNull(::fromProgramArguments)
    }

    private fun tokens(arguments: String?, supportSingleQuotes: Boolean = false): List<String> =
        if (arguments.isNullOrBlank()) emptyList() else ParametersListUtil.parse(arguments, false, supportSingleQuotes)

    private fun split(value: String?): Set<String>? = value
        ?.split(',')
        ?.map { it.trim().lowercase(Locale.ROOT) }
        ?.filter { it.isNotEmpty() }
        ?.toCollection(LinkedHashSet())
        ?.takeIf { it.isNotEmpty() }
}
