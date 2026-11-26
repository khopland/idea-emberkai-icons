package com.github.khopland.emberkai.lens

import com.github.khopland.emberkai.IconProviderTestCase
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.registerOrReplaceServiceInstance

internal class FailingTestsLensTest : IconProviderTestCase() {
    private val files = HashMap<String, PsiFile>()

    override fun tearDown() {
        try {
            IconLensService.getIfCreated(project)?.select(null)
        } finally {
            super.tearDown()
        }
    }

    fun testTrackerIsRegisteredAndTheLensIsOffered() {
        assertTrue(TestFailures.getInstance(project) is SmTestFailures)
        freshTracker()
        assertTrue(lensMenu().contains(FailingTestsLens.displayName))
    }

    fun testFailedTestsJoinTheLensUntilTheyPass() {
        val order = test("OrderTest.kt")
        val cart = test("CartTest.kt")
        val failures = freshTracker()

        finish(failed("test://OrderTest/total"))
        finish(failed("test://OrderTest/tax"))
        finish(failed("test://CartTest/add"))
        awaitFiles(setOf(order.virtualFile, cart.virtualFile))

        // One of two failing tests passes: the file still has a failing test.
        finish(passed("test://OrderTest/total"))
        assertEquals(setOf(order.virtualFile, cart.virtualFile), failures.files())

        finish(passed("test://OrderTest/tax"))
        assertEquals(setOf(cart.virtualFile), failures.files())

        // Ignored and skipped tests do not count as failing.
        finish(ignored("test://CartTest/add"))
        assertEquals(emptySet<Any>(), failures.files())
    }

    fun testStoppedRunsAndUnknownLocationsChangeNothing() {
        val order = test("OrderTest.kt")
        val failures = freshTracker()

        finish(failed("test://OrderTest/total"))
        finish(failed("test://Elsewhere/gone"))
        awaitFiles(setOf(order.virtualFile))

        val running = proxy("test://OrderTest/total").apply { setStarted() }
        finish(running)
        assertEquals(setOf(order.virtualFile), failures.files())
    }

    fun testTheLensFollowsFailures() {
        val order = test("OrderTest.kt")
        val readme = myFixture.addFileToProject("README.md", "# readme").virtualFile
        freshTracker()
        val service = IconLensService.getInstance(project)

        PlatformTestUtil.waitForPromise(service.select(FailingTestsLens)!!)
        assertEquals(0, service.snapshot?.memberCount)

        finish(failed("test://OrderTest/total"))
        PlatformTestUtil.waitWithEventsDispatching("lens snapshot", { service.snapshot?.memberCount == 1 }, 10)
        assertTrue(service.snapshot!!.highlights(order.virtualFile))
        assertTrue(service.snapshot!!.highlights(order.virtualFile.parent))
        assertFalse(service.snapshot!!.highlights(readme))
        assertEquals("${FailingTestsLens.displayName} (1)", lensMenu().last { it.startsWith(FailingTestsLens.displayName) })

        finish(passed("test://OrderTest/total"))
        PlatformTestUtil.waitWithEventsDispatching("cleared snapshot", { service.snapshot?.memberCount == 0 }, 10)
    }

    /** Light tests share one project, so each test gets a tracker without the previous test's failures. */
    private fun freshTracker(): SmTestFailures {
        val tracker = SmTestFailures(project)
        project.registerOrReplaceServiceInstance(TestFailures::class.java, tracker, testRootDisposable)
        return tracker
    }

    private fun test(name: String): PsiFile {
        val file = myFixture.addFileToProject("src/test/$name", "class ${name.substringBefore('.')}")
        files[name.substringBefore('.')] = file
        return file
    }

    private fun finish(test: SMTestProxy) {
        project.messageBus.syncPublisher(SMTRunnerEventsListener.TEST_STATUS).onTestFinished(test)
    }

    private fun awaitFiles(expected: Set<Any>) {
        val failures = TestFailures.getInstance(project)!!
        PlatformTestUtil.waitWithEventsDispatching("failed tests located", { failures.files() == expected }, 10)
    }

    private fun proxy(url: String) = SMTestProxy(url.substringAfterLast('/'), false, url).apply { locator = Locator() }

    private fun failed(url: String) = proxy(url).apply {
        setStarted()
        setTestFailed("expected 2 but was 3", null, false)
    }

    private fun passed(url: String) = proxy(url).apply {
        setStarted()
        setFinished()
    }

    private fun ignored(url: String) = proxy(url).apply {
        setStarted()
        setTestIgnored("disabled", null)
    }

    private fun lensMenu(): List<String> {
        val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))
        return IconLensActionGroup().getChildren(event).mapNotNull { action ->
            val update = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
            action.update(update)
            update.presentation.text
        }
    }

    /** Resolves `test://<Class>/<method>` to the file the test added for that class. */
    private inner class Locator : SMTestLocator {
        override fun getLocation(
            protocol: String,
            path: String,
            project: Project,
            scope: GlobalSearchScope,
        ): List<Location<*>> = listOfNotNull(files[path.substringBefore('/')]?.let { PsiLocation(it) })
    }
}
