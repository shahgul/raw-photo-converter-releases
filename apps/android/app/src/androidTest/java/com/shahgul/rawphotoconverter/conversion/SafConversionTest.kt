package com.shahgul.rawphotoconverter.conversion

import android.content.Context
import android.content.Intent
import android.app.ActivityManager
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CancellationException
import com.shahgul.rawphotoconverter.MainActivity

class SafConversionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.cacheDir, "test-documents")
    private val authority = "com.shahgul.rawphotoconverter.test.documents"
    private val output get() = requireNotNull(DocumentFile.fromTreeUri(context, DocumentsContract.buildTreeDocumentUri(authority, "output")))
    private val engine get() = RawConversionEngine(context)
    private val input get() = RawInput(DocumentsContract.buildDocumentUri(authority, "source/sample.ARW"), "sample.ARW")

    @Before fun setup() {
        root.deleteRecursively()
        File(root, "source").mkdirs()
        File(root, "output").mkdirs()
        NativeConversionTest().syntheticDng(6).let { fixture ->
            try { fixture.copyTo(File(root, "source/sample.ARW")) } finally { fixture.delete() }
        }
        context.getSharedPreferences("conversion", Context.MODE_PRIVATE).edit().clear().commit()
        // A real SAF picker grants access separately from provider ownership.
        // DocumentFile.canWrite() deliberately checks those URI grants.
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        for (uri in listOf(DocumentsContract.buildTreeDocumentUri(authority, "output"), DocumentsContract.buildTreeDocumentUri(authority, "source"), input.uri)) {
            context.grantUriPermission(context.packageName, uri, flags)
        }
        engine.cleanInterruptedTemps()
        assertTrue("Fixture output must resolve as a directory", output.isDirectory)
        assertTrue("Fixture output must be writable", output.canWrite())
    }

    @Test fun savesNewJpegThroughProviderAndNeverChangesOriginal() {
        val source = File(root, "source/sample.ARW").readBytes()
        val result = engine.convert(input, output, ConversionRecipe(), ConversionCancellation()) {}
        assertNotNull(result.output)
        assertEquals(64, result.width)
        assertEquals(96, result.height)
        assertTrue(File(root, "output/sample.jpg").length() > 0)
        assertArrayEquals(source, File(root, "source/sample.ARW").readBytes())
        assertEquals(listOf("sample.jpg"), File(root, "output").list().orEmpty().toList())
        assertTrue(engine.convert(input, output, ConversionRecipe(), ConversionCancellation()) {}.skipped)
    }

    @Test fun existingJpegIsSkippedCaseInsensitivelyWithoutWriting() {
        val existing = File(root, "output/SAMPLE.JPG").apply { writeText("existing JPEG") }
        val result = engine.convert(input, output, ConversionRecipe(), ConversionCancellation()) {}
        assertTrue(result.skipped)
        assertEquals("existing JPEG", existing.readText())
    }

    @Test fun providerFailureRemovesIncompleteDocumentAndPrivateTemps() {
        File(root, "fail-write").writeText("fail")
        val failure = assertThrows(Exception::class.java) { engine.convert(input, output, ConversionRecipe(), ConversionCancellation()) {} }
        assertTrue(failure.message.orEmpty().contains("Simulated output provider failure"))
        assertTrue(File(root, "output").list().orEmpty().isEmpty())
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("raw-conversion-") })
    }

    @Test fun cancellationBeforeSavingLeavesNoFinalJpeg() {
        val token = ConversionCancellation()
        assertThrows(CancellationException::class.java) {
            engine.convert(input, output, ConversionRecipe(), token) { if (it == "Saving JPEG") token.cancelled = true }
        }
        assertTrue(File(root, "output").list().orEmpty().isEmpty())
    }

    @Test fun recoveryDeletesOnlyJournalledTemporaryDocument() {
        val pending = requireNotNull(output.createFile("image/jpeg", ".raw-photo-converter-test.pending.jpg"))
        File(root, "output/other.jpg").writeText("keep")
        context.getSharedPreferences("conversion", Context.MODE_PRIVATE).edit().putString("pendingOutput", pending.uri.toString()).commit()
        engine.cleanInterruptedTemps()
        assertFalse(pending.exists())
        assertFalse(File(root, "output/.raw-photo-converter-test.pending.jpg").exists())
        assertEquals("keep", File(root, "output/other.jpg").readText())
    }

    @Test fun recoveryNeverDeletesVerifiedFinalJpeg() {
        val saved = requireNotNull(output.createFile("image/jpeg", "completed.jpg"))
        File(root, "output/completed.jpg").writeText("keep")
        context.getSharedPreferences("conversion", Context.MODE_PRIVATE).edit().putString("pendingOutput", saved.uri.toString()).commit()
        engine.cleanInterruptedTemps()
        assertTrue(saved.exists())
        assertEquals("keep", File(root, "output/completed.jpg").readText())
    }

    @Test fun foregroundFolderBatchContinuesAfterCorruptRaw() {
        File(root, "source/broken.ARW").writeText("not a RAW")
        val source = DocumentsContract.buildTreeDocumentUri(authority, "source")
        val destination = DocumentsContract.buildTreeDocumentUri(authority, "output")
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            activity.onActivity { ConversionService.start(it, source, "Source folder", true, destination, true) }
            val deadline = System.currentTimeMillis() + 60000
            while (System.currentTimeMillis() < deadline) {
                val state = ConversionStore.state.value
                if (!state.running && state.total == 2 && state.results.size == 2) break
                Thread.sleep(50)
            }
            val state = ConversionStore.state.value
            assertFalse(state.running)
            assertEquals("Batch did not start: $state", 2, state.total)
            assertEquals(1, state.results.count { it.error != null })
            assertEquals(1, state.results.count { it.output != null })
            assertTrue(File(root, "output/sample.jpg").exists())
            assertFalse(File(root, "output/broken.jpg").exists())
        }
    }

    @Suppress("DEPRECATION")
    @Test fun foregroundCancellationStopsServiceAndKeepsOriginal() {
        val source = File(root, "source/sample.ARW").readBytes()
        val block = File(root, "block-read").apply { writeText("block") }
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            try {
                activity.onActivity { ConversionService.start(it, input.uri, input.label, false, output.uri, true) }
                await("Provider read did not start") { File(root, "read-entered").exists() }
                activity.onActivity { ConversionService.cancel(it) }
                await("Cancel command was not received") { ConversionStore.state.value.phase == "Cancelling" }
                block.delete()
                await("Cancelled foreground service did not stop") {
                    !ConversionStore.state.value.running &&
                        context.getSystemService(ActivityManager::class.java).getRunningServices(20).none {
                            it.service.className == ConversionService::class.java.name
                        }
                }
                assertEquals("Cancelled", ConversionStore.state.value.phase)
                assertTrue(File(root, "output").list().orEmpty().isEmpty())
                assertArrayEquals(source, File(root, "source/sample.ARW").readBytes())
            } finally { block.delete() }
        }
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        fail("$message: ${ConversionStore.state.value}")
    }
}
