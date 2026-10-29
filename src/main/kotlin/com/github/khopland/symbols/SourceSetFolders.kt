package com.github.khopland.symbols

import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale
import java.util.regex.Pattern

/** Standard JVM and Multiplatform layout fallback, used only when no imported root role applies. */
internal object SourceSetFolders {
    private val SOURCE_SET: Pattern = Pattern.compile("(?:[a-z0-9_]+)?(?:main|test)")

    fun role(directory: VirtualFile): ProjectFolderIcons.Role? {
        if (isSourceSet(directory)) return ProjectFolderIcons.Role.CONTENT_ROOT
        var root = directory
        while (true) {
            val name = root.name.lowercase(Locale.ROOT)
            if (name == "kotlin" || name == "java" || name == "resources") {
                val sourceSet = root.parent
                if (sourceSet != null && isSourceSet(sourceSet)) {
                    val test = sourceSet.name.lowercase(Locale.ROOT).endsWith("test")
                    if (name == "resources") {
                        if (root != directory) return null
                        return if (test) ProjectFolderIcons.Role.TEST_RESOURCES
                        else ProjectFolderIcons.Role.RESOURCES
                    }
                    if (root == directory) {
                        return if (test) ProjectFolderIcons.Role.TEST_SOURCE else ProjectFolderIcons.Role.SOURCE
                    }
                    return if (test) ProjectFolderIcons.Role.TEST_PACKAGE else ProjectFolderIcons.Role.PACKAGE
                }
            }
            root = root.parent ?: break
        }
        return null
    }

    private fun isSourceSet(directory: VirtualFile): Boolean {
        val parent = directory.parent ?: return false
        return parent.name.equals("src", ignoreCase = true) &&
            SOURCE_SET.matcher(directory.name.lowercase(Locale.ROOT)).matches()
    }
}
