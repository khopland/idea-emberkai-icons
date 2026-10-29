package com.github.khopland.symbols

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.reflect.KMutableProperty1

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
        var semanticTooltips: Boolean = true
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
        var unignoredSecrets: Boolean = true
        var activeSpringProfiles: Boolean = true
        var stereotypeBadges: Boolean = true
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

    var semanticTooltips: Boolean
        get() = currentState.semanticTooltips
        set(value) {
            updateState { it.semanticTooltips = value }
        }

    internal fun isEnabled(category: IconRuleCategory): Boolean = currentState.isEnabled(category)

    internal fun setEnabled(category: IconRuleCategory, enabled: Boolean) {
        updateState { it.setEnabled(category, enabled) }
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
        copy.semanticTooltips = semanticTooltips
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
        copy.unignoredSecrets = unignoredSecrets
        copy.activeSpringProfiles = activeSpringProfiles
        copy.stereotypeBadges = stereotypeBadges
    }

    companion object {
        private val EXPORTED_FLAGS: List<Pair<String, KMutableProperty1<State, Boolean>>> = listOf(
            "preserveNativeJavaKotlinIcons" to State::preserveNativeJavaKotlinIcons,
            "colorServiceFolders" to State::colorServiceFolders,
            "moduleAwareColors" to State::moduleAwareColors,
            "highContrastFolderIcons" to State::highContrastFolderIcons,
            "monochromeFolderIcons" to State::monochromeFolderIcons,
            "reducedFolderColor" to State::reducedFolderColor,
            "semanticTooltips" to State::semanticTooltips,
            "ci" to State::ci,
            "migrations" to State::migrations,
            "helm" to State::helm,
            "ansible" to State::ansible,
            "infrastructure" to State::infrastructure,
            "gitOps" to State::gitOps,
            "apiSpecifications" to State::apiSpecifications,
            "kubernetes" to State::kubernetes,
            "springBoot" to State::springBoot,
            "springModulith" to State::springModulith,
            "architecture" to State::architecture,
            "environmentVariants" to State::environmentVariants,
            "unignoredSecrets" to State::unignoredSecrets,
            "activeSpringProfiles" to State::activeSpringProfiles,
            "stereotypeBadges" to State::stereotypeBadges,
        )
        private const val PALETTE_KEY = "serviceFolderPalette"

        fun getInstance(): EmberkaiIconsSettings =
            ApplicationManager.getApplication().getService(EmberkaiIconsSettings::class.java)

        /** Writes [state] as a portable properties file. */
        internal fun export(state: State, path: Path) {
            val properties = Properties()
            EXPORTED_FLAGS.forEach { (name, property) -> properties.setProperty(name, property.get(state).toString()) }
            properties.setProperty(PALETTE_KEY, state.serviceFolderPalette)
            Files.newOutputStream(path).use { properties.store(it, "Emberkai Icons settings") }
        }

        /** Reads an exported file; missing or invalid values fall back to the defaults. */
        internal fun import(path: Path): State {
            val properties = Properties()
            Files.newInputStream(path).use(properties::load)
            return State().apply {
                EXPORTED_FLAGS.forEach { (name, property) ->
                    properties.getProperty(name)?.trim()?.toBooleanStrictOrNull()?.let { property.set(this, it) }
                }
                serviceFolderPalette = FolderIconPalette.fromId(properties.getProperty(PALETTE_KEY)?.trim()).name
            }
        }
    }
}

internal fun EmberkaiIconsSettings.State.isEnabled(category: IconRuleCategory): Boolean = when (category) {
    IconRuleCategory.CI -> ci
    IconRuleCategory.MIGRATION -> migrations
    IconRuleCategory.HELM -> helm
    IconRuleCategory.ANSIBLE -> ansible
    IconRuleCategory.INFRASTRUCTURE -> infrastructure
    IconRuleCategory.GITOPS -> gitOps
    IconRuleCategory.API_SPECIFICATION -> apiSpecifications
    IconRuleCategory.KUBERNETES -> kubernetes
    IconRuleCategory.SPRING_BOOT -> springBoot
    IconRuleCategory.SPRING_MODULITH -> springModulith
    IconRuleCategory.ARCHITECTURE -> architecture
    IconRuleCategory.ENVIRONMENT_VARIANTS -> environmentVariants
    IconRuleCategory.UNIGNORED_SECRETS -> unignoredSecrets
    IconRuleCategory.ACTIVE_SPRING_PROFILES -> activeSpringProfiles
    IconRuleCategory.STEREOTYPE_BADGES -> stereotypeBadges
}

internal fun EmberkaiIconsSettings.State.setEnabled(category: IconRuleCategory, enabled: Boolean) {
    when (category) {
        IconRuleCategory.CI -> ci = enabled
        IconRuleCategory.MIGRATION -> migrations = enabled
        IconRuleCategory.HELM -> helm = enabled
        IconRuleCategory.ANSIBLE -> ansible = enabled
        IconRuleCategory.INFRASTRUCTURE -> infrastructure = enabled
        IconRuleCategory.GITOPS -> gitOps = enabled
        IconRuleCategory.API_SPECIFICATION -> apiSpecifications = enabled
        IconRuleCategory.KUBERNETES -> kubernetes = enabled
        IconRuleCategory.SPRING_BOOT -> springBoot = enabled
        IconRuleCategory.SPRING_MODULITH -> springModulith = enabled
        IconRuleCategory.ARCHITECTURE -> architecture = enabled
        IconRuleCategory.ENVIRONMENT_VARIANTS -> environmentVariants = enabled
        IconRuleCategory.UNIGNORED_SECRETS -> unignoredSecrets = enabled
        IconRuleCategory.ACTIVE_SPRING_PROFILES -> activeSpringProfiles = enabled
        IconRuleCategory.STEREOTYPE_BADGES -> stereotypeBadges = enabled
    }
}
