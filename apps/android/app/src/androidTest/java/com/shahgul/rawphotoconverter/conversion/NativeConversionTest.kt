package com.shahgul.rawphotoconverter.conversion

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

class NativeConversionTest {
    @Test fun cameraModeReportsMissingPreviewWithoutDeveloping() {
        val source = syntheticDng(1)
        try {
            val error = assertThrows(IllegalStateException::class.java) { NativeRawDecoder.cameraPreview(source.absolutePath, ConversionCancellation()) }
            assertTrue(error.message.orEmpty().contains("preview"))
            assertThrows(CancellationException::class.java) { NativeRawDecoder.cameraPreview(source.absolutePath, ConversionCancellation().apply { cancelled = true }) }
        } finally { source.delete() }
    }
    @Test fun originalRawResolutionKeepsAvailablePixels() {
        val source = syntheticDng(6, width = 768, height = 512)
        try {
            val bitmap = NativeRawDecoder.decode(source.absolutePath, 0, ConversionCancellation())
            try { assertEquals(512, bitmap.width); assertEquals(768, bitmap.height) } finally { bitmap.recycle() }
        } finally { source.delete() }
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun developsBayerPixelsAndNormalizesPortraitOrientation() {
        for ((orientation, expected) in listOf(1 to PixelSize(96, 64), 6 to PixelSize(64, 96))) {
            val source = syntheticDng(orientation)
            try {
                val image = NativeRawDecoder.decode(source.absolutePath, 4000, ConversionCancellation())
                try {
                    assertEquals(expected.width, image.width)
                    assertEquals(expected.height, image.height)
                    assertFalse("Decoder must develop pixels, not return a blank preview", image.getPixel(20, 20) == image.getPixel(40, 40))
                } finally { image.recycle() }
            } finally { source.delete() }
        }
    }

    @Test fun jpegPreservesCameraMetadataAndResetsOrientation() {
        val source = syntheticDng(6)
        val output = File.createTempFile("native-output", ".jpg", context.cacheDir)
        val original = source.readBytes()
        try {
            val image = NativeRawDecoder.decode(source.absolutePath, 4000, ConversionCancellation())
            try {
                JpegEncoder.encode(image, source, output, ConversionRecipe(), ConversionCancellation()) {}
            } finally { image.recycle() }
            val exif = ExifInterface(output)
            assertEquals("Synthetic Bayer", exif.getAttribute(ExifInterface.TAG_MODEL))
            assertEquals(1, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
            assertEquals(1, exif.getAttributeInt(ExifInterface.TAG_COLOR_SPACE, 0))
            assertArrayEquals(original, source.readBytes())
        } finally { source.delete(); output.delete() }
    }

    @Test fun nativeResizePreservesPortraitAspectAndUsesSrgbBitmap() {
        val source = syntheticDng(6, width = 768, height = 512)
        try {
            val image = NativeRawDecoder.decode(source.absolutePath, 256, ConversionCancellation())
            try {
                assertEquals(171, image.width)
                assertEquals(256, image.height)
                assertTrue(image.colorSpace?.isSrgb == true)
            } finally { image.recycle() }
        } finally { source.delete() }
    }

    @Test fun invalidRawAndCancelledDecodeReturnActionableErrors() {
        val source = File.createTempFile("invalid", ".arw", context.cacheDir)
        source.writeText("not a RAW")
        try {
            val error = assertThrows(IllegalStateException::class.java) {
                NativeRawDecoder.decode(source.absolutePath, 4000, ConversionCancellation())
            }
            assertTrue(error.message.orEmpty().contains("Open RAW"))
            val cancellation = ConversionCancellation().apply { cancelled = true }
            assertThrows(CancellationException::class.java) {
                NativeRawDecoder.decode(source.absolutePath, 4000, cancellation)
            }
        } finally { source.delete() }
    }

    @Test fun xmpSurvivesWithNormalizedOrientationAndDimensions() {
        val packet = """<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description xmlns:tiff="http://ns.adobe.com/tiff/1.0/" xmlns:dc="http://purl.org/dc/elements/1.1/" tiff:Orientation="6" tiff:ImageWidth="96" tiff:ImageLength="64" dc:creator="Photographer"/></rdf:RDF></x:xmpmeta>"""
        val source = syntheticDng(6, packet)
        val output = File.createTempFile("xmp-output", ".jpg", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888)
            try {
                JpegEncoder.encode(bitmap, source, output, ConversionRecipe(), ConversionCancellation()) {}
            } finally { bitmap.recycle() }
            val xmp = ExifInterface(output).getAttribute(ExifInterface.TAG_XMP).orEmpty()
            assertTrue(xmp.contains("tiff:Orientation=\"1\""))
            assertTrue(xmp.contains("tiff:ImageWidth=\"64\""))
            assertTrue(xmp.contains("tiff:ImageLength=\"96\""))
            assertTrue(xmp.contains("Photographer"))
        } finally { source.delete(); output.delete() }
    }

    @Test fun xmpRejectsDocumentTypesWithoutReadingExternalEntities() {
        val source = syntheticDng(1, "<!DOCTYPE x [<!ENTITY external SYSTEM 'file:///private'>]><x>&external;</x>")
        val output = File.createTempFile("unsafe-xmp", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
        try {
            assertThrows(IllegalArgumentException::class.java) {
                JpegEncoder.encode(bitmap, source, output, ConversionRecipe(), ConversionCancellation()) {}
            }
        } finally { bitmap.recycle(); source.delete(); output.delete() }
    }

    // An uncompressed Bayer DNG with no embedded JPEG. Native tests must exercise
    // RAW development; decoding a camera thumbnail would not satisfy this fixture.
    internal fun syntheticDng(orientation: Int, xmp: String? = null, width: Int = 96, height: Int = 64): File {
        data class Tag(val id: Int, val type: Int, val count: Int, val bytes: ByteArray)
        fun shorts(vararg values: Int) = ByteBuffer.allocate(values.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { values.forEach { putShort(it.toShort()) } }.array()
        fun longs(vararg values: Int) = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { values.forEach { putInt(it) } }.array()
        fun ascii(value: String) = (value + "\u0000").toByteArray(Charsets.US_ASCII)
        val tags = mutableListOf<Tag>()
        fun short(id: Int, value: Int) { tags += Tag(id, 3, 1, shorts(value)) }
        fun long(id: Int, value: Int) { tags += Tag(id, 4, 1, longs(value)) }
        fun text(id: Int, value: String) { val bytes = ascii(value); tags += Tag(id, 2, bytes.size, bytes) }
        long(256, width); long(257, height); short(258, 16); short(259, 1); short(262, 32803)
        text(271, "Test"); text(272, "Synthetic Bayer"); long(273, 0); short(274, orientation)
        short(277, 1); long(278, height); long(279, width * height * 2); short(284, 1)
        tags += Tag(33421, 3, 2, shorts(2, 2))
        tags += Tag(33422, 1, 4, byteArrayOf(0, 1, 1, 2))
        tags += Tag(50706, 1, 4, byteArrayOf(1, 4, 0, 0))
        tags += Tag(50707, 1, 4, byteArrayOf(1, 1, 0, 0))
        text(50708, "Synthetic Bayer")
        long(50717, 65535)
        tags += Tag(50721, 10, 9, longs(1, 1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 1))
        tags += Tag(50728, 5, 3, longs(1, 1, 1, 1, 1, 1))
        short(50778, 21)
        xmp?.toByteArray(Charsets.UTF_8)?.let { tags += Tag(700, 1, it.size, it) }
        tags.sortBy { it.id }
        val dataStart = 8 + 2 + tags.size * 12 + 4
        val pixelStart = dataStart + tags.sumOf { if (it.bytes.size > 4) it.bytes.size else 0 }
        val buffer = ByteBuffer.allocate(pixelStart + width * height * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put('I'.code.toByte()).put('I'.code.toByte()).putShort(42).putInt(8)
        buffer.putShort(tags.size.toShort())
        var offset = dataStart
        for (tag in tags) {
            buffer.putShort(tag.id.toShort()).putShort(tag.type.toShort()).putInt(tag.count)
            when {
                tag.id == 273 -> buffer.putInt(pixelStart)
                tag.bytes.size <= 4 -> buffer.put(tag.bytes.copyOf(4))
                else -> { buffer.putInt(offset); val position = buffer.position(); buffer.position(offset); buffer.put(tag.bytes); buffer.position(position); offset += tag.bytes.size }
            }
        }
        buffer.putInt(0)
        buffer.position(pixelStart)
        repeat(height) { y -> repeat(width) { x -> buffer.putShort((1000 + x * 24000 / width + y * 12000 / height).toShort()) } }
        return File.createTempFile("bayer-", ".dng", context.cacheDir).apply { writeBytes(buffer.array()) }
    }
}
