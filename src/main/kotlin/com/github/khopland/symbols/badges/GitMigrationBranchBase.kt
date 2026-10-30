package com.github.khopland.symbols.badges

import com.github.khopland.symbols.core.RefreshProjectIconPresentations
import com.github.khopland.symbols.git.GitQueries
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import git4idea.commands.GitCommand
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Lists each migration folder on the default branch with `git ls-tree`, on one background thread.
 * Only folders the icon provider asks about are looked up, and each once until a repository change
 * (a fetch, commit, or checkout) moves a ref; then every known folder is looked up again. Icons
 * refresh only when a folder's answer changes.
 */
internal class GitMigrationBranchBase(private val project: Project) : MigrationBranchBase, Disposable {
    private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Emberkai migration base", 1)
    private val answers = ConcurrentHashMap<VirtualFile, Answer>()
    private val pending = ConcurrentHashMap.newKeySet<VirtualFile>()

    /** Null [migrations] records a folder outside Git or without a default branch, so it is not looked up again. */
    private class Answer(val migrations: BaseBranchMigrations?)

    init {
        project.messageBus.connect(this).subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener { answers.keys.forEach(::schedule) },
        )
    }

    override fun migrationsOnBase(directory: VirtualFile): BaseBranchMigrations? {
        val answer = answers[directory]
        if (answer == null) {
            if (answers.size < MAX_FOLDERS) schedule(directory)
            return null
        }
        return answer.migrations
    }

    override fun dispose() {
        answers.clear()
    }

    private fun schedule(directory: VirtualFile) {
        if (project.isDisposed || !pending.add(directory)) return
        executor.execute {
            pending.remove(directory)
            if (project.isDisposed || !directory.isValid) {
                answers.remove(directory)
                return@execute
            }
            val answer = Answer(lookUp(directory))
            val previous = answers.put(directory, answer)
            if (answer.migrations != previous?.migrations) {
                ApplicationManager.getApplication().invokeLater(
                    { RefreshProjectIconPresentations.refresh(project) },
                    project.disposed,
                )
            }
        }
    }

    private fun lookUp(directory: VirtualFile): BaseBranchMigrations? {
        val repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(directory) ?: return null
        val root = repository.root
        val base = GitQueries.defaultBranch(project, repository) ?: return null
        val relative = VfsUtilCore.getRelativePath(directory, root) ?: return null
        val path = if (relative.isEmpty()) "." else "$relative/"
        val paths = GitQueries.run(project, root, GitCommand.LS_TREE, "--name-only", base, "--", path) ?: return null
        val names = paths.filter { it.isNotBlank() }.mapTo(HashSet()) { it.substringAfterLast('/') }
        return BaseBranchMigrations(base, names, FlywayMigrations.latestVersion(names))
    }

    private companion object {
        const val MAX_FOLDERS = 200
    }
}
