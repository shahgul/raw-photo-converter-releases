package com.shahgul.rawphotoconverter.conversion

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

// Permission-protected debug-only provider. Keeping this in the target UID lets tests
// exercise real content URI I/O without crossing another APK's private storage.
// It is absent from the release APK and has no document-picker intent filter.
class TestDocumentsProvider : DocumentsProvider() {
    // /data/user/0 and /data/data may alias the same cache. Document IDs must be
    // relative to the same canonical base used by file(), never contain ../.
    private val root get() = File(requireNotNull(context).cacheDir, "test-documents").canonicalFile
    override fun onCreate() = true
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        file(documentId).toPath().startsWith(file(parentDocumentId).toPath())
    private fun file(id: String): File {
        val file = File(root, id).canonicalFile
        require(file.toPath().startsWith(root.canonicalFile.toPath()))
        return file
    }
    private fun id(file: File) = file.relativeTo(root).invariantSeparatorsPath
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES)).apply {
        val row = newRow()
        for (column in columnNames) row.add(column, when (column) {
            Root.COLUMN_ROOT_ID -> "test"
            Root.COLUMN_DOCUMENT_ID -> "output"
            Root.COLUMN_TITLE -> "Test documents"
            Root.COLUMN_FLAGS -> Root.FLAG_SUPPORTS_CREATE
            Root.COLUMN_MIME_TYPES -> "*/*"
            else -> null
        })
    }
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = cursor(projection).apply {
        val file = file(documentId)
        if (file.exists()) addDocument(file)
    }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor = cursor(projection).apply {
        file(parentDocumentId).listFiles().orEmpty().forEach { addDocument(it) }
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode == "r" && documentId.startsWith("source/") && File(root, "block-read").exists()) {
            File(root, "read-entered").writeText("entered")
            val deadline = System.currentTimeMillis() + 15000
            while (File(root, "block-read").exists() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        }
        if (mode.contains('w') && File(root, "fail-write").exists()) throw FileNotFoundException("Simulated output provider failure")
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    }
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        require('/' !in displayName && '\\' !in displayName)
        val document = File(file(parentDocumentId), displayName)
        check(document.createNewFile()) { "Document already exists" }
        return id(document)
    }
    override fun renameDocument(documentId: String, displayName: String): String {
        val source = file(documentId)
        val destination = File(source.parentFile, displayName)
        check(!destination.exists() && source.renameTo(destination))
        return id(destination)
    }
    override fun deleteDocument(documentId: String) { check(file(documentId).delete()) }
    private fun cursor(projection: Array<out String>?) = MatrixCursor(projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE))
    private fun MatrixCursor.addDocument(file: File) {
        val row = newRow()
        for (column in columnNames) row.add(column, when (column) {
            Document.COLUMN_DOCUMENT_ID -> id(file)
            Document.COLUMN_DISPLAY_NAME -> file.name
            Document.COLUMN_MIME_TYPE -> if (file.isDirectory) Document.MIME_TYPE_DIR else if (file.extension == "jpg") "image/jpeg" else "application/octet-stream"
            Document.COLUMN_FLAGS -> if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
            Document.COLUMN_SIZE -> file.length()
            else -> null
        })
    }
}
