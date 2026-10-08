package com.shahgul.rawphotoconverter.conversion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

data class PreparedJpeg(val choice: QualityChoice, val width: Int, val height: Int)

object CameraPreviewEncoder {
    fun prepare(source: File, output: File, recipe: ConversionRecipe, cancellation: ConversionCancellation,
                phase: (String) -> Unit): PreparedJpeg {
        cancellation.check()
        phase("Extracting camera JPEG")
        return prepareBytes(NativeRawDecoder.cameraPreview(source.absolutePath, cancellation), source, output, recipe, cancellation, phase)
    }

    internal fun prepareBytes(bytes: ByteArray, source: File, output: File, recipe: ConversionRecipe,
                              cancellation: ConversionCancellation, phase: (String) -> Unit): PreparedJpeg {
        cancellation.check()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 50_000_000) {
            "The embedded camera JPEG is invalid or exceeds the 50 MP memory limit. Choose Develop RAW."
        }
        val previewExif = ExifInterface(ByteArrayInputStream(bytes))
        val rawExif = if (recipe.preserveMetadata && !source.extension.equals("cr3", ignoreCase = true)) {
            runCatching { ExifInterface(source) }.getOrNull() ?: previewExif
        } else {
            previewExif
        }
        val orientation = previewExif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
            rawExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)).takeIf { it in 1..8 } ?: 1
        val dimensions = ConversionPolicy.fit(bounds.outWidth, bounds.outHeight, recipe.maxLongEdge)
        val portrait = orientation in 5..8
        fun prepared(choice: QualityChoice) = PreparedJpeg(choice,
            if (portrait) dimensions.height else dimensions.width, if (portrait) dimensions.width else dimensions.height)
        if (dimensions == PixelSize(bounds.outWidth, bounds.outHeight)) {
            // Strip private preview metadata even when preservation is off; ICC profiles stay.
            output.writeBytes(withoutPrivateMetadata(bytes))
            phase("Restoring metadata")
            JpegEncoder.writeMetadata(output, rawExif.takeIf { recipe.preserveMetadata }, dimensions.width, dimensions.height,
                orientation, previewExif.getAttributeInt(ExifInterface.TAG_COLOR_SPACE,
                    rawExif.getAttributeInt(ExifInterface.TAG_COLOR_SPACE, 1)))
            cancellation.check()
            if (output.length() <= recipe.targetBytes) return prepared(QualityChoice(0, output.length(), true))
        }
        phase("Resizing camera JPEG")
        // Decode in sRGB before Android's JPEG encoder; no camera tone/white-balance changes.
        val options = BitmapFactory.Options().apply { inPreferredColorSpace = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB) }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("Unable to decode the embedded camera JPEG. Choose Develop RAW.")
        var resized: Bitmap? = null
        try {
            cancellation.check()
            resized = Bitmap.createScaledBitmap(bitmap, dimensions.width, dimensions.height, true)
            return prepared(JpegEncoder.encode(resized, source, output, recipe, cancellation, orientation, rawExif, phase))
        } finally {
            if (resized !== bitmap) resized?.recycle()
            bitmap.recycle()
        }
    }

    /** Remove EXIF/XMP, IPTC and comments without touching JPEG scan data or ICC. */
    internal fun withoutPrivateMetadata(bytes: ByteArray): ByteArray {
        require(bytes.size >= 4 && bytes[0].toInt() and 255 == 255 && bytes[1].toInt() and 255 == 216) { "Invalid camera JPEG." }
        val result = ByteArrayOutputStream(bytes.size)
        result.write(bytes, 0, 2)
        var offset = 2
        while (offset < bytes.size) {
            val start = offset
            require(bytes[offset].toInt() and 255 == 255) { "Invalid JPEG segment." }
            while (offset < bytes.size && bytes[offset].toInt() and 255 == 255) offset++
            require(offset < bytes.size) { "Truncated camera JPEG." }
            val marker = bytes[offset++].toInt() and 255
            if (marker == 218 || marker == 217) {
                result.write(bytes, start, bytes.size - start)
                return result.toByteArray()
            }
            require(marker !in listOf(0, 216) && marker !in 208..215 && offset + 2 <= bytes.size) { "Invalid JPEG marker." }
            val length = ((bytes[offset].toInt() and 255) shl 8) or (bytes[offset + 1].toInt() and 255)
            require(length >= 2 && offset + length <= bytes.size) { "Truncated JPEG segment." }
            if (marker !in listOf(225, 237, 254)) result.write(bytes, start, offset + length - start)
            offset += length
        }
        error("Camera JPEG has no image data.")
    }
}
