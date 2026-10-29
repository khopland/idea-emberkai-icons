package com.github.khopland.symbols

import com.github.khopland.symbols.core.IconResolver
import com.github.khopland.symbols.lens.IconLens
import com.github.khopland.symbols.lens.LensSnapshot
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile

/**
 * Icon lookups run on hot UI paths, so every new rule or badge has to stay cheap. This resolves a
 * synthetic monorepo and fails when the average lookup exceeds a fixed budget. Budgets are roughly
 * ten times the times measured on a developer laptop, which absorbs slower and noisier CI runners
 * while still catching a rule that starts reading file contents on every warm lookup.
 */
internal class IconLookupBudgetTest : IconProviderTestCase() {
    fun testIconLookupsStayWithinBudget() {
        val files = corpus()

        // The first pass reads content and fills caches; later passes must be served from them.
        val cold = timeNanos { files.forEach { IconResolver.resolve(it, project) } } / files.size
        val warm = bestOf(WARM_PASSES) { files.forEach { IconResolver.resolve(it, project) } } / files.size

        assertTrue("Cold lookup averaged ${cold}ns, budget ${COLD_BUDGET_NS}ns", cold <= COLD_BUDGET_NS)
        assertTrue("Warm lookup averaged ${warm}ns, budget ${WARM_BUDGET_NS}ns", warm <= WARM_BUDGET_NS)
    }

    fun testLensSnapshotStaysWithinBudget() {
        val fileCount = corpus().count { !it.isDirectory }

        val perFile = bestOf(LENS_PASSES) {
            runReadAction { LensSnapshot.collect(project, IconLens.PRODUCTION_BLAST_RADIUS) }
        } / fileCount

        assertTrue("Lens snapshot averaged ${perFile}ns per file, budget ${LENS_BUDGET_NS}ns", perFile <= LENS_BUDGET_NS)
    }

    /** Twenty services with a typical mix of tool files, plain files, and their folders. */
    private fun corpus(): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        fun add(path: String, content: String) {
            files += myFixture.addFileToProject(path, content).virtualFile
        }
        for (service in 0 until SERVICES) {
            val root = "services/svc$service"
            add("$root/build.gradle.kts", "plugins { id(\"org.springframework.boot\") }")
            add("$root/src/main/resources/application.yml", "server: {}")
            add("$root/src/main/resources/application-prod.yml", "server: {}")
            add("$root/k8s/deployment.yaml", "apiVersion: apps/v1\nkind: Deployment\n")
            add("$root/k8s/service.yaml", "apiVersion: v1\nkind: Service\n")
            add("$root/openapi.yaml", "openapi: 3.1.0\ninfo: {title: API}")
            add("$root/.env.production", "A=1")
            add("$root/docker-compose.prod.yml", "services: {}")
            for (version in 1..10) add("$root/db/migration/V${version}__step.sql", "select 1")
            for (index in 0 until 20) add("$root/docs/note$index.md", "# note")
            for (index in 0 until 20) add("$root/data/item$index.json", "{}")
        }
        val directories = files.flatMap { generateSequence(it.parent) { parent -> parent.parent }.take(4).toList() }
        return files + directories.distinct()
    }

    private inline fun bestOf(passes: Int, block: () -> Unit): Long = (1..passes).minOf { timeNanos(block) }

    private inline fun timeNanos(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private companion object {
        const val SERVICES = 20
        const val WARM_PASSES = 5
        const val LENS_PASSES = 3

        // Measured on an Apple Silicon laptop: cold ~58µs, warm ~5µs, lens ~3µs per file.
        const val COLD_BUDGET_NS = 600_000L
        const val WARM_BUDGET_NS = 50_000L
        const val LENS_BUDGET_NS = 30_000L
    }
}
