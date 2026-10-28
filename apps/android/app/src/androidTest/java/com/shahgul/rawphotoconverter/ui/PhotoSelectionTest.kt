// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.shahgul.rawphotoconverter.MainActivity
import com.shahgul.rawphotoconverter.conversion.BatchState
import com.shahgul.rawphotoconverter.conversion.ConversionStore
import com.shahgul.rawphotoconverter.conversion.ConversionPolicy
import com.shahgul.rawphotoconverter.conversion.NativeConversionTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class PhotoSelectionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val authority = "com.shahgul.rawphotoconverter.test.documents"
    private fun uri(name: String) = DocumentsContract.buildDocumentUri(authority, "source/$name")

    @Before fun setup() {
        compose.runOnUiThread { ConversionStore.publish(BatchState()) }
        val root = File(compose.activity.cacheDir, "test-documents/source").apply { mkdirs() }
        val fixture = NativeConversionTest().syntheticDng(1)
        try {
            fixture.copyTo(File(root, "first.ARW"), overwrite = true)
            fixture.copyTo(File(root, "second.ARW"), overwrite = true)
            fixture.copyTo(File(root, "third.CR3"), overwrite = true)
            File(root, "notes.txt").writeText("not a RAW")
        } finally { fixture.delete() }
        listOf("first.ARW", "second.ARW", "third.CR3", "notes.txt").forEach {
            compose.activity.grantUriPermission(compose.activity.packageName, uri(it), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    @After fun reset() {
        compose.runOnUiThread { ConversionStore.publish(BatchState()) }
    }

    private fun pick(vararg names: String) {
        val clip = ClipData.newRawUri("RAW photos", uri(names.first()))
        names.drop(1).forEach { clip.addItem(ClipData.Item(uri(it))) }
        val result = Intent().apply { clipData = clip; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val filter = IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
            addDataType("*/*")
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        val monitor = instrumentation.addMonitor(filter,
            Instrumentation.ActivityResult(Activity.RESULT_OK, result), true)
        try {
            compose.onNode(hasText("Add photos") and hasClickAction()).performClick()
            compose.waitUntil(10_000) {
                // Activity-result delivery briefly detaches the Compose hierarchy.
                // Keep the picker monitor installed until the selected files arrive.
                runCatching {
                    compose.onAllNodesWithText("Reading source…").fetchSemanticsNodes().isEmpty() &&
                        names.filter(ConversionPolicy::isSupportedRaw).all {
                            compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()
                        }
                }.getOrDefault(false)
            }
        } finally { instrumentation.removeMonitor(monitor) }
        compose.waitForIdle()
    }

    @Test fun pickerAppendsDeduplicatesAndRestoresQueueAcrossRecreation() {
        pick("first.ARW", "second.ARW")
        compose.onNodeWithText("2 selected photos").assertExists()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val resolver = compose.activity.contentResolver
        val capture = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "android-multi-photo-queue.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RawConverterTests")
        })!!
        try { resolver.openOutputStream(capture)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        pick("second.ARW", "third.CR3", "notes.txt")
        compose.onNodeWithText("3 selected photos").assertExists()
        compose.onNodeWithText("notes.txt").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            runCatching {
                compose.onAllNodesWithText("3 selected photos").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        compose.onNodeWithContentDescription("Remove third.CR3").performClick()
        compose.onNodeWithText("Remove photo?").assertExists()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("2 selected photos").assertExists()
        compose.onNodeWithContentDescription("Remove second.ARW").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("first.ARW").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Remove photo").performScrollTo().performClick()
        compose.onNodeWithText("Remove photo?").assertExists()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Convert to JPEG").assertIsNotEnabled()
    }
}
