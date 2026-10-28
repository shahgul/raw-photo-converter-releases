package com.shahgul.rawphotoconverter.ui
import com.shahgul.rawphotoconverter.conversion.*
import org.junit.Assert.*
import org.junit.Test
class ProcessingReportTest {
    @Test fun reportIncludesSettingsFailuresAndCompletedElapsedTime() {
        val state = BatchState(phase = "Finished with errors", total = 2, startedAtMs = 1000, finishedAtMs = 63000,
            settings = BatchSettings(ConversionMode.CAMERA_LOOK, OutputResolution.ORIGINAL, false, "JPEGs"),
            results = listOf(ConversionResult("first.ARW", skipped = true), ConversionResult("second.ARW", error = "Preview missing")),
            events = listOf(BatchEvent(1200, "Extracting camera JPEG")))
        val report = conversionReport(state)
        for (text in listOf("Camera look", "Original resolution", "JPEG", "sRGB", "Metadata: disabled", "Destination: JPEGs", "Elapsed: 01:02", "Skipped", "Preview missing", "00:01 Extracting")) assertTrue(text, report.contains(text))
    }
    @Test fun smoothProgressReachesFullOnlyAtTheEndOfTheSweep() {
        val sweep = 1_800L
        fun shown(elapsed: Long, finished: Long?, completed: Int, total: Int) =
            smoothProcessingProgress(elapsed, finished, completed, total, sweep).toDouble()
        assertEquals(0.0, shown(0, null, 0, 1), 0.001)
        val mid = shown(sweep / 2, null, 0, 1)
        assertTrue(mid > 0.2 && mid < 0.9)
        assertTrue(shown(sweep + 5_000, null, 0, 1) < 1.0)
        assertTrue(shown(200, 100, 1, 1) < 1.0)
        assertEquals(1.0, shown(sweep, 100, 1, 1), 0.001)
        assertTrue(shown(8_000, 8_000, 1, 1) < 1.0)
        assertEquals(1.0, shown(8_320, 8_000, 1, 1), 0.001)
        val early = smoothProcessingProgress(400, null, 1, 4, sweep)
        val later = smoothProcessingProgress(400, null, 3, 4, sweep)
        assertTrue(later > early)
    }

    @Test fun fileProgressStaysUnknownUntilASingleFileFinishes() {
        assertEquals(null, completedFileProgress(completed = 0, total = 1))
        assertEquals(1f, completedFileProgress(completed = 1, total = 1))
        assertEquals(0.5f, completedFileProgress(completed = 1, total = 2))
    }

    @Test fun skippedResultsDescribeTheExistingFile() {
        val text = resultDescription(
            ConversionResult("photo.ARW", skipped = true, width = 4000, height = 2664, sizeBytes = 2_400_000),
        )
        assertTrue(text.contains("already exists"))
        assertTrue(text.contains("4000 × 2664"))
        assertTrue(resultFacts(ConversionResult("photo.ARW", error = "Invalid RAW")).any { it.first == "Error" })
    }

    @Test fun cameraCopiesDoNotInventAQualityValue() {
        val text = resultDescription(ConversionResult("photo.ARW", width = 1616, height = 1080, sizeBytes = 200000, cameraLook = true))
        assertTrue(text.contains("Camera look")); assertFalse(text.contains("Qnull"))
        assertEquals("00:00", formatElapsed(-1)); assertEquals("61:01", formatElapsed(3661000))
    }
}
