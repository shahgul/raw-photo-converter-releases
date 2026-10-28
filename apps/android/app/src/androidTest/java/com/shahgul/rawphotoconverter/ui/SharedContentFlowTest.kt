// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import android.content.ClipData
import android.content.Intent
import android.app.Activity
import android.app.Instrumentation
import android.net.Uri
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import android.provider.DocumentsContract
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.IntentCompat
import com.shahgul.rawphotoconverter.MainActivity
import com.shahgul.rawphotoconverter.conversion.*
import com.shahgul.rawphotoconverter.ui.theme.RawPhotoConverterTheme
import com.shahgul.rawphotoconverter.ui.theme.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class SharedContentFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val authority = "com.shahgul.rawphotoconverter.test.documents"
    private lateinit var launchIntent: Intent
    private fun uri(name: String) = DocumentsContract.buildDocumentUri(authority, "shared-ui/$name")

    @Before fun setup() {
        launchIntent = Intent(compose.activity.intent)
        compose.runOnUiThread { ConversionStore.publish(BatchState()) }
        val root = File(compose.activity.cacheDir, "test-documents/shared-ui")
        root.deleteRecursively()
        root.mkdirs()
        val fixture = NativeConversionTest().syntheticDng(1)
        try {
            fixture.copyTo(File(root, "first.ARW"))
            fixture.copyTo(File(root, "second.ARW"))
        } finally { fixture.delete() }
        File(root, "notes.txt").writeText("unsupported")
        File(root, "nested").mkdirs()
        val tree = DocumentsContract.buildTreeDocumentUri(authority, "shared-ui")
        compose.activity.grantUriPermission(compose.activity.packageName, tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
    }

    @After fun reset() {
        compose.runOnUiThread {
            compose.activity.intent = Intent(launchIntent)
            ConversionStore.publish(BatchState())
        }
    }

    private fun share(vararg files: Uri) {
        compose.runOnUiThread {
            val incoming = Intent(compose.activity, MainActivity::class.java).apply {
                action = if (files.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                type = "*/*"
                if (files.size == 1) putExtra(Intent.EXTRA_STREAM, files.single())
                else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.toList()))
                clipData = ClipData.newRawUri("Shared RAWs", files.first()).apply {
                    files.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // Exercise the framework callback directly: ActivityScenario identifies
            // its activity by the original launch Intent, which a real share replaces.
            MainActivity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java).apply {
                isAccessible = true
            }.invoke(compose.activity, incoming)
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(15_000) { runCatching {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false) }
        compose.waitForIdle()
    }

    @Test fun repeatedMixedSharesPreserveQueueAndRecreationDoesNotImportAgain() {
        share(uri("first.ARW"))
        awaitText("first.ARW")
        share(uri("first.ARW"), uri("second.ARW"), uri("notes.txt"))
        awaitText("2 selected photos")
        awaitText("weren't added")
        compose.onNodeWithText("first.ARW").assertExists()
        compose.onNodeWithText("second.ARW").assertExists()
        compose.runOnUiThread { compose.activity.intent = Intent(launchIntent) }
        compose.activityRule.scenario.recreate()
        awaitText("2 selected photos")
        share(uri("notes.txt"))
        awaitText("weren't added")
        compose.onNodeWithText("2 selected photos").assertExists()
    }

    @Test fun sharedFolderReportsUnsupportedFilesAndIncludesBothRaws() {
        share(DocumentsContract.buildTreeDocumentUri(authority, "shared-ui"))
        awaitText("2 selected photos")
        awaitText("subfolders aren't included")
        compose.onNodeWithText("first.ARW").assertExists()
        compose.onNodeWithText("second.ARW").assertExists()
    }

    @Test fun resultsOfferBatchAndIndividualExportAndDisableBatchWhileSharing() {
        val files = listOf(ConversionResult("first.ARW", Uri.parse("content://output/first.jpg")),
            ConversionResult("second.ARW", Uri.parse("content://output/second.jpg")),
            ConversionResult("bad.ARW", error = "Invalid RAW"), ConversionResult("skipped.ARW", skipped = true))
        var shares = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                RawPhotoConverterTheme {
                    ResultsScreen(BatchState(results = files, total = 4, finishedAtMs = 1L), null,
                        {}, {}, {}, {}, {}, false, {}, ThemePreference.System, {}, onShareAll = { shares++ })
                }
            }
        }
        compose.onNodeWithText("Share 2 JPEGs").assertIsEnabled().performClick()
        assertEquals(1, shares)
        compose.onAllNodesWithText("Share").onFirst().assertIsEnabled()
        compose.runOnUiThread {
            compose.activity.setContent {
                RawPhotoConverterTheme {
                    ResultsScreen(BatchState(results = files, total = 4, finishedAtMs = 1L), null,
                        {}, {}, {}, {}, {}, false, {}, ThemePreference.System, {}, sharing = true)
                }
            }
        }
        compose.onNodeWithText("Preparing JPEGs…").assertIsNotEnabled()
    }

    @Test fun appExportOpensChooserWithReadableOutputsAndReportsDeletedJpegs() {
        val root = File(compose.activity.cacheDir, "test-documents/shared-ui")
        for (name in listOf("first.jpg", "second.jpg")) {
            val file = File(root, name)
            val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
            try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } }
            finally { bitmap.recycle() }
            ExifInterface(file).apply { setAttribute(ExifInterface.TAG_MODEL, "Private camera"); saveAttributes() }
        }
        val results = listOf(ConversionResult("first.ARW", uri("first.jpg")),
            ConversionResult("second.ARW", uri("second.jpg")), ConversionResult("bad.ARW", error = "Invalid RAW"))
        compose.runOnUiThread {
            ConversionStore.publish(BatchState(results = results, total = 3, finishedAtMs = 1L))
            compose.activity.deliverShowProcessingRequest()
        }
        awaitText("Share 2 JPEGs")
        compose.onNodeWithText("Remove EXIF metadata").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("Export options").performClick()
        compose.onNodeWithText("Remove EXIF metadata").assertDoesNotExist()
        compose.onNodeWithText("Export options").performClick()
        compose.onNodeWithText("Remove EXIF metadata").assertIsOn()
        val captured = AtomicReference<Intent>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_CHOOSER) return null
                captured.set(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            compose.onNodeWithText("Share 2 JPEGs").performClick()
            compose.waitUntil(10_000) { captured.get() != null }
            val outbound = IntentCompat.getParcelableExtra(captured.get(), Intent.EXTRA_INTENT, Intent::class.java)
            assertNotNull(outbound)
            assertEquals(Intent.ACTION_SEND_MULTIPLE, outbound!!.action)
            assertEquals(2, outbound.clipData!!.itemCount)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, outbound.flags)
            val exported = compose.activity.contentResolver.openInputStream(outbound.clipData!!.getItemAt(0).uri)!!.use { ExifInterface(it) }
            assertEquals(null, exported.getAttribute(ExifInterface.TAG_MODEL))
            assertEquals("Private camera", ExifInterface(File(root, "first.jpg")).getAttribute(ExifInterface.TAG_MODEL))
            File(root, "second.jpg").delete()
            captured.set(null)
            compose.onNodeWithText("Share 2 JPEGs").performClick()
            compose.waitUntil(10_000) { captured.get() != null }
            val remaining = IntentCompat.getParcelableExtra(captured.get(), Intent.EXTRA_INTENT, Intent::class.java)!!
            assertEquals(Intent.ACTION_SEND, remaining.action)
            assertEquals("${compose.activity.packageName}.exports", remaining.clipData!!.getItemAt(0).uri!!.authority)
            awaitText("weren't shared")
        } finally {
            instrumentation.removeMonitor(monitor)
            compose.runOnUiThread { ConversionStore.publish(BatchState()) }
        }
    }
}
