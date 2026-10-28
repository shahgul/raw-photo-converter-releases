// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.Intent
import android.content.IntentFilter
import android.provider.DocumentsContract
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.shahgul.rawphotoconverter.MainActivity
import com.shahgul.rawphotoconverter.conversion.BatchState
import com.shahgul.rawphotoconverter.conversion.ConversionStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class OutputFolderSelectionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val authority = "com.shahgul.rawphotoconverter.test.documents"
    private fun tree(name: String) = DocumentsContract.buildTreeDocumentUri(authority, name)
    private fun document(name: String) = DocumentsContract.buildDocumentUri(authority, "raw-test-folder/$name")

    @Before fun setup() {
        compose.runOnUiThread { ConversionStore.publish(BatchState()) }
        val root = File(compose.activity.cacheDir, "test-documents")
        File(root, "raw-test-folder").apply { deleteRecursively(); mkdirs() }
        File(root, "raw-result").apply { deleteRecursively(); mkdirs() }
        listOf("DSC0001.ARW", "DSC0002.ARW", "DSC0003.ARW", "DSC0004.ARW").forEach {
            File(root, "raw-test-folder/$it").writeText("sony raw")
        }
        File(root, "raw-test-folder/note.jpg").writeText("jpeg")
        File(root, "raw-result/one.jpg").writeText("jpeg")
        File(root, "raw-result/two.jpg").writeText("jpeg")
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        listOf(tree("raw-test-folder"), tree("raw-result"), document("DSC0001.ARW"), document("DSC0002.ARW")).forEach {
            compose.activity.grantUriPermission(compose.activity.packageName, it, flags)
        }
    }

    @After fun reset() {
        compose.runOnUiThread { ConversionStore.publish(BatchState()) }
    }

    @Test fun choosingOutputFolderKeepsSingleRawPhoto() {
        pickPhotos("DSC0001.ARW")
        compose.waitUntil(15_000) {
            runCatching { compose.onAllNodesWithText("DSC0001.ARW").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.onNodeWithText("1 RAW", substring = true).assertExists()
        pickTree("raw-result", "Choose output folder")
        compose.onNodeWithText("DSC0001.ARW").assertExists()
        compose.onNodeWithText("1 RAW", substring = true).assertExists()
        compose.onNodeWithText("raw-result").assertExists()
        compose.onNodeWithText("0 RAW", substring = true).assertDoesNotExist()
        compose.onNodeWithText("No supported RAW files found in this folder").assertDoesNotExist()
        compose.onNodeWithText("Convert to JPEG").assertIsEnabled()
        compose.activityRule.scenario.recreate()
        awaitText("DSC0001.ARW")
        compose.onNodeWithText("raw-result").assertExists()
        compose.onNodeWithText("Convert to JPEG").assertIsEnabled()
    }

    @Test fun choosingOutputFolderKeepsRawInputAcrossRecreation() {
        pickPhotos("DSC0001.ARW", "DSC0002.ARW")
        compose.onNodeWithText("2 selected photos").assertExists()
        pickTree("raw-result", "Choose output folder")
        compose.onNodeWithText("2 selected photos").assertExists()
        compose.onNodeWithText("raw-result").assertExists()
        compose.onNodeWithText("0 RAW", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Convert 2 photos").assertIsEnabled()
        compose.activityRule.scenario.recreate()
        awaitText("2 selected photos")
        compose.onNodeWithText("raw-result").assertExists()

        pickTree("raw-test-folder", "Source folder", await = "4 RAW")
        compose.onNodeWithText("raw-test-folder").assertExists()
        compose.onNodeWithText("raw-result").assertExists()
        compose.onNodeWithText("1 unsupported", substring = true).assertExists()
        compose.onNodeWithText("0 RAW", substring = true).assertDoesNotExist()
        compose.onNodeWithText("No supported RAW files found in this folder").assertDoesNotExist()
        compose.onNodeWithText("Convert 4 photos").assertIsEnabled()
        pickTree("raw-result", "raw-result", await = "4 RAW")
        compose.onNodeWithText("raw-test-folder").assertExists()
        compose.onNodeWithText("Convert 4 photos").assertIsEnabled()
        compose.activityRule.scenario.recreate()
        awaitText("4 RAW")
        awaitText("raw-test-folder")
        compose.onNodeWithText("raw-result").assertExists()
        compose.onNodeWithText("Convert 4 photos").assertIsEnabled()
    }

    private fun awaitText(text: String) {
        compose.waitUntil(15_000) {
            runCatching { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.waitForIdle()
    }

    private fun pickPhotos(vararg names: String) {
        val clip = ClipData.newRawUri("RAW photos", document(names.first()))
        names.drop(1).forEach { clip.addItem(ClipData.Item(document(it))) }
        val result = Intent().apply {
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val filter = IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
            addDataType("*/*")
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        val monitor = instrumentation.addMonitor(filter, Instrumentation.ActivityResult(Activity.RESULT_OK, result), true)
        try {
            compose.onNode(hasText("Add photos") and hasClickAction()).performClick()
            awaitText(names.first())
        } finally { instrumentation.removeMonitor(monitor) }
    }

    private fun pickTree(name: String, clickLabel: String, await: String? = null) {
        val result = Intent().apply {
            data = tree(name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        val monitor = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_OPEN_DOCUMENT_TREE),
            Instrumentation.ActivityResult(Activity.RESULT_OK, result),
            true,
        )
        try {
            compose.onNode(hasText(clickLabel) and hasClickAction()).performScrollTo().performClick()
            if (await != null) awaitText(await) else compose.waitForIdle()
        } finally { instrumentation.removeMonitor(monitor) }
    }
}
