package com.github.khopland.symbols

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Answers from the change list manager's cached ignore state; icon lookups never start VCS work.
 * Until the first change list update finishes, ignore status is unknown and no badge is shown.
 * Secret-looking files seen by the icon provider are rechecked after VCS updates, at most once per
 * [RECHECK_DELAY_MS], and icons refresh only when one of their badges changes.
 */
internal class VcsSecretFileExposure(private val project: Project) : SecretFileExposure, Disposable {
    private val shownBadges = ConcurrentHashMap<VirtualFile, Boolean>()
    private val recheckScheduled = AtomicBoolean()

    @Volatile
    private var statusKnown = false

    init {
        val connection = project.messageBus.connect(this)
        // Ignored and unversioned holders finish after the change list update and report as status changes.
        connection.subscribe(ChangeListListener.TOPIC, object : ChangeListListener {
            override fun changeListUpdateDone() = scheduleRecheck()
            override fun unchangedFileStatusChanged(upToDateFilesChanged: Boolean) = scheduleRecheck()
            override fun changedFileStatusChanged(upToDateFilesChanged: Boolean) = scheduleRecheck()
        })
        connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsMappingListener { scheduleRecheck() })
        changeListManager().invokeAfterUpdate(false) {
            statusKnown = true
            scheduleRecheck()
        }
    }

    override fun isExposed(file: VirtualFile): Boolean {
        if (project.isDisposed || !file.isValid) return false
        val exposed = statusKnown && isInVcsAndNotIgnored(file)
        if (shownBadges.size < MAX_TRACKED_FILES || shownBadges.containsKey(file)) shownBadges[file] = exposed
        return exposed
    }

    override fun dispose() {
        shownBadges.clear()
    }

    private fun isInVcsAndNotIgnored(file: VirtualFile): Boolean =
        project.getService(ProjectLevelVcsManager::class.java).getVcsRootFor(file) != null &&
            !changeListManager().isIgnoredFile(file)

    // getService instead of getInstance: newer platforms moved getInstance to a Kotlin companion that
    // older IDEs lack, so calling it compiles to bytecode that fails there.
    private fun changeListManager(): ChangeListManager = project.getService(ChangeListManager::class.java)

    private fun scheduleRecheck() {
        if (project.isDisposed || shownBadges.isEmpty() || !recheckScheduled.compareAndSet(false, true)) return
        AppExecutorUtil.getAppScheduledExecutorService().schedule(
            {
                recheckScheduled.set(false)
                recheck()
            },
            RECHECK_DELAY_MS,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun recheck() {
        if (project.isDisposed) return
        var changed = false
        for ((file, shown) in shownBadges) {
            if (!file.isValid) {
                shownBadges.remove(file)
                continue
            }
            val exposed = statusKnown && isInVcsAndNotIgnored(file)
            if (exposed != shown) {
                shownBadges[file] = exposed
                changed = true
            }
        }
        if (changed) {
            ApplicationManager.getApplication().invokeLater(
                { RefreshProjectIconPresentations.refresh() },
                project.disposed,
            )
        }
    }

    private companion object {
        const val RECHECK_DELAY_MS = 500L
        const val MAX_TRACKED_FILES = 1_000
    }
}
