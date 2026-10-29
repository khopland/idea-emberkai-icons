package com.github.khopland.symbols.core

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
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
}
