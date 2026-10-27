package com.github.khopland.symbols

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "EmberkaiIconsSettings", storages = [Storage("emberkai-icons.xml")])
class EmberkaiIconsSettings : PersistentStateComponent<EmberkaiIconsSettings.State> {
    class State {
        var preserveNativeJavaKotlinIcons: Boolean = true
        var colorServiceFolders: Boolean = true
        var moduleAwareColors: Boolean = false
        var serviceFolderPalette: String = FolderIconPalette.EMBERKAI.name
        var highContrastFolderIcons: Boolean = false
        var monochromeFolderIcons: Boolean = false
        var reducedFolderColor: Boolean = false
        var ci: Boolean = true
        var migrations: Boolean = true
        var helm: Boolean = true
        var ansible: Boolean = true
        var infrastructure: Boolean = true
        var gitOps: Boolean = true
        var apiSpecifications: Boolean = true
        var kubernetes: Boolean = true
        var springBoot: Boolean = true
        var springModulith: Boolean = true
        var architecture: Boolean = false
        var environmentVariants: Boolean = true
    }

    @Volatile
    private var currentState = State()
    private val stateLock = Any()

    override fun getState(): State = currentState.copyState()

    override fun loadState(state: State) {
        synchronized(stateLock) {
            currentState = state.copyState()
        }
    }

    var preserveNativeJavaKotlinIcons: Boolean
        get() = currentState.preserveNativeJavaKotlinIcons
        set(value) {
            updateState { it.preserveNativeJavaKotlinIcons = value }
        }

    var colorServiceFolders: Boolean
        get() = currentState.colorServiceFolders
        set(value) {
            updateState { it.colorServiceFolders = value }
        }

    var moduleAwareColors: Boolean
        get() = currentState.moduleAwareColors
        set(value) {
            updateState { it.moduleAwareColors = value }
        }

    internal var serviceFolderPalette: FolderIconPalette
        get() = FolderIconPalette.fromId(currentState.serviceFolderPalette)
        set(value) {
            updateState { it.serviceFolderPalette = value.name }
        }

    var highContrastFolderIcons: Boolean
        get() = currentState.highContrastFolderIcons
        set(value) {
            updateState { it.highContrastFolderIcons = value }
        }

    var monochromeFolderIcons: Boolean
        get() = currentState.monochromeFolderIcons
        set(value) {
            updateState { it.monochromeFolderIcons = value }
        }

    var reducedFolderColor: Boolean
        get() = currentState.reducedFolderColor
        set(value) {
            updateState { it.reducedFolderColor = value }
        }

    internal fun isEnabled(category: IconRuleCategory): Boolean = when (category) {
        IconRuleCategory.CI -> currentState.ci
        IconRuleCategory.MIGRATION -> currentState.migrations
        IconRuleCategory.HELM -> currentState.helm
        IconRuleCategory.ANSIBLE -> currentState.ansible
        IconRuleCategory.INFRASTRUCTURE -> currentState.infrastructure
        IconRuleCategory.GITOPS -> currentState.gitOps
        IconRuleCategory.API_SPECIFICATION -> currentState.apiSpecifications
        IconRuleCategory.KUBERNETES -> currentState.kubernetes
        IconRuleCategory.SPRING_BOOT -> currentState.springBoot
        IconRuleCategory.SPRING_MODULITH -> currentState.springModulith
        IconRuleCategory.ARCHITECTURE -> currentState.architecture
        IconRuleCategory.ENVIRONMENT_VARIANTS -> currentState.environmentVariants
    }

    internal fun setEnabled(category: IconRuleCategory, enabled: Boolean) {
        updateState { state ->
            when (category) {
                IconRuleCategory.CI -> state.ci = enabled
                IconRuleCategory.MIGRATION -> state.migrations = enabled
                IconRuleCategory.HELM -> state.helm = enabled
                IconRuleCategory.ANSIBLE -> state.ansible = enabled
                IconRuleCategory.INFRASTRUCTURE -> state.infrastructure = enabled
                IconRuleCategory.GITOPS -> state.gitOps = enabled
                IconRuleCategory.API_SPECIFICATION -> state.apiSpecifications = enabled
                IconRuleCategory.KUBERNETES -> state.kubernetes = enabled
                IconRuleCategory.SPRING_BOOT -> state.springBoot = enabled
                IconRuleCategory.SPRING_MODULITH -> state.springModulith = enabled
                IconRuleCategory.ARCHITECTURE -> state.architecture = enabled
                IconRuleCategory.ENVIRONMENT_VARIANTS -> state.environmentVariants = enabled
            }
        }
    }

    private fun updateState(update: (State) -> Unit) {
        synchronized(stateLock) {
            val newState = currentState.copyState()
            update(newState)
            currentState = newState
        }
    }

    private fun State.copyState(): State = State().also { copy ->
        copy.preserveNativeJavaKotlinIcons = preserveNativeJavaKotlinIcons
        copy.colorServiceFolders = colorServiceFolders
        copy.moduleAwareColors = moduleAwareColors
        copy.serviceFolderPalette = serviceFolderPalette
        copy.highContrastFolderIcons = highContrastFolderIcons
        copy.monochromeFolderIcons = monochromeFolderIcons
        copy.reducedFolderColor = reducedFolderColor
        copy.ci = ci
        copy.migrations = migrations
        copy.helm = helm
        copy.ansible = ansible
        copy.infrastructure = infrastructure
        copy.gitOps = gitOps
        copy.apiSpecifications = apiSpecifications
        copy.kubernetes = kubernetes
        copy.springBoot = springBoot
        copy.springModulith = springModulith
        copy.architecture = architecture
        copy.environmentVariants = environmentVariants
    }

    companion object {
        fun getInstance(): EmberkaiIconsSettings =
            ApplicationManager.getApplication().getService(EmberkaiIconsSettings::class.java)
    }
}
