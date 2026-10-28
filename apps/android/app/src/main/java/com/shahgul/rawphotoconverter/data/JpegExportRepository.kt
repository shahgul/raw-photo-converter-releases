// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.shahgul.rawphotoconverter.conversion.CameraPreviewEncoder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.util.UUID

data class ExportOptions(val removeExif: Boolean = false)

/** Export transformations operate on private copies, never the saved JPEG or RAW. */
class JpegExportRepository(private val context: Context) {
    suspend fun prepare(uris: List<Uri>, options: ExportOptions): List<Uri> {
        if (!options.removeExif) return uris
        val root = File(context.cacheDir, "jpeg-exports").apply { check(mkdirs() || isDirectory) }
        // Keep successful shares available to receivers; expire them on a later export.
        val expired = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        root.listFiles()?.filter { it.isDirectory && it.lastModified() < expired }?.forEach { it.deleteRecursively() }
        val session = File(root, UUID.randomUUID().toString()).apply { check(mkdir()) }
        try {
            return uris.distinct().mapIndexed { index, uri ->
                currentCoroutineContext().ensureActive()
                val displayName = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
                val name = displayName?.substringAfterLast('/')?.substringAfterLast('\\')
                    ?.takeIf { it.endsWith(".jpg", true) || it.endsWith(".jpeg", true) } ?: "photo-${index + 1}.jpg"
                val directory = File(session, index.toString()).apply { check(mkdir()) }
                val copy = File(directory, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    copy.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var size = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            check(size <= 64L * 1024 * 1024) { "This JPEG exceeds the 64 MiB export safety limit." }
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: error("A saved JPEG couldn't be read. Check the output folder.")
                val exif = ExifInterface(copy)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
                if (orientation in 2..8) normalizeOrientation(copy, orientation)
                // Existing lossless sanitizer preserves compressed pixels and ICC profiles.
                val runtime = Runtime.getRuntime()
                val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
                check(copy.length() * 3 < available * 3 / 4) { "Not enough memory to prepare this JPEG safely. Try a smaller export batch or Standard resolution." }
                copy.writeBytes(CameraPreviewEncoder.withoutPrivateMetadata(copy.readBytes()))
                currentCoroutineContext().ensureActive()
                FileProvider.getUriForFile(context, "${context.packageName}.exports", copy)
            }
        } catch (failure: Throwable) {
            session.deleteRecursively()
            throw failure
        }
    }

    private fun normalizeOrientation(file: File, orientation: Int) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val pixels = bounds.outWidth.toLong() * bounds.outHeight
        val runtime = Runtime.getRuntime()
        val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        check(bounds.outWidth > 0 && bounds.outHeight > 0 && pixels <= 50_000_000 && pixels * 8 < available * 3 / 4) {
            "This JPEG is too large to rotate safely for metadata-free export. Share with metadata, or convert at Standard resolution."
        }
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }) ?: error("The saved JPEG couldn't be decoded for export.")
        var rotated: Bitmap? = null
        try {
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> { setRotate(180f); postScale(-1f, 1f) }
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(270f); postScale(-1f, 1f) }
                    8 -> setRotate(270f)
                }
            }
            rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            file.outputStream().use { check(rotated.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "Unable to prepare the export JPEG." } }
        } finally {
            if (rotated !== bitmap) rotated?.recycle()
            bitmap.recycle()
        }
    }
}
