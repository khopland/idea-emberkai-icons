package com.github.khopland.symbols.lens

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lens membership that comes from Git. Git runs on a background thread, and only while [lens] is
 * active: subclasses call [scheduleRefresh] when repository state changes, at most once per
 * [REFRESH_DELAY_MS], and the lens is recomputed only when the set of files differs. Turning the
 * lens off forgets the result, so selecting it again starts fresh instead of showing a stale one.
 */
internal abstract class GitLensFiles(
    protected val project: Project,
    private val lens: Lens,
    threadName: String,
) : Disposable {
    private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(threadName, 1)
    private val refreshScheduled = AtomicBoolean()

    @Volatile
    private var files: Set<VirtualFile>? = null

    /** The files [compute] found, or null until the first run finishes, and starts one if needed. */
    fun files(): Set<VirtualFile>? = files.also { if (it == null) scheduleRefresh() }

    fun forget() {
        files = null
    }

    override fun dispose() = forget()

    /** Runs Git on a background thread; never called from the EDT or a read action. */
    protected abstract fun compute(): Set<VirtualFile>

    protected fun scheduleRefresh() {
        if (project.isDisposed || !refreshScheduled.compareAndSet(false, true)) return
        AppExecutorUtil.getAppScheduledExecutorService().schedule(
            {
                refreshScheduled.set(false)
                executor.execute(::refresh)
            },
            REFRESH_DELAY_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun refresh() {
        if (project.isDisposed) return
        if (IconLensService.getIfCreated(project)?.active != lens) {
            files = null
            return
        }
        val computed = compute()
        if (project.isDisposed || computed == files) return
        files = computed
        IconLensService.getIfCreated(project)?.membershipChanged(lens)
    }

    private companion object {
        const val REFRESH_DELAY_MS = 300L
    }
}
