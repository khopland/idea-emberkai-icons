package com.github.khopland.symbols.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SlowOperations
import java.io.IOException

/** Caches small inspection results, including unsaved edits, without retaining file text. */
internal object FileContentCache {
    const val MAX_FILE_SIZE = 256 * 1024

    data class Inspection<T>(val stamp: Long, val fromDocument: Boolean, val value: T)

    fun <T> inspect(
        file: VirtualFile?,
        key: Key<Inspection<T>>,
        fallback: T,
        reader: (String) -> T,
    ): T {
        if (file == null || !file.isValid || file.isDirectory || file.length > MAX_FILE_SIZE) return fallback
        val document = FileDocumentManager.getInstance().getCachedDocument(file)
        if (document != null && document.textLength > MAX_FILE_SIZE) return fallback
        val stamp = document?.modificationStamp ?: file.modificationStamp
        val cached = file.getUserData(key)
        if (cached != null && cached.stamp == stamp && cached.fromDocument == (document != null)) return cached.value

        var value = fallback
        try {
            val text = document?.text ?: VfsUtilCore.loadText(file)
            value = reader(text)
        } catch (_: IOException) {
            // Unreadable metadata leaves the normal icon in place.
        }
        file.putUserData(key, Inspection(stamp, document != null, value))
        return value
    }

    /** The result [inspect] cached for [file]'s current content, or null when it has not run since; never reads. */
    fun <T> peek(file: VirtualFile?, key: Key<Inspection<T>>): Inspection<T>? {
        if (file == null || !file.isValid || file.isDirectory) return null
        val document = FileDocumentManager.getInstance().getCachedDocument(file)
        val stamp = document?.modificationStamp ?: file.modificationStamp
        return file.getUserData(key)?.takeIf { it.stamp == stamp && it.fromDocument == (document != null) }
    }

    /**
     * Whether this thread may load file content or VFS children from disk. On the EDT that is a slow
     * operation the IDE reports; unit tests allow it unless they forbid slow operations, as the
     * platform's own check does.
     */
    fun mayLoad(): Boolean {
        val application = ApplicationManager.getApplication()
        return !application.isDispatchThread ||
            (application.isUnitTestMode && !java.lang.Boolean.getBoolean(SlowOperations.FORBID_SLOW_OPS_PROPERTY))
    }
}
