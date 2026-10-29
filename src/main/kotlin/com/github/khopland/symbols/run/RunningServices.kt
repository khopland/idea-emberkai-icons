package com.github.khopland.symbols.run

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Run and debug sessions that are live right now, keyed by the module folder that owns them. The
 * execution listener pushes sessions in as they start and out as they end, so icon lookup is a
 * single map read and never inspects run configurations itself.
 */
@Service(Service.Level.PROJECT)
internal class RunningServices {
    data class Session(
        val configurationName: String,
        val debug: Boolean,
        /** The HTTP port a Spring Boot service listens on, when its configuration names one. */
        val port: Int? = null,
    ) {
        /** Such as "debugging 'Orders' on port 8081". */
        fun describe(): String =
            (if (debug) "debugging" else "running") + " '$configurationName'" + (port?.let { " on port $it" } ?: "")
    }

    private class Entry(val directories: List<VirtualFile>, val session: Session)

    private val entries = LinkedHashMap<Any, Entry>()

    /** Rebuilt on every change, never mutated, so readers on any thread see a consistent snapshot. */
    @Volatile
    private var byDirectory: Map<VirtualFile, List<Session>> = emptyMap()

    fun sessionsFor(directory: VirtualFile): List<Session> = byDirectory[directory].orEmpty()

    /** Records the session behind [key], usually its process handler, and returns the folders to repaint. */
    fun start(key: Any, directories: List<VirtualFile>, session: Session): List<VirtualFile> = synchronized(entries) {
        if (directories.isEmpty()) return emptyList()
        val previous = entries.put(key, Entry(directories, session))
        rebuild()
        (directories + previous?.directories.orEmpty()).distinct()
    }

    /** Forgets the session behind [key] and returns the folders to repaint. */
    fun stop(key: Any): List<VirtualFile> = synchronized(entries) {
        val removed = entries.remove(key) ?: return emptyList()
        rebuild()
        removed.directories
    }

    fun clear(): List<VirtualFile> = synchronized(entries) {
        val directories = entries.values.flatMap { it.directories }.distinct()
        entries.clear()
        rebuild()
        directories
    }

    private fun rebuild() {
        val next = LinkedHashMap<VirtualFile, MutableList<Session>>()
        for (entry in entries.values) {
            for (directory in entry.directories) next.getOrPut(directory, ::ArrayList) += entry.session
        }
        byDirectory = next
    }

    companion object {
        fun getInstance(project: Project): RunningServices = project.getService(RunningServices::class.java)

        /** Avoids creating the service from icon lookup before any session started. */
        fun getIfCreated(project: Project): RunningServices? = project.getServiceIfCreated(RunningServices::class.java)
    }
}
