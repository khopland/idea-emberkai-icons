package com.github.khopland.emberkai.lens

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** How lenses pick their files. The active lens itself is a view mode and is not stored. */
@Service(Service.Level.APP)
@State(name = "EmberkaiLensSettings", storages = [Storage("emberkai-icons.xml")])
class EmberkaiLensSettings : PersistentStateComponent<EmberkaiLensSettings.State> {
    /** Field names are the persisted setting names; keep them stable. */
    data class State(
        var hotspotDays: Int = Hotspots.DEFAULT_DAYS,
        var hotspotLimit: Int = Hotspots.DEFAULT_LIMIT,
        var hotspotMinimumCommits: Int = Hotspots.DEFAULT_MINIMUM_COMMITS,
    )

    /** Replaced, never mutated, so readers on any thread see a consistent snapshot. */
    @Volatile
    private var currentState = State()

    override fun getState(): State = currentState.copy()

    override fun loadState(state: State) {
        currentState = state.copy()
    }

    // A hand-edited settings file cannot push values outside what the settings page allows.
    var hotspotDays: Int
        get() = currentState.hotspotDays.coerceIn(DAYS_RANGE)
        set(value) = update { it.hotspotDays = value.coerceIn(DAYS_RANGE) }

    var hotspotLimit: Int
        get() = currentState.hotspotLimit.coerceIn(LIMIT_RANGE)
        set(value) = update { it.hotspotLimit = value.coerceIn(LIMIT_RANGE) }

    var hotspotMinimumCommits: Int
        get() = currentState.hotspotMinimumCommits.coerceIn(MINIMUM_COMMITS_RANGE)
        set(value) = update { it.hotspotMinimumCommits = value.coerceIn(MINIMUM_COMMITS_RANGE) }

    private fun update(change: (State) -> Unit) {
        synchronized(this) {
            currentState = currentState.copy().also(change)
        }
    }

    companion object {
        val DAYS_RANGE = 1..3650
        val LIMIT_RANGE = 1..1000
        val MINIMUM_COMMITS_RANGE = 1..100

        fun getInstance(): EmberkaiLensSettings =
            ApplicationManager.getApplication().getService(EmberkaiLensSettings::class.java)
    }
}
