package com.github.khopland.symbols

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile

/** A module name and the accent color assigned to it. */
internal data class ModuleAccent(
    val moduleName: String,
    val color: ModuleAccentColor,
)

/**
 * Project-level accent overrides. Automatic colors come from the module name; users can pin a
 * different color per module, and the overrides travel with the project's `.idea` directory.
 */
@Service(Service.Level.PROJECT)
@State(name = "EmberkaiModuleAccents", storages = [Storage("emberkai-module-accents.xml")])
internal class ModuleAccentStore(private val project: Project) : PersistentStateComponent<ModuleAccentStore.State> {
    class State {
        var overrides: MutableMap<String, String> = linkedMapOf()
    }

    @Volatile
    private var currentState = State()
    private val stateLock = Any()

    override fun getState(): State = synchronized(stateLock) { currentState.copyState() }

    override fun loadState(state: State) {
        synchronized(stateLock) {
            currentState = state.copyState()
        }
    }

    /** Resolve the accent for any directory that belongs to an IntelliJ module. */
    fun accentFor(directory: VirtualFile, palette: FolderIconPalette): ModuleAccent? {
        if (project.isDisposed) return null
        val module = ProjectFileIndex.getInstance(project).getModuleForFile(directory) ?: return null
        return ModuleAccent(module.name, colorFor(module.name, palette))
    }

    fun colorFor(moduleName: String, palette: FolderIconPalette): ModuleAccentColor =
        overrideFor(moduleName) ?: ModuleAccentColor.defaultFor(moduleName, palette)

    fun overrideFor(moduleName: String): ModuleAccentColor? =
        synchronized(stateLock) { currentState.overrides[moduleName] }.let(ModuleAccentColor::fromId)

    fun setOverride(moduleName: String, color: ModuleAccentColor) {
        updateState { it.overrides[moduleName] = color.id }
    }

    fun clearOverride(moduleName: String) {
        updateState { it.overrides.remove(moduleName) }
    }

    fun clearAll() {
        updateState { it.overrides.clear() }
    }

    fun hasOverrides(): Boolean = synchronized(stateLock) { currentState.overrides.isNotEmpty() }

    private fun updateState(update: (State) -> Unit) {
        synchronized(stateLock) {
            val newState = currentState.copyState()
            update(newState)
            currentState = newState
        }
    }

    private fun State.copyState(): State = State().also { copy ->
        copy.overrides = LinkedHashMap(overrides)
    }

    companion object {
        internal fun getInstance(project: Project): ModuleAccentStore =
            project.getService(ModuleAccentStore::class.java)
    }
}
