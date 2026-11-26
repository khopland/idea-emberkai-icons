package com.github.khopland.emberkai.lens

import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.concurrency.CancellablePromise

/**
 * Follows the IDE's test runner. Each test is keyed by its location URL, such as
 * `java:test://com.shop.OrderTest/total`, so a test that fails and later passes, in this run or a
 * later one, leaves the lens again, and a test that was not run again keeps its last result.
 * Ignored and skipped tests count as not failing; a stopped run leaves results as they were.
 *
 * Finding the file a failed test lives in resolves PSI, so failures are located in the background,
 * in batches, once indexes are ready.
 */
internal class SmTestFailures(private val project: Project) : TestFailures, Disposable {
    private val lock = Any()

    /** Every failing test's location URL, and the file it is in once located. */
    private val failing = HashMap<String, VirtualFile?>()

    /** Failed tests waiting to be located. */
    private val unlocated = LinkedHashMap<String, SMTestProxy>()

    @Volatile
    private var files: Set<VirtualFile> = emptySet()

    override fun files(): Set<VirtualFile> = files

    /** Records [test]'s result once it finishes; any thread. */
    fun finished(test: SMTestProxy) {
        val url = test.locationUrl ?: return
        // Stopped and ignored tests also count as defects, so they are asked about first.
        when {
            // Stopped: nothing new is known about the test.
            test.isInterrupted -> Unit
            // Passed, skipped, or ignored.
            test.isPassed || test.isIgnored -> {
                val changed = synchronized(lock) {
                    unlocated.remove(url)
                    val located = failing.containsKey(url) && failing.remove(url) != null
                    if (located) files = failing.values.filterNotNullTo(HashSet())
                    located
                }
                if (changed) changed()
            }
            // Failed, or errored.
            test.isDefect -> {
                synchronized(lock) {
                    // Already located by an earlier failure: the file has not moved.
                    if (failing[url] != null) return
                    failing[url] = null
                    unlocated[url] = test
                }
                locate()
            }
            // Running, or not run.
            else -> Unit
        }
    }

    /** Locates the failed tests that are still waiting, in one background read action. */
    private fun locate(): CancellablePromise<*> =
        ReadAction.nonBlocking<Map<String, VirtualFile?>> {
            val batch = synchronized(lock) { unlocated.toMap() }
            val scope = GlobalSearchScope.allScope(project)
            batch.mapValues { (_, test) -> test.getLocation(project, scope)?.virtualFile }
        }
            .inSmartMode(project)
            .expireWith(this)
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.any()) { located -> record(located) }
            .submit(AppExecutorUtil.getAppExecutorService())

    private fun record(located: Map<String, VirtualFile?>) {
        val changed = synchronized(lock) {
            var changed = false
            for ((url, file) in located) {
                unlocated.remove(url)
                // The test passed while it was being located, or lives nowhere the project can show.
                if (!failing.containsKey(url)) continue
                if (file == null) failing.remove(url) else if (failing.put(url, file) != file) changed = true
            }
            if (changed) files = failing.values.filterNotNullTo(HashSet())
            changed
        }
        if (changed) changed()
    }

    private fun changed() {
        if (project.isDisposed) return
        IconLensService.getIfCreated(project)?.membershipChanged(FailingTestsLens)
    }

    override fun dispose() = Unit
}

/** Forwards the test runner's results to [SmTestFailures]. */
internal class TestFailuresListener(private val project: Project) : SMTRunnerEventsAdapter() {
    override fun onTestFinished(test: SMTestProxy) {
        (TestFailures.getInstance(project) as? SmTestFailures)?.finished(test)
    }

    override fun onTestIgnored(test: SMTestProxy) {
        (TestFailures.getInstance(project) as? SmTestFailures)?.finished(test)
    }
}
