package com.github.khopland.symbols;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtilCore;

import java.io.IOException;
import java.util.function.Function;

/** Caches small inspection results, including unsaved edits, without retaining file text. */
final class FileContentCache {
    static final int MAX_FILE_SIZE = 256 * 1024;

    record Inspection<T>(long stamp, boolean fromDocument, T value) {}

    private FileContentCache() {}

    static <T> T inspect(VirtualFile file, Key<Inspection<T>> key, T fallback, Function<String, T> reader) {
        if (file == null || !file.isValid() || file.isDirectory() || file.getLength() > MAX_FILE_SIZE) return fallback;
        var document = FileDocumentManager.getInstance().getCachedDocument(file);
        if (document != null && document.getTextLength() > MAX_FILE_SIZE) return fallback;
        long stamp = document == null ? file.getModificationStamp() : document.getModificationStamp();
        Inspection<T> cached = file.getUserData(key);
        if (cached != null && cached.stamp == stamp && cached.fromDocument == (document != null)) return cached.value;

        T value = fallback;
        try {
            String text = document == null ? VfsUtilCore.loadText(file) : document.getText();
            value = reader.apply(text);
        } catch (IOException ignored) {
            // Unreadable metadata leaves the normal icon in place.
        }
        file.putUserData(key, new Inspection<>(stamp, document != null, value));
        return value;
    }
}
