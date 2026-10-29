package com.github.khopland.symbols.settings

import com.github.khopland.symbols.core.IconRuleCategory
import com.github.khopland.symbols.folders.FolderIconPalette
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty

@Service(Service.Level.APP)
@State(name = "EmberkaiIconsSettings", storages = [Storage("emberkai-icons.xml")])
class EmberkaiIconsSettings : PersistentStateComponent<EmberkaiIconsSettings.State> {
    /** Field names are the persisted and exported setting names; keep them stable. */
    data class State(
        var preserveNativeJavaKotlinIcons: Boolean = true,
        var colorServiceFolders: Boolean = true,
        var moduleAwareColors: Boolean = false,
        var serviceFolderPalette: String = FolderIconPalette.EMBERKAI.name,
        var highContrastFolderIcons: Boolean = false,
        var monochromeFolderIcons: Boolean = false,
        var reducedFolderColor: Boolean = false,
        var semanticTooltips: Boolean = true,
        var ci: Boolean = true,
        var migrations: Boolean = true,
        var helm: Boolean = true,
        var ansible: Boolean = true,
        var infrastructure: Boolean = true,
        var gitOps: Boolean = true,
        var apiSpecifications: Boolean = true,
        var kubernetes: Boolean = true,
        var springBoot: Boolean = true,
        var springModulith: Boolean = true,
        var architecture: Boolean = false,
        var environmentVariants: Boolean = true,
        var unignoredSecrets: Boolean = true,
        var activeSpringProfiles: Boolean = true,
        var stereotypeBadges: Boolean = true,
        var migrationConflicts: Boolean = true,
    )

    /** Replaced, never mutated, so readers on any thread see a consistent snapshot. */
    @Volatile
    private var currentState = State()
    private val stateLock = Any()

    override fun getState(): State = currentState.copy()

    override fun loadState(state: State) {
        synchronized(stateLock) {
            currentState = state.copy()
        }
    }

    var preserveNativeJavaKotlinIcons by flag(State::preserveNativeJavaKotlinIcons)
    var colorServiceFolders by flag(State::colorServiceFolders)
    var moduleAwareColors by flag(State::moduleAwareColors)
    var highContrastFolderIcons by flag(State::highContrastFolderIcons)
    var monochromeFolderIcons by flag(State::monochromeFolderIcons)
    var reducedFolderColor by flag(State::reducedFolderColor)
    var semanticTooltips by flag(State::semanticTooltips)

    internal var serviceFolderPalette: FolderIconPalette
        get() = FolderIconPalette.fromId(currentState.serviceFolderPalette)
        set(value) {
            updateState { it.serviceFolderPalette = value.name }
        }

    internal fun isEnabled(category: IconRuleCategory): Boolean = currentState.isEnabled(category)

    internal fun setEnabled(category: IconRuleCategory, enabled: Boolean) {
        updateState { it.setEnabled(category, enabled) }
    }

    private fun updateState(update: (State) -> Unit) {
        synchronized(stateLock) {
            currentState = currentState.copy().also(update)
        }
    }

    private fun flag(field: KMutableProperty1<State, Boolean>) =
        object : ReadWriteProperty<EmberkaiIconsSettings, Boolean> {
            override fun getValue(thisRef: EmberkaiIconsSettings, property: KProperty<*>): Boolean =
                field.get(currentState)

            override fun setValue(thisRef: EmberkaiIconsSettings, property: KProperty<*>, value: Boolean) {
                updateState { field.set(it, value) }
            }
        }

    companion object {
        /** Boolean settings that are not icon categories; categories map through [flag]. */
        internal val GENERAL_FLAGS: List<KMutableProperty1<State, Boolean>> = listOf(
            State::preserveNativeJavaKotlinIcons,
            State::colorServiceFolders,
            State::moduleAwareColors,
            State::highContrastFolderIcons,
            State::monochromeFolderIcons,
            State::reducedFolderColor,
            State::semanticTooltips,
        )
        private val EXPORTED_FLAGS = GENERAL_FLAGS + IconRuleCategory.entries.map(::flag)
        private const val PALETTE_KEY = "serviceFolderPalette"

        fun getInstance(): EmberkaiIconsSettings =
            ApplicationManager.getApplication().getService(EmberkaiIconsSettings::class.java)

        /** The state field that stores whether [category] is enabled. */
        internal fun flag(category: IconRuleCategory): KMutableProperty1<State, Boolean> = when (category) {
            IconRuleCategory.CI -> State::ci
            IconRuleCategory.MIGRATION -> State::migrations
            IconRuleCategory.HELM -> State::helm
            IconRuleCategory.ANSIBLE -> State::ansible
            IconRuleCategory.INFRASTRUCTURE -> State::infrastructure
            IconRuleCategory.GITOPS -> State::gitOps
            IconRuleCategory.API_SPECIFICATION -> State::apiSpecifications
            IconRuleCategory.KUBERNETES -> State::kubernetes
            IconRuleCategory.SPRING_BOOT -> State::springBoot
            IconRuleCategory.SPRING_MODULITH -> State::springModulith
            IconRuleCategory.ARCHITECTURE -> State::architecture
            IconRuleCategory.ENVIRONMENT_VARIANTS -> State::environmentVariants
            IconRuleCategory.UNIGNORED_SECRETS -> State::unignoredSecrets
            IconRuleCategory.ACTIVE_SPRING_PROFILES -> State::activeSpringProfiles
            IconRuleCategory.STEREOTYPE_BADGES -> State::stereotypeBadges
            IconRuleCategory.MIGRATION_CONFLICTS -> State::migrationConflicts
        }

        /** Writes [state] as a portable properties file. */
        internal fun export(state: State, path: Path) {
            val properties = Properties()
            EXPORTED_FLAGS.forEach { properties.setProperty(it.name, it.get(state).toString()) }
            properties.setProperty(PALETTE_KEY, state.serviceFolderPalette)
            Files.newOutputStream(path).use { properties.store(it, "Emberkai Icons settings") }
        }

        /** Reads an exported file; missing or invalid values fall back to the defaults. */
        internal fun import(path: Path): State {
            val properties = Properties()
            Files.newInputStream(path).use(properties::load)
            return State().apply {
                EXPORTED_FLAGS.forEach { property ->
                    properties.getProperty(property.name)?.trim()?.toBooleanStrictOrNull()?.let { property.set(this, it) }
                }
                serviceFolderPalette = FolderIconPalette.fromId(properties.getProperty(PALETTE_KEY)?.trim()).name
            }
        }
    }
}

internal fun EmberkaiIconsSettings.State.isEnabled(category: IconRuleCategory): Boolean =
    EmberkaiIconsSettings.flag(category).get(this)

internal fun EmberkaiIconsSettings.State.setEnabled(category: IconRuleCategory, enabled: Boolean) {
    EmberkaiIconsSettings.flag(category).set(this, enabled)
}
