package com.github.khopland.symbols.spring

import com.github.khopland.symbols.core.RefreshProjectIconPresentations
import com.intellij.execution.CommonJavaRunConfigurationParameters
import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.project.Project

/**
 * Recomputes the active Spring profiles when the selected run configuration changes. Registered
 * from `java-support.xml`, so the Java run-configuration interfaces are always available.
 */
internal class ActiveSpringProfileListener(private val project: Project) : RunManagerListener {
    override fun stateLoaded(runManager: RunManager, isFirstLoadState: Boolean) = update(runManager)

    override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) = update()

    override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) = update()

    override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) = update()

    private fun update(runManager: RunManager = RunManager.getInstance(project)) {
        if (project.isDisposed) return
        val snapshot = runManager.selectedConfiguration?.let(ActiveSpringProfileReader::read)
        if (!ActiveSpringProfiles.getInstance(project).update(snapshot)) return
        ApplicationManager.getApplication().invokeLater(
            { RefreshProjectIconPresentations.refresh(project) },
            project.disposed,
        )
    }
}

/**
 * Reads profiles from Java application-style configurations and Gradle `bootRun` tasks. Anything
 * else, or a configuration that fails to report its parameters, yields no snapshot and no marker.
 */
internal object ActiveSpringProfileReader {
    private const val SPRING_BOOT_CONFIGURATION = "com.intellij.spring.boot.run.SpringBootApplicationRunConfiguration"

    fun read(settings: RunnerAndConfigurationSettings): ActiveSpringProfiles.Snapshot? {
        val profiles = runCatching { profilesOf(settings.configuration) }.getOrNull()
        return profiles?.takeIf { it.isNotEmpty() }?.let { ActiveSpringProfiles.Snapshot(settings.name, it) }
    }

    /** The profiles [configuration] activates; may throw when a configuration misreports its parameters. */
    fun profilesOf(configuration: RunConfiguration): Set<String> {
        if (configuration is ExternalSystemRunConfiguration) return gradleProfilesOf(configuration)
        val program = configuration as? CommonProgramRunConfigurationParameters
        return SpringProfileArguments.resolve(
            programArguments = program?.programParameters,
            systemProperty = springBootActiveProfiles(configuration),
            vmOptions = (configuration as? CommonJavaRunConfigurationParameters)?.vmParameters,
            environment = program?.envs,
        )
    }

    private fun gradleProfilesOf(configuration: ExternalSystemRunConfiguration): Set<String> {
        val settings = configuration.settings
        if (!settings.externalSystemIdString.equals("GRADLE", ignoreCase = true)) return emptySet()
        if (settings.taskNames.none { it.substringAfterLast(':') == "bootRun" }) return emptySet()
        val arguments = (settings.taskNames + listOfNotNull(settings.scriptParameters)).joinToString(" ")
        return SpringProfileArguments.fromGradleArguments(arguments)
            ?: SpringProfileArguments.fromEnvironment(settings.env)
            ?: emptySet()
    }

    /**
     * The Ultimate Spring Boot configuration keeps its "Active profiles" field outside any public
     * interface, so read it defensively; Spring Boot passes that field as a system property.
     */
    private fun springBootActiveProfiles(configuration: RunConfiguration): String? {
        if (configuration.javaClass.name != SPRING_BOOT_CONFIGURATION) return null
        return runCatching {
            configuration.javaClass.getMethod("getActiveProfiles").invoke(configuration) as? String
        }.getOrNull()
    }
}
