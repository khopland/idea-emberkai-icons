package com.github.khopland.symbols.lens

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.concurrency.CancellablePromise

/** The files a lens matched, plus every folder that contains one of them. */
internal class LensSnapshot(
    val lens: Lens,
    val memberCount: Int,
    private val highlighted: Set<VirtualFile>,
) {
    fun highlights(file: VirtualFile): Boolean = file in highlighted

    companion object {
        /** Walks the project's content once; call inside a read action. */
        fun collect(project: Project, lens: Lens): LensSnapshot {
            val highlighted = HashSet<VirtualFile>()
            var members = 0
            ProjectFileIndex.getInstance(project).iterateContent { file ->
                ProgressManager.checkCanceled()
                if (lens.contains(file, project)) {
                    members++
                    // Stop at the first ancestor another member already added.
                    var current: VirtualFile? = file
                    while (current != null && highlighted.add(current)) current = current.parent
                }
                true
            }
            return LensSnapshot(lens, members, highlighted)
        }
    }
}

/**
 * Holds the project's active lens. The lens is a view mode, not a setting, so it is not persisted
 * and every project starts without one. Membership is computed in the background when a lens is
 * selected, again after VFS changes in project content, and when a lens reports that its membership
 * changed; the Project view decorator only reads the finished snapshot, and dims nothing while the
 * first one is being computed or the lens is not ready.
 */
@Service(Service.Level.PROJECT)
internal class IconLensService(private val project: Project) : Disposable {
    // Coalesces bursts of VFS events, such as a branch switch, into one recomputation.
    private val recomputeAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    @Volatile
    var active: Lens? = null
        private set

    @Volatile
    var snapshot: LensSnapshot? = null
        private set

    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (active != null && events.any(::affectsContent)) {
                        recomputeAlarm.cancelAllRequests()
                        recomputeAlarm.addRequest({ recompute() }, RECOMPUTE_DELAY_MS)
                    }
                }
            },
        )
    }

    /** Must be called from the EDT. The returned promise completes once the Project view shows [lens]. */
    fun select(lens: Lens?): CancellablePromise<*>? {
        if (lens == active) return null
        active?.released(project)
        active = lens
        snapshot = null
        recomputeAlarm.cancelAllRequests()
        if (lens == null) {
            refreshProjectView()
            return null
        }
        return recompute()
    }

    private fun recompute(): CancellablePromise<*>? {
        val lens = active ?: return null
        return ReadAction.nonBlocking<LensSnapshot?> { if (lens.isReady(project)) LensSnapshot.collect(project, lens) else null }
            .expireWith(this)
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.any()) { result ->
                // Not ready yet: the lens calls membershipChanged once its state has loaded.
                if (result == null || active != result.lens) return@finishOnUiThread
                snapshot = result
                refreshProjectView()
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    /** Recomputes [lens] if it is still active, after state behind its membership changed. Any thread. */
    fun membershipChanged(lens: Lens) {
        if (active != lens) return
        recomputeAlarm.cancelAllRequests()
        recomputeAlarm.addRequest({ if (active == lens) recompute() }, 0)
    }

    private fun affectsContent(event: VFileEvent): Boolean {
        if (project.isDisposed) return false
        // Deleted files are already gone from the index; treat them as relevant.
        val file = event.file?.takeIf { it.isValid } ?: return true
        return ProjectFileIndex.getInstance(project).isInContent(file)
    }

    private fun refreshProjectView() {
        if (!project.isDisposed) ProjectView.getInstance(project).refresh()
    }

    override fun dispose() = Unit

    companion object {
        private const val RECOMPUTE_DELAY_MS = 500

        fun getInstance(project: Project): IconLensService = project.getService(IconLensService::class.java)

        /** Avoids creating the service from Project view decoration before any lens was chosen. */
        fun getIfCreated(project: Project): IconLensService? =
            project.getServiceIfCreated(IconLensService::class.java)
    }
}
