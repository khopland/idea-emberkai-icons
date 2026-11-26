package com.github.khopland.emberkai.lens

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * The test files with a test that failed the last time it ran in this IDE session, for
 * [FailingTestsLens]. Registered only when the IDE has the test runner; without it the lens is not
 * offered.
 */
internal interface TestFailures {
    /** Never null: a session with no failed test has an empty set. Cheap; safe from any thread. */
    fun files(): Set<VirtualFile>

    companion object {
        fun getInstance(project: Project): TestFailures? = project.getService(TestFailures::class.java)
    }
}
