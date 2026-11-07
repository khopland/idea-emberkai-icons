package com.github.khopland.symbols.rules

import com.github.khopland.symbols.core.FileContentCache
import com.github.khopland.symbols.core.StructuredFileKind
import com.github.khopland.symbols.generated.GeneratedFiles
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
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

    override fun getInputFilter(): FileBasedIndex.InputFilter =
        FileBasedIndex.InputFilter { file -> !file.fileType.isBinary }

    override fun getIndexer(): DataIndexer<String, String, FileContent> = DataIndexer { content ->
        val file = content.file
        val text = content.contentAsText
        buildMap {
            put(INDEXED, "")
            if (text.length <= FileContentCache.MAX_FILE_SIZE) {
                if (StructuredFileKind.appliesTo(file)) {
                    val metadata = StructuredFileKind.parse(text.toString())
                    if (metadata.kind != StructuredFileKind.NONE) put(KIND_PREFIX + metadata.kind.name, metadata.resource.orEmpty())
                }
                if (SopsFiles.appliesTo(file) && SopsFiles.isEncrypted(text.toString())) put(SOPS, "")
            }
            if (GeneratedFiles.appliesTo(file)) {
                GeneratedFiles.find(text.subSequence(0, minOf(text.length, GeneratedFiles.HEADER_LENGTH)).toString())
                    ?.let { put(GENERATED, it) }
            }
        }
    }

    internal companion object {
        val NAME: ID<String, String> = ID.create("com.github.khopland.symbols.content")
        private const val INDEXED = "indexed"
        private const val KIND_PREFIX = "kind:"
        private const val SOPS = "sops"
        private const val GENERATED = "generated"

        /**
         * What the index knows about [file], or null when the caller must read the file itself: on
         * the EDT, without a read action, in dumb mode, with unsaved edits, or for a file the index skips.
         */
        fun facts(file: VirtualFile, project: Project?): Facts? {
            if (project == null || project.isDisposed || file !is VirtualFileWithId || !file.isValid) return null
            val application = ApplicationManager.getApplication()
            // Querying can index pending files first, which the EDT must not wait for.
            if (application.isDispatchThread || !application.isReadAccessAllowed || DumbService.isDumb(project)) return null
            if (FileDocumentManager.getInstance().isFileModified(file)) return null
            val data = try {
                FileBasedIndex.getInstance().getFileData(NAME, file, project)
            } catch (_: IndexNotReadyException) {
                return null
            }
            return data.takeIf { INDEXED in it }?.let(::Facts)
        }
    }

    /** One file's indexed facts. */
    internal class Facts(private val data: Map<String, String>) {
        val structured: StructuredFileKind.Metadata
            get() = data.entries.firstOrNull { it.key.startsWith(KIND_PREFIX) }
                ?.let { (key, resource) ->
                    StructuredFileKind.Metadata(StructuredFileKind.valueOf(key.removePrefix(KIND_PREFIX)), resource.ifEmpty { null })
                }
                ?: StructuredFileKind.Metadata.UNKNOWN

        val sopsEncrypted: Boolean get() = SOPS in data

        val generatedMarker: String? get() = data[GENERATED]
    }
}
