package com.github.khopland.symbols.overrides

import com.github.khopland.symbols.core.MappingKeys
import com.github.khopland.symbols.core.ProjectPathUtil
import com.github.khopland.symbols.core.RefreshProjectIconPresentations
import com.github.khopland.symbols.core.SymbolsIconProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.Alarm
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URLClassLoader
import java.util.Locale
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

/** A project-local icon mapping loaded from `.idea/emberkai-icons.properties`. */
internal data class ProjectIconOverride(
    val key: String,
    val icon: Icon,
    val resourcePath: String,
)

/**
 * Project-scoped overrides live next to the project's other `.idea` configuration so a team can
 * share them through version control. Lookups only read an in-memory snapshot; VFS events and
 * unsaved edits to the properties document invalidate it and repaint the project's icons.
 */
@Service(Service.Level.PROJECT)
internal class ProjectIconOverrideStore(private val project: Project) : Disposable {
    private val log = Logger.getInstance(ProjectIconOverrideStore::class.java)
    private val lock = Any()
    // Coalesces bursts such as typing in the overrides file into one Project view refresh.
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    @Volatile
    private var cached: Snapshot? = null

    /** Shared by snapshots of one generation; detached from IconLoader's cache on every change. */
    private var artworkLoader: URLClassLoader? = null

    @Volatile
    private var generation = 0L

    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any(::affectsOverrides)) changed()
                }
            },
        )
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                    if (isPropertiesPath(file.path)) changed()
                }
            },
            this,
        )
    }

    fun propertiesFile(): VirtualFile? =
        propertiesPath()?.let(LocalFileSystem.getInstance()::findFileByPath)?.takeIf { it.isValid && !it.isDirectory }

    fun iconsDirectory(): VirtualFile? = iconsPath()?.let(LocalFileSystem.getInstance()::findFileByPath)

    fun overrides(): Map<String, String> = snapshot().values

    /** Refreshes `.idea` from disk, then repaints this project's icons. */
    fun reload() {
        val ideaDirectory = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath("$it/$IDEA_DIRECTORY") }
        // Files created outside the IDE only become visible after a refresh; its VFS events reload again.
        if (ideaDirectory != null) VfsUtil.markDirtyAndRefresh(true, true, true, ideaDirectory)
        changed()
    }

    /** Must be called from the EDT; creates `.idea/emberkai-icons.properties` when missing. */
    fun ensurePropertiesFile(): VirtualFile? {
        val base = project.basePath ?: return null
        return try {
            WriteAction.compute<VirtualFile, IOException> {
                val directory = VfsUtil.createDirectoryIfMissing("$base/$IDEA_DIRECTORY")
                    ?: throw IOException("Could not create $base/$IDEA_DIRECTORY")
                val existing = directory.findChild(PROPERTIES_NAME)
                if (existing?.isDirectory == true) throw IOException("${existing.path} is a directory")
                existing ?: directory.createChildData(this, PROPERTIES_NAME).also {
                    it.setBinaryContent(INITIAL_FILE.toByteArray(Charsets.UTF_8))
                }
            }
        } catch (exception: IOException) {
            log.warn("Could not create project icon overrides file", exception)
            null
        }
    }

    /** Must be called from the EDT. */
    fun ensureIconsDirectory(): VirtualFile? {
        val path = iconsPath() ?: return null
        return try {
            WriteAction.compute<VirtualFile, IOException> {
                VfsUtil.createDirectoryIfMissing(path) ?: throw IOException("Could not create $path")
            }
        } catch (exception: IOException) {
            log.warn("Could not create project icon overrides directory", exception)
            null
        }
    }

    /** Must be called from the EDT. Returns false when the overrides file could not be written. */
    fun putOverride(key: String, iconPath: String): Boolean {
        if (!ProjectIconOverrideFile.isSupportedKey(key) || iconPath.isBlank()) return false
        return updateEntry(key, iconPath.trim())
    }

    /** Must be called from the EDT. */
    fun removeOverride(key: String): Boolean {
        if (ProjectIconOverrideFile.normalizeKey(key) !in overrides()) return true
        return updateEntry(key, null)
    }

    /**
     * Copies [source] and its `_dark` sibling, if any, into `.idea/emberkai-icons/` and returns the
     * relative path. Artwork already in that folder is referenced in place; a different file with
     * the same name gets a numbered copy.
     */
    fun importArtwork(source: VirtualFile): String? {
        iconsDirectory()?.let { directory ->
            VfsUtilCore.getRelativePath(source, directory, '/')?.let { return it }
        }
        val directory = ensureIconsDirectory() ?: return null
        return try {
            val bytes = source.contentsToByteArray()
            val extension = source.extension?.let { ".$it" }.orEmpty()
            val darkBytes = source.parent?.findChild("${source.nameWithoutExtension}_dark$extension")
                ?.takeIf { !it.isDirectory }?.contentsToByteArray()
            WriteAction.compute<String, IOException> {
                val baseName = source.nameWithoutExtension.ifBlank { "custom" }
                var name = baseName
                var suffix = 2
                while (true) {
                    val existing = directory.findChild(name + extension) ?: break
                    if (!existing.isDirectory && existing.contentsToByteArray().contentEquals(bytes)) break
                    name = "$baseName-${suffix++}"
                }
                val target = directory.findChild(name + extension) ?: directory.createChildData(this, name + extension)
                target.setBinaryContent(bytes)
                if (darkBytes != null) {
                    val dark = "${name}_dark$extension"
                    (directory.findChild(dark) ?: directory.createChildData(this, dark)).setBinaryContent(darkBytes)
                }
                name + extension
            }
        } catch (exception: IOException) {
            log.warn("Could not copy icon override artwork ${source.path}", exception)
            null
        }
    }

    fun fileOverride(file: VirtualFile, name: String): ProjectIconOverride? {
        val snapshot = snapshot()
        if (snapshot.values.isEmpty()) return null
        val lowerName = name.lowercase(Locale.ROOT)
        val parent = file.parent.takeIf { snapshot.hasContextualKeys }
        val lookup = { key: String -> match(snapshot, key) }
        return MappingKeys.contextual(parent, "file.", lowerName, snapshot.contextDepth("file."), lookup)
            ?: lookup("file.$lowerName")
            ?: MappingKeys.contextualExtension(parent, lowerName, snapshot.contextDepth("extension."), lookup)
            ?: MappingKeys.extension(lowerName, lookup)
    }

    fun folderOverride(directory: VirtualFile, name: String): ProjectIconOverride? {
        val snapshot = snapshot()
        if (snapshot.values.isEmpty()) return null
        val lowerName = name.lowercase(Locale.ROOT)
        val parent = directory.parent.takeIf { snapshot.hasContextualKeys }
        return MappingKeys.contextual(parent, "folder.", lowerName, snapshot.contextDepth("folder.")) {
            match(snapshot, it)
        } ?: match(snapshot, "folder.$lowerName")
    }

    override fun dispose() {
        synchronized(lock) {
            cached = null
            detachArtworkLoader()
        }
    }

    private fun match(snapshot: Snapshot, key: String): ProjectIconOverride? {
        val value = snapshot.values[key] ?: return null
        val artwork = snapshot.artwork(value) ?: return null
        return ProjectIconOverride(key, artwork.icon, artwork.resourcePath)
    }

    private fun snapshot(): Snapshot {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val loadedGeneration = generation
            val snapshot = load()
            // A change that arrived while loading must not be masked by the stale result.
            if (loadedGeneration == generation) cached = snapshot
            return snapshot
        }
    }

    private fun load(): Snapshot {
        val file = propertiesFile()
            ?: return Snapshot(emptyMap(), iconsDirectory = null)
        val text = try {
            FileDocumentManager.getInstance().getCachedDocument(file)?.immutableCharSequence?.toString()
                ?: VfsUtilCore.loadText(file)
        } catch (exception: IOException) {
            log.warn("Could not read project icon overrides", exception)
            return Snapshot(emptyMap(), iconsDirectory = null)
        }
        return Snapshot(ProjectIconOverrideFile.parse(text), iconsPath())
    }

    private fun changed() {
        synchronized(lock) {
            generation++
            cached = null
            detachArtworkLoader()
        }
        if (project.isDisposed || refreshAlarm.isDisposed) return
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({ RefreshProjectIconPresentations.refresh(project) }, REFRESH_DELAY_MS)
    }

    private fun detachArtworkLoader() {
        artworkLoader?.let { IconLoader.detachClassLoader(it) }
        artworkLoader = null
    }

    /** Local SVGs load through a class loader so IconLoader resolves `_dark` variants and HiDPI images. */
    private fun artworkLoader(directory: String): ClassLoader? = synchronized(lock) {
        artworkLoader?.let { return it }
        try {
            URLClassLoader(arrayOf(File(directory).toURI().toURL()), null).also { artworkLoader = it }
        } catch (exception: MalformedURLException) {
            log.warn("Could not create icon override class loader", exception)
            null
        }
    }

    private fun updateEntry(key: String, value: String?): Boolean {
        val file = ensurePropertiesFile() ?: return false
        val documents = FileDocumentManager.getInstance()
        val document = documents.getDocument(file)
        try {
            if (document != null) {
                val updated = ProjectIconOverrideFile.update(document.text, key, value)
                WriteCommandAction.runWriteCommandAction(project, "Update Icon Override", null, {
                    document.setText(updated)
                })
                documents.saveDocument(document)
            } else {
                val updated = ProjectIconOverrideFile.update(VfsUtilCore.loadText(file), key, value)
                WriteAction.run<IOException> { file.setBinaryContent(updated.toByteArray(file.charset)) }
            }
        } catch (exception: IOException) {
            log.warn("Could not save project icon overrides", exception)
            return false
        }
        changed()
        return true
    }

    private fun basePaths(): List<String> {
        val base = project.basePath ?: return emptyList()
        val independent = FileUtil.toSystemIndependentName(base)
        return listOf(independent, ProjectPathUtil.basePath(project) ?: independent).distinct()
    }

    private fun propertiesPath(): String? = project.basePath?.let { "$it/$IDEA_DIRECTORY/$PROPERTIES_NAME" }

    private fun iconsPath(): String? = project.basePath?.let { "$it/$IDEA_DIRECTORY/$ICONS_DIRECTORY" }

    private fun isPropertiesPath(path: String): Boolean {
        val normalized = FileUtil.toSystemIndependentName(path)
        return basePaths().any { FileUtil.pathsEqual(normalized, "$it/$IDEA_DIRECTORY/$PROPERTIES_NAME") }
    }

    private fun affectsOverrides(event: VFileEvent): Boolean {
        val paths = buildList {
            add(event.path)
            when (event) {
                is VFileMoveEvent -> add(event.oldPath)
                is VFilePropertyChangeEvent -> add(event.oldPath)
            }
        }
        return paths.any(::affectsOverridePath)
    }

    private fun affectsOverridePath(path: String): Boolean {
        val normalized = FileUtil.toSystemIndependentName(path)
        return basePaths().any { base ->
            val properties = "$base/$IDEA_DIRECTORY/$PROPERTIES_NAME"
            val icons = "$base/$IDEA_DIRECTORY/$ICONS_DIRECTORY"
            FileUtil.pathsEqual(normalized, properties) ||
                FileUtil.isAncestor(icons, normalized, false) ||
                // Creating, deleting, or renaming `.idea` itself.
                FileUtil.isAncestor(normalized, properties, true)
        }
    }

    /**
     * Parsed values plus lazily loaded artwork. Missing, unreadable, or out-of-folder artwork resolves
     * to null so lookups fall through to bundled mappings instead of failing.
     */
    private inner class Snapshot(val values: Map<String, String>, private val iconsDirectory: String?) {
        val hasContextualKeys = values.keys.any { '/' in it }

        /** Deepest key per prefix, so a lookup stops walking parents where no override can match. */
        private val contextDepths: Map<String, Int> = values.keys
            .groupingBy { it.substringBefore('.') + "." }
            .fold(0) { depth, key -> maxOf(depth, key.count { it == '/' }) }

        fun contextDepth(prefix: String): Int = contextDepths[prefix] ?: 0

        private val artwork = ConcurrentHashMap<String, Optional<Artwork>>()

        fun artwork(value: String): Artwork? = artwork.computeIfAbsent(value) {
            val loaded = try {
                loadArtwork(value)
            } catch (exception: RuntimeException) {
                log.warn("Could not load icon override $value", exception)
                null
            }
            Optional.ofNullable(loaded)
        }.orElse(null)

        private fun loadArtwork(value: String): Artwork? {
            if (ProjectIconOverrideFile.isBundledPath(value)) {
                val path = value.trim()
                if (!ProjectIconOverrideFile.isBundledArtwork(path)) return null
                // Same cached icon instance as the bundled mapping that references this path.
                return Artwork(IconLoader.getIcon(path, SymbolsIconProvider::class.java), path)
            }
            val relative = ProjectIconOverrideFile.relativeArtworkPath(value) ?: return null
            val directoryPath = iconsDirectory ?: return null
            val directory = LocalFileSystem.getInstance().findFileByPath(directoryPath) ?: return null
            val file = directory.findFileByRelativePath(relative)?.takeIf { it.isValid && !it.isDirectory }
                ?: return null
            // Reject symlinks that resolve outside the overrides directory.
            val canonicalDirectory = directory.canonicalPath ?: directory.path
            val canonicalFile = file.canonicalPath ?: file.path
            if (!FileUtil.isAncestor(canonicalDirectory, canonicalFile, true)) return null
            val loader = artworkLoader(directoryPath) ?: return null
            val icon = IconLoader.findIcon("/$relative", loader) ?: return null
            return Artwork(icon, file.path)
        }
    }

    private data class Artwork(val icon: Icon, val resourcePath: String)

    internal companion object {
        const val PROPERTIES_NAME = "emberkai-icons.properties"
        const val ICONS_DIRECTORY = "emberkai-icons"
        private const val IDEA_DIRECTORY = ".idea"
        private const val REFRESH_DELAY_MS = 300
        private const val INITIAL_FILE =
            "# Emberkai Icons project overrides\n" +
                "# Keys use the same format as the bundled icons.properties file:\n" +
                "#   file.<name>, extension.<ext>, folder.<name>, optionally prefixed with parent folders\n" +
                "#   such as file.deploy/prod/values.yaml.\n" +
                "# Values may be a bundled resource path such as /symbols/icons/files/github.svg\n" +
                "# or a path relative to .idea/emberkai-icons/ such as my-icon.svg.\n" +
                "# Add my-icon_dark.svg next to an SVG to provide a dark-theme variant.\n"

        fun getInstance(project: Project): ProjectIconOverrideStore =
            project.getService(ProjectIconOverrideStore::class.java)
    }
}
