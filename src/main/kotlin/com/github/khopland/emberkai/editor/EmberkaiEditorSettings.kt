package com.github.khopland.emberkai.editor

import com.github.khopland.emberkai.settings.EmberkaiIconsSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty

/**
 * What Emberkai adds to open editors. Kept apart from [EmberkaiIconsSettings] because it has its own
 * settings page, and presets on the icons page do not change it.
 */
@Service(Service.Level.APP)
@State(name = "EmberkaiEditorSettings", storages = [Storage("emberkai-icons.xml")])
class EmberkaiEditorSettings : PersistentStateComponent<EmberkaiEditorSettings.State> {
    /** Field names are the persisted setting names; keep them stable. */
    data class State(
        var productionBanner: Boolean = true,
        var productionTabTint: Boolean = true,
    )

    /** Replaced, never mutated, so readers on any thread see a consistent snapshot. */
    @Volatile
    private var currentState = State()

    override fun getState(): State = currentState.copy()

    override fun loadState(state: State) {
        currentState = state.copy()
    }

    /**
     * Both warnings used to share the icons page's Production editor warnings switch. Someone who
     * turned that off keeps both off; the old value is then cleared so it is read only once.
     */
    override fun noStateLoaded() {
        val icons = EmberkaiIconsSettings.getInstance()
        if (icons.state.productionEditorWarnings) return
        currentState = State(productionBanner = false, productionTabTint = false)
        icons.clearLegacyProductionEditorWarnings()
    }

    var productionBanner by flag(State::productionBanner)
    var productionTabTint by flag(State::productionTabTint)

    private fun flag(field: KMutableProperty1<State, Boolean>) =
        object : ReadWriteProperty<EmberkaiEditorSettings, Boolean> {
            override fun getValue(thisRef: EmberkaiEditorSettings, property: KProperty<*>): Boolean =
                field.get(currentState)

            override fun setValue(thisRef: EmberkaiEditorSettings, property: KProperty<*>, value: Boolean) {
                synchronized(this@EmberkaiEditorSettings) {
                    currentState = currentState.copy().also { field.set(it, value) }
                }
            }
        }

    companion object {
        fun getInstance(): EmberkaiEditorSettings =
            ApplicationManager.getApplication().getService(EmberkaiEditorSettings::class.java)
    }
}
