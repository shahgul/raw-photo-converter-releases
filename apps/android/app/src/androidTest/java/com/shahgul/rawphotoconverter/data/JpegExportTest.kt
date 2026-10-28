// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.shahgul.rawphotoconverter.conversion.CameraPreviewEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class JpegExportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root get() = File(context.cacheDir, "jpeg-exports").apply { mkdirs() }
    private fun uri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
    private fun fixture(orientation: Int = 1): File {
        val file = File(root, "fixture-$orientation.jpg")
        val bitmap = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888)
        for (y in 0 until 60) for (x in 0 until 120) bitmap.setPixel(x, y, if (x < 60) Color.RED else Color.BLUE)
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } }
        finally { bitmap.recycle() }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_MODEL, "Private camera")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:10:13 10:00:00")
            setLatLong(12.0, 77.0)
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    @Test fun uncheckedExportUsesOriginalAndCheckedExportRemovesMetadataWithoutChangingPixelsOrOriginal() = runBlocking(Dispatchers.IO) {
        val file = fixture()
        val before = file.readBytes()
        val repository = JpegExportRepository(context)
        assertEquals(listOf(uri(file)), repository.prepare(listOf(uri(file)), ExportOptions()))
        val shared = repository.prepare(listOf(uri(file)), ExportOptions(removeExif = true)).single()
        assertNotEquals(uri(file), shared)
        val bytes = context.contentResolver.openInputStream(shared)!!.use { it.readBytes() }
        assertArrayEquals(CameraPreviewEncoder.withoutPrivateMetadata(before), bytes)
        val metadata = ExifInterface(bytes.inputStream())
        assertNull(metadata.getAttribute(ExifInterface.TAG_MODEL))
        assertNull(metadata.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertNull(metadata.latLong)
        // ExifInterface supplies an UNDEFINED orientation even without an EXIF block.
        assertEquals(ExifInterface.ORIENTATION_UNDEFINED, metadata.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
        assertArrayEquals(before, file.readBytes())
        file.delete()
        Unit
    }

    @Test fun removingExifKeepsRotatedAndMirroredPhotosCorrectlyOriented() = runBlocking(Dispatchers.IO) {
        for (orientation in listOf(2, 6)) {
            val file = fixture(orientation)
            val before = file.readBytes()
            val shared = JpegExportRepository(context).prepare(listOf(uri(file)), ExportOptions(true)).single()
            val bytes = context.contentResolver.openInputStream(shared)!!.use { it.readBytes() }
            assertEquals(ExifInterface.ORIENTATION_UNDEFINED,
                ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
            assertArrayEquals(bytes, CameraPreviewEncoder.withoutPrivateMetadata(bytes))
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            try {
                assertEquals(if (orientation == 6) 60 else 120, bitmap.width)
                assertEquals(if (orientation == 6) 120 else 60, bitmap.height)
                val first = bitmap.getPixel(10, 10)
                if (orientation == 2) assertTrue(Color.blue(first) > Color.red(first))
                else assertTrue(Color.red(first) > Color.blue(first))
            } finally { bitmap.recycle() }
            assertArrayEquals(before, file.readBytes())
            file.delete()
        }
    }

    @Test fun preparationFailureDoesNotLeavePartialShareCopiesOrModifyInputs() = runBlocking(Dispatchers.IO) {
        val good = fixture()
        val bad = File(root, "bad.jpg").apply { writeText("not a JPEG") }
        val before = root.listFiles()!!.filter { it.isDirectory }.map { it.name }.toSet()
        try {
            JpegExportRepository(context).prepare(listOf(uri(good), uri(bad)), ExportOptions(true))
            fail("Malformed JPEG must not be shared with metadata as a fallback")
        } catch (failure: Exception) {
            assertTrue(failure is IllegalArgumentException || failure is java.io.IOException)
        }
        assertEquals(before, root.listFiles()!!.filter { it.isDirectory }.map { it.name }.toSet())
        assertNotNull(ExifInterface(good).getAttribute(ExifInterface.TAG_MODEL))
        good.delete()
        bad.delete()
        Unit
    }
}
