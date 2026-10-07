package com.shahgul.rawphotoconverter.conversion
import android.content.Context
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class CameraPreviewTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun cameraCopyKeepsPixelsButRemovesPrivateMetadata() {
        val source = fixture()
        val output = File.createTempFile("camera-output", ".jpg", context.cacheDir)
        try {
            ExifInterface(source).apply {
                setAttribute(ExifInterface.TAG_MODEL, "Private camera")
                setAttribute(ExifInterface.TAG_ORIENTATION, "6")
                setLatLong(12.0, 77.0); saveAttributes()
            }
            val bytes = source.readBytes()
            val result = CameraPreviewEncoder.prepareBytes(bytes, source, output,
                ConversionRecipe(mode = ConversionMode.CAMERA_LOOK, maxLongEdge = 0, preserveMetadata = false), ConversionCancellation()) {}
            assertEquals(64, result.width); assertEquals(96, result.height)
            assertEquals(0, result.choice.quality)
            assertArrayEquals(CameraPreviewEncoder.withoutPrivateMetadata(bytes), CameraPreviewEncoder.withoutPrivateMetadata(output.readBytes()))
            val saved = ExifInterface(output)
            assertNull(saved.getAttribute(ExifInterface.TAG_MODEL)); assertNull(saved.latLong)
            assertEquals(6, saved.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
        } finally { source.delete(); output.delete() }
    }
    @Test fun cameraResizePreservesAspectAndSupportedMetadata() {
        val source = fixture(768, 512)
        val output = File.createTempFile("camera-resize", ".jpg", context.cacheDir)
        try {
            ExifInterface(source).apply { setAttribute(ExifInterface.TAG_MODEL, "Test camera"); saveAttributes() }
            val result = CameraPreviewEncoder.prepareBytes(source.readBytes(), source, output,
                ConversionRecipe(mode = ConversionMode.CAMERA_LOOK, maxLongEdge = 256), ConversionCancellation()) {}
            assertEquals(256, result.width); assertEquals(171, result.height)
            assertTrue(result.choice.quality in 80..96)
            assertEquals("Test camera", ExifInterface(output).getAttribute(ExifInterface.TAG_MODEL))
            assertThrows(IllegalArgumentException::class.java) { CameraPreviewEncoder.withoutPrivateMetadata(byteArrayOf(1,2,3,4)) }
        } finally { source.delete(); output.delete() }
    }
    private fun fixture(width: Int = 96, height: Int = 64): File {
        val source = File.createTempFile("camera-fixture", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.rgb(35, 20, 15))
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } finally { bitmap.recycle() }
        return source
    }
}
