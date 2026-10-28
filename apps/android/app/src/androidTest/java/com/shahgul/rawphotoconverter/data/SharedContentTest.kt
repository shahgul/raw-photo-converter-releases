// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import com.shahgul.rawphotoconverter.conversion.ConversionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SharedContentTest {
    private val first = Uri.parse("content://photos/first")
    private val second = Uri.parse("content://photos/second")

    @Test fun singleAndMultipleSharesDeduplicateStreamsAndClipData() {
        val single = incomingSharedContent(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, first))!!
        assertEquals(listOf(first), single.uris)
        val multiple = Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
        multiple.clipData = ClipData.newRawUri("photos", first)
        assertEquals(listOf(first, second), incomingSharedContent(multiple)!!.uris)
        assertNull(incomingSharedContent(Intent(Intent.ACTION_MAIN)))
    }

    @Test fun untrustedPathsAndMalformedExtrasCannotEnterQueue() {
        val mixed = Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM,
            arrayListOf(first, Uri.parse("file:///storage/emulated/0/photo.ARW"), Uri.parse("https://example.com/photo.ARW")))
        assertEquals(listOf(first), incomingSharedContent(mixed)!!.uris)
        assertNotNull(incomingSharedContent(mixed)!!.message)
        val malformed = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, Intent(Intent.ACTION_DELETE))
        assertTrue(incomingSharedContent(malformed)!!.uris.isEmpty())
        assertNotNull(incomingSharedContent(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "text only"))!!.message)
        val directory = Intent(Intent.ACTION_VIEW).setDataAndType(first, DocumentsContract.Document.MIME_TYPE_DIR)
        assertEquals(listOf(first), incomingSharedContent(directory)!!.uris)
        assertNull(incomingSharedContent(Intent(Intent.ACTION_VIEW).setDataAndType(first, "image/jpeg")))
    }

    @Test fun folderSharesKeepSupportedFilesAndReportUnsupportedMissingAndSubfolders() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "test-documents/share-fixture")
        root.deleteRecursively()
        root.mkdirs()
        File(root, "first.ARW").writeText("inspection only")
        File(root, "second.CR3").writeText("inspection only")
        File(root, "notes.txt").writeText("unsupported")
        File(root, "nested").mkdirs()
        val authority = "com.shahgul.rawphotoconverter.test.documents"
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "share-fixture")
        context.grantUriPermission(context.packageName, tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        try {
            val repository = RawSourceRepository(context)
            val result = repository.inspectSharedContent(listOf(tree, tree))
            assertEquals(setOf("first.ARW", "second.CR3"), result.photos.map { it.label }.toSet())
            assertEquals(2, result.failures.size)
            val document = DocumentsContract.buildDocumentUri(authority, "share-fixture")
            assertEquals(2, repository.inspectSharedContent(listOf(document)).photos.size)
            val missing = DocumentsContract.buildDocumentUri(authority, "share-fixture/missing.ARW")
            assertEquals(1, repository.inspectSharedContent(listOf(missing)).failures.size)
            assertEquals(2, repository.inspectFolder(tree).ignoredCount)
            File(root, "first.ARW").delete()
            File(root, "second.CR3").delete()
            val unsupportedOnly = repository.inspectSharedContent(listOf(tree))
            assertTrue(unsupportedOnly.photos.isEmpty())
            assertEquals(2, unsupportedOnly.failures.size)
        } finally { root.deleteRecursively() }
    }

    @Test fun sonyArwFolderCountsEachDscFileAndSkipsTheJpeg() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "test-documents/raw-test-folder")
        root.deleteRecursively()
        root.mkdirs()
        listOf("DSC0001.ARW", "DSC0002.ARW", "DSC0003.ARW", "DSC0004.ARW").forEach { File(root, it).writeText("sony raw") }
        File(root, "note.jpg").writeText("jpeg")
        val tree = DocumentsContract.buildTreeDocumentUri("com.shahgul.rawphotoconverter.test.documents", "raw-test-folder")
        context.grantUriPermission(context.packageName, tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        try {
            val summary = RawSourceRepository(context).inspectFolder(tree)
            assertEquals("raw-test-folder", summary.label)
            assertEquals(4, summary.rawCount)
            assertEquals(1, summary.ignoredCount)
            assertTrue(summary.totalBytes > 0L)
        } finally { root.deleteRecursively() }
    }

    @Test fun exportSharesOnlySuccessfulDistinctJpegsWithReadGrantsForEveryUri() {
        val results = listOf(ConversionResult("first", first), ConversionResult("duplicate", first),
            ConversionResult("second", second), ConversionResult("failed", first, error = "failed"),
            ConversionResult("skipped", first, skipped = true), ConversionResult("no output"))
        assertEquals(listOf(first, second), savedJpegUris(results))
        val multiple = jpegShareIntent(savedJpegUris(results))
        assertEquals(Intent.ACTION_SEND_MULTIPLE, multiple.action)
        assertEquals("image/jpeg", multiple.type)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, multiple.flags)
        assertEquals(2, multiple.clipData!!.itemCount)
        assertEquals(second, multiple.clipData!!.getItemAt(1).uri)
        assertEquals(listOf(first, second), incomingSharedContent(multiple)!!.uris)
        assertEquals(Intent.ACTION_SEND, jpegShareIntent(listOf(first, first)).action)
        assertThrows(IllegalArgumentException::class.java) { jpegShareIntent(emptyList()) }
    }
}
