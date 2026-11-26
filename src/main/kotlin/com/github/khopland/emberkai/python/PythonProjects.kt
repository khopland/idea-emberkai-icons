package com.github.khopland.emberkai.python

import com.github.khopland.emberkai.core.FileContentCache
import com.github.khopland.emberkai.core.RefreshProjectIconPresentations
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** The frameworks whose roles Emberkai recognizes in Python sources, by the distribution that brings them. */
internal enum class PythonFramework(vararg val distributions: String) {
    FASTAPI("fastapi"),
    FLASK("flask"),
    DJANGO("django", "djangorestframework"),
    CELERY("celery"),
    DRAMATIQ("dramatiq"),
    FASTSTREAM("faststream"),
    APSCHEDULER("apscheduler"),
    SQLALCHEMY("sqlalchemy", "sqlmodel", "flask-sqlalchemy"),
    PYDANTIC_SETTINGS("pydantic-settings"),
    TESTCONTAINERS("testcontainers"),
}

/** A folder with a Python project manifest, and the recognized frameworks it names. */
internal class PythonProject(val root: VirtualFile, val frameworks: Set<PythonFramework>)

/**
 * Finds the project manifest that owns a Python file: the nearest folder, up to the file's content
 * root, with a `pyproject.toml`, `setup.py`, `setup.cfg`, `Pipfile`, `requirements.txt`,
 * `requirements.in`, or a Django `manage.py`. Every manifest in that folder is searched for the
 * distribution names of [PythonFramework]; dependency tables differ between tools, but a name
 * written in any of them is enough to decide whether the sources are worth reading.
 *
 * Results are cached per manifest modification stamp. Where loading from disk is a slow operation
 * (see [FileContentCache.mayLoad]), only folders and manifests already loaded are used, as for
 * Node packages.
 */
internal object PythonProjects {
    val MANIFESTS = setOf("pyproject.toml", "setup.py", "setup.cfg", "Pipfile", "requirements.txt", "requirements.in", "manage.py")

    private const val MAX_DEPTH = 24
    private val FRAMEWORKS = Key.create<FileContentCache.Inspection<Set<PythonFramework>>>("emberkai.pythonFrameworks")

    /**
     * Words that may be distribution names, with `-` and `_` interchangeable, as pip treats them. Dots
     * split words, so `from django.core.management import …` in `manage.py` names Django too.
     */
    private val DISTRIBUTION = Regex("""[\w-]+""")
    private val BY_DISTRIBUTION: Map<String, PythonFramework> =
        PythonFramework.entries.flatMap { framework -> framework.distributions.map { it to framework } }.toMap()

    /** Installed packages and virtual environments never decide a badge. */
    private val INSTALLED = listOf("/site-packages/", "/dist-packages/", "/.venv/", "/.tox/", "/node_modules/")

    fun nearest(file: VirtualFile, project: Project): PythonProject? {
        val load = FileContentCache.mayLoad()
        for (directory in ancestors(file, project)) {
            val manifests = MANIFESTS.mapNotNull { child(directory, it, load) }
            if (manifests.isEmpty()) continue
            val frameworks = HashSet<PythonFramework>()
            for (manifest in manifests) {
                val found = if (load) frameworks(manifest) else FileContentCache.peek(manifest, FRAMEWORKS)?.value ?: return null
                frameworks += found
            }
            return PythonProject(directory, frameworks)
        }
        return null
    }

    fun isInstalled(file: VirtualFile): Boolean = isInstalled(file.path)

    fun isInstalled(path: String): Boolean = INSTALLED.any { it in path }

    internal fun frameworksIn(text: String): Set<PythonFramework> =
        DISTRIBUTION.findAll(text).mapNotNullTo(HashSet()) { match ->
            BY_DISTRIBUTION[match.value.lowercase(Locale.ROOT).replace('_', '-')]
        }

    private fun frameworks(manifest: VirtualFile): Set<PythonFramework> =
        FileContentCache.inspect(manifest, FRAMEWORKS, emptySet(), ::frameworksIn)

    private fun child(directory: VirtualFile, name: String, load: Boolean): VirtualFile? {
        val child = if (load) directory.findChild(name) else (directory as? NewVirtualFile)?.findChildIfCached(name)
        return child?.takeUnless { it.isDirectory }
    }

    /** [file]'s folders, innermost first, up to its content root; none for installed packages. */
    private fun ancestors(file: VirtualFile, project: Project): Sequence<VirtualFile> {
        if (isInstalled(file)) return emptySequence()
        val contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file) ?: return emptySequence()
        val start = if (file.isDirectory) file else file.parent ?: return emptySequence()
        return generateSequence(start) { directory -> directory.takeUnless { it == contentRoot }?.parent }.take(MAX_DEPTH)
    }
}

/**
 * Repaints icons when a Python project manifest changes, appears, or goes away: whether a source
 * file is read for its role depends on the frameworks its project names.
 */
internal class PythonProjectListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        // A dependency sync can rewrite manifests in many batches; repaint once per burst.
        if (isRelevant(events) && refreshPending.compareAndSet(false, true)) {
            ApplicationManager.getApplication().invokeLater {
                refreshPending.set(false)
                RefreshProjectIconPresentations.refresh()
            }
        }
    }

    internal fun isRelevant(events: List<VFileEvent>): Boolean = events.any { event ->
        event.path.substringAfterLast('/') in PythonProjects.MANIFESTS && !PythonProjects.isInstalled(event.path)
    }

    private companion object {
        val refreshPending = AtomicBoolean(false)
    }
}
