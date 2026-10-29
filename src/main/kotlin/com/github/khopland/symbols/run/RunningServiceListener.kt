package com.github.khopland.symbols.run

import com.github.khopland.symbols.core.RefreshProjectIconPresentations
import com.github.khopland.symbols.spring.ActiveSpringProfileReader
import com.github.khopland.symbols.spring.SpringProfileArguments
import com.github.khopland.symbols.spring.SpringServerPort
import com.intellij.execution.CommonJavaRunConfigurationParameters
import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.ExecutionListener
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.jps.model.java.JavaResourceRootType

/**
 * Tracks run and debug sessions as they start and stop. Mapping a configuration to its module and
 * reading its port happen in a background read action; only the folders of the affected sessions
 * are repainted. Registered from `java-support.xml`, where module-based and Gradle run
 * configurations are always available.
 */
internal class RunningServiceListener(private val project: Project) : ExecutionListener {
    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        val name = env.runnerAndConfigurationSettings?.name ?: env.runProfile.name
        val debug = executorId == DefaultDebugExecutor.EXECUTOR_ID
        ReadAction.nonBlocking<RunningServiceReader.Target?> {
            RunningServiceReader.read(project, env.runProfile, name, debug)
        }
            .expireWith(project)
            .finishOnUiThread(ModalityState.any()) { target ->
                // A short run can end before its module was mapped; its stop event found nothing to remove.
                if (target == null || handler.isProcessTerminated) return@finishOnUiThread
                val changed = RunningServices.getInstance(project).start(handler, target.directories, target.session)
                RefreshProjectIconPresentations.refresh(project, changed)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        val changed = RunningServices.getIfCreated(project)?.stop(handler).orEmpty()
        if (changed.isEmpty()) return
        ApplicationManager.getApplication().invokeLater(
            { RefreshProjectIconPresentations.refresh(project, changed) },
            project.disposed,
        )
    }
}

/**
 * Maps a run profile to the module folder that owns it. Compound configurations launch their
 * children one by one, so each child is mapped on its own. Anything that does not name exactly one
 * module or Gradle project yields no target, so the badge never lands on a guess.
 */
internal object RunningServiceReader {
    class Target(val directories: List<VirtualFile>, val session: RunningServices.Session)

    fun read(project: Project, profile: RunProfile, name: String, debug: Boolean): Target? {
        if (project.isDisposed) return null
        val configuration = profile as? RunConfiguration ?: return null
        val (directory, module) = runCatching { ownerOf(configuration) }.getOrNull() ?: return null
        val port = runCatching { portOf(configuration, directory, module) }.getOrNull()
        return Target(listOf(directory), RunningServices.Session(name, debug, port))
    }

    private fun ownerOf(configuration: RunConfiguration): Pair<VirtualFile, Module?>? = when (configuration) {
        is ExternalSystemRunConfiguration -> gradleProjectOf(configuration)?.let { it to null }
        is ModuleBasedConfiguration<*, *> -> configuration.configurationModule.module?.let { module ->
            moduleDirectory(module)?.let { it to module }
        }
        else -> null
    }

    /**
     * Gradle-imported source-set modules such as `app.main` keep their content root on
     * `app/src/main`, so the linked Gradle project folder is the one to badge.
     */
    private fun moduleDirectory(module: Module): VirtualFile? {
        ExternalSystemApiUtil.getExternalProjectPath(module)?.let(::directory)?.let { return it }
        return ModuleRootManager.getInstance(module).contentRoots.singleOrNull()?.takeIf { it.isDirectory }
    }

    /**
     * The Gradle project a task run belongs to. Qualified tasks such as `:orders:bootRun` point at a
     * subproject folder in the default layout; tasks in different subprojects are ambiguous.
     */
    private fun gradleProjectOf(configuration: ExternalSystemRunConfiguration): VirtualFile? {
        val settings = configuration.settings
        if (!settings.externalSystemIdString.equals("GRADLE", ignoreCase = true)) return null
        val root = settings.externalProjectPath?.let(::directory) ?: return null
        val tokens = SpringProfileArguments.tokens(settings.taskNames.joinToString(" "), supportSingleQuotes = true)
        val taskNames = tokens.filterIndexed { index, token ->
            !token.startsWith('-') && tokens.getOrNull(index - 1) != "--args"
        }
        if (taskNames.isEmpty()) return null
        val projectPaths = taskNames.map { it.removePrefix(":").substringBeforeLast(':', "") }.distinct()
        val projectPath = projectPaths.singleOrNull() ?: return null
        if (projectPath.isEmpty()) return root
        return root.findFileByRelativePath(projectPath.replace(':', '/'))?.takeIf { it.isDirectory }
    }

    private fun portOf(configuration: RunConfiguration, directory: VirtualFile, module: Module?): Int? {
        val profiles = runCatching { ActiveSpringProfileReader.profilesOf(configuration) }.getOrDefault(emptySet())
        val resources = resourceDirectories(directory, module)
        if (configuration is ExternalSystemRunConfiguration) {
            val settings = configuration.settings
            val arguments = (settings.taskNames + listOfNotNull(settings.scriptParameters)).joinToString(" ")
            return SpringServerPort.resolve(
                programArguments = SpringProfileArguments.gradleForwardedArguments(arguments).joinToString(" "),
                environment = settings.env,
                profiles = profiles,
                resourceDirectories = resources,
            )
        }
        val program = configuration as? CommonProgramRunConfigurationParameters
        return SpringServerPort.resolve(
            programArguments = program?.programParameters,
            vmOptions = (configuration as? CommonJavaRunConfigurationParameters)?.vmParameters,
            environment = program?.envs,
            profiles = profiles,
            resourceDirectories = resources,
        )
    }

    private fun resourceDirectories(directory: VirtualFile, module: Module?): List<VirtualFile> {
        val moduleResources = module?.let {
            ModuleRootManager.getInstance(it).getSourceRoots(JavaResourceRootType.RESOURCE)
        }.orEmpty()
        return (moduleResources + listOfNotNull(directory.findFileByRelativePath("src/main/resources"))).distinct()
    }

    private fun directory(path: String): VirtualFile? =
        LocalFileSystem.getInstance().findFileByPath(path)?.takeIf { it.isDirectory }
}
