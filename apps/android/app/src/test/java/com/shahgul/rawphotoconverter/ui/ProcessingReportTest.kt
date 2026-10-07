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
        for (text in listOf("Camera look", "Original resolution", "Metadata: disabled", "Destination: JPEGs", "Elapsed: 01:02", "Skipped", "Preview missing", "00:01 Extracting")) assertTrue(text, report.contains(text))
    }
    @Test fun cameraCopiesDoNotInventAQualityValue() {
        val text = resultDescription(ConversionResult("photo.ARW", width = 1616, height = 1080, sizeBytes = 200000, cameraLook = true))
        assertTrue(text.contains("Camera look")); assertFalse(text.contains("Qnull"))
        assertEquals("00:00", formatElapsed(-1)); assertEquals("61:01", formatElapsed(3661000))
    }
}
