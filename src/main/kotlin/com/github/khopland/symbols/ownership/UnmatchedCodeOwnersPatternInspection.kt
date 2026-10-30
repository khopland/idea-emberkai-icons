package com.github.khopland.symbols.ownership

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiFile

/**
 * Flags CODEOWNERS lines whose pattern matches nothing in the repository, usually a path that was
 * moved or deleted, so its owners silently stopped being requested. Also flags a CODEOWNERS file
 * that GitHub ignores because one in an earlier location wins. Excluded folders such as `build/`
 * and `node_modules/` count as paths but are not searched, and nested Git repositories are skipped.
 */
class UnmatchedCodeOwnersPatternInspection : LocalInspectionTool() {
    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        val virtualFile = file.virtualFile ?: return null
        if (virtualFile.name != "CODEOWNERS") return null
        val root = repositoryRootFor(virtualFile) ?: return null
        val active = CodeOwners.fileAt(root) ?: return null
        if (active != virtualFile) {
            val relative = VfsUtilCore.getRelativePath(active, root, '/') ?: active.name
            return arrayOf(
                manager.createProblemDescriptor(
                    file, TextRange(0, minOf(file.textLength, firstLineLength(file.text))),
                    "Ignored: $relative takes precedence", ProblemHighlightType.WARNING, isOnTheFly,
                ),
            )
        }
        val rules = CodeOwners.parse(file.text)
        if (rules.isEmpty()) return null
        val unmatched = rules.toMutableList()
        val index = ProjectFileIndex.getInstance(file.project)
        VfsUtilCore.visitChildrenRecursively(root, object : VirtualFileVisitor<Unit>() {
            override fun visitFileEx(child: VirtualFile): Result {
                ProgressManager.checkCanceled()
                if (unmatched.isEmpty()) return SKIP_CHILDREN
                if (child == root) return CONTINUE
                if (child.isDirectory && (child.name == ".git" || child.findChild(".git") != null)) return SKIP_CHILDREN
                val path = VfsUtilCore.getRelativePath(child, root, '/') ?: return SKIP_CHILDREN
                unmatched.removeAll { it.matches(path, child.isDirectory) }
                return if (child.isDirectory && index.isExcluded(child)) SKIP_CHILDREN else CONTINUE
            }
        })
        if (unmatched.isEmpty()) return null
        val lineStarts = lineStarts(file.text)
        return unmatched.mapNotNull { rule ->
            val start = lineStarts.getOrNull(rule.line - 1) ?: return@mapNotNull null
            val lineText = file.text.substring(start).substringBefore('\n')
            val offset = lineText.length - lineText.trimStart().length
            val end = start + offset + lineText.trimStart().takeWhile { !it.isWhitespace() }.length
            manager.createProblemDescriptor(
                file, TextRange(start + offset, maxOf(end, start + offset + 1)),
                "Pattern matches no file or folder in the repository", ProblemHighlightType.WARNING, isOnTheFly,
            )
        }.toTypedArray()
    }

    private fun repositoryRootFor(codeOwners: VirtualFile): VirtualFile? {
        val parent = codeOwners.parent ?: return null
        return listOfNotNull(parent.parent, parent).firstOrNull { candidate ->
            CodeOwners.LOCATION_NAMES.any { candidate.findFileByRelativePath(it) == codeOwners }
        }
    }

    private fun lineStarts(text: String): List<Int> = buildList {
        add(0)
        text.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
    }

    private fun firstLineLength(text: String): Int = text.indexOf('\n').takeIf { it >= 0 } ?: text.length
}
