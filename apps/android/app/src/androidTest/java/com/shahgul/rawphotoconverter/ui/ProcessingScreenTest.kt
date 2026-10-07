package com.shahgul.rawphotoconverter.ui
import android.content.ClipboardManager
import android.content.ContentValues
import android.provider.MediaStore
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.shahgul.rawphotoconverter.MainActivity
import com.shahgul.rawphotoconverter.conversion.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
class ProcessingScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @After fun reset() {
        compose.runOnUiThread { ConversionStore.publish(BatchState()) }
        // Keep ActivityScenario able to finish cleanly after notification-style reopen.
        runCatching { compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED) }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val resolver = compose.activity.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RawConverterTests")
        })!!
        try { resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
    private fun running() = BatchState(running = true, current = "photo.ARW", phase = "Developing RAW", total = 2,
        startedAtMs = SystemClock.elapsedRealtime(), sourceLabel = "photo.ARW",
        settings = BatchSettings(ConversionMode.DEVELOP_RAW, OutputResolution.STANDARD, true, "JPEGs"))
    private fun awaitCancelVisible() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("processing-cancel").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("processing-cancel").assertIsDisplayed()
    }
    @Test fun backRecreationAndNotificationResumeKeepConversionVisible() {
        compose.runOnUiThread { ConversionStore.publish(running()) }
        awaitCancelVisible()
        capture("processing-running")
        compose.onNodeWithText("Back to setup").performClick()
        compose.onNodeWithText("View processing").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("View processing").assertIsDisplayed()
        assertTrue(ConversionStore.state.value.running)
        // Deliver the notification deep-link without startActivity(CLEAR_TOP), which leaves
        // ActivityScenario stuck in PAUSED and fails rule teardown on CI emulators.
        compose.activityRule.scenario.onActivity { it.deliverShowProcessingRequest() }
        awaitCancelVisible()
        assertTrue(ConversionStore.state.value.running)
    }
    @Test fun logsReportAndResultsScreenShowAfterCompletion() {
        compose.runOnUiThread { ConversionStore.publish(running()) }
        compose.onNodeWithText("Converter logs").assertIsDisplayed()
        compose.onNodeWithText("Recipe").performClick()
        compose.onNodeWithText("MODE").assertExists()
        compose.onNodeWithText("Converter logs").performClick()
        capture("processing-details")
        compose.onNodeWithText("Copy").performClick()
        compose.runOnUiThread {
            val report = compose.activity.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
            assertTrue(report.contains("Developing RAW")); assertTrue(report.contains("Standard (4000 px)"))
            ConversionStore.publish(ConversionStore.state.value.copy(running = false, phase = "Finished with errors",
                finishedAtMs = SystemClock.elapsedRealtime(),
                results = listOf(ConversionResult("photo.ARW", error = "Invalid RAW"), ConversionResult("existing.ARW", skipped = true))))
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Failed: Invalid RAW").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Failed: Invalid RAW").assertExists()
        compose.onNodeWithText("Convert more").assertIsDisplayed()
        compose.onNodeWithText("Results").assertIsDisplayed()
        capture("processing-partial-failure")
        compose.onNodeWithText("Convert more").performClick()
        compose.onNodeWithText("View conversion results").assertExists()
        assertFalse(ConversionStore.state.value.running)
    }
}
