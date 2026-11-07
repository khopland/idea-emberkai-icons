package com.github.khopland.symbols.rules

import com.github.khopland.symbols.core.FileContentCache
import com.github.khopland.symbols.core.StructuredFileKind
import com.github.khopland.symbols.generated.GeneratedFiles
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

/**
 * Indexes what Emberkai reads from file contents: the structured YAML/JSON kind and Kubernetes
 * resource, SOPS encryption, and generated-file header markers. Indexing runs in the background,
 * survives restarts, and lets callers off the EDT skip reading the file. Keys name a fact, so a
 * later query can also list every file with one, such as `kind:KUBERNETES`.
 */
class ContentIndex : FileBasedIndexExtension<String, String>() {
    override fun getName(): ID<String, String> = NAME

    // Bump whenever a reader below changes what it reports, so existing indexes are rebuilt.
    override fun getVersion(): Int = 1

    override fun dependsOnFileContent(): Boolean = true

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter(::isIndexable)

    /** Most files have no facts and store nothing, so the index stays small. */
    override fun getIndexer(): DataIndexer<String, String, FileContent> = DataIndexer { content ->
        val file = content.file
        val text by lazy(LazyThreadSafetyMode.NONE) { content.contentAsText.toString() }
        buildMap {
            if (StructuredFileKind.appliesTo(file)) {
                val metadata = StructuredFileKind.parse(text)
                if (metadata.kind != StructuredFileKind.NONE) put(KIND_PREFIX + metadata.kind.name, metadata.resource.orEmpty())
            }
            if (SopsFiles.appliesTo(file) && SopsFiles.isEncrypted(text)) put(SOPS, "")
            if (GeneratedFiles.appliesTo(file)) {
                GeneratedFiles.find(text.take(GeneratedFiles.HEADER_LENGTH))?.let { put(GENERATED, it) }
            }
        }
    }

    internal companion object {
        val NAME: ID<String, String> = ID.create("com.github.khopland.symbols.content")
        private const val KIND_PREFIX = "kind:"
        private const val SOPS = "sops"
        private const val GENERATED = "generated"

        /** Larger files skip the index; their readers bail out or read only a short header. */
        private fun isIndexable(file: VirtualFile): Boolean =
            !file.isDirectory && file.length <= FileContentCache.MAX_FILE_SIZE && !file.fileType.isBinary

        /**
         * What the index knows about [file], or null when the caller must read the file itself: on
         * the EDT, without a read action, in dumb mode, with unsaved edits, or for a file the index
         * skips. An indexed file without facts stores nothing, so coverage is decided here instead.
         */
        fun facts(file: VirtualFile, project: Project?): Facts? {
            if (project == null || project.isDisposed || file !is VirtualFileWithId || !file.isValid) return null
            val application = ApplicationManager.getApplication()
            // Querying can index pending files first, which the EDT must not wait for.
            if (application.isDispatchThread || !application.isReadAccessAllowed || DumbService.isDumb(project)) return null
            if (!isIndexable(file) || FileDocumentManager.getInstance().isFileModified(file)) return null
            val fileIndex = ProjectFileIndex.getInstance(project)
            if (!fileIndex.isInContent(file) && !fileIndex.isInLibrary(file)) return null
            return try {
                Facts(FileBasedIndex.getInstance().getFileData(NAME, file, project))
            } catch (_: IndexNotReadyException) {
                null
            }
        }
    }

    /** One file's indexed facts. */
    internal class Facts(private val data: Map<String, String>) {
        val structured: StructuredFileKind.Metadata
            get() = data.entries.firstNotNullOfOrNull { (key, resource) ->
                // A kind this version no longer has reads as unknown until the version bump reindexes.
                val kind = StructuredFileKind.entries.firstOrNull { KIND_PREFIX + it.name == key } ?: return@firstNotNullOfOrNull null
                StructuredFileKind.Metadata(kind, resource.ifEmpty { null })
            } ?: StructuredFileKind.Metadata.UNKNOWN

        val sopsEncrypted: Boolean get() = SOPS in data

        val generatedMarker: String? get() = data[GENERATED]
    }
}
