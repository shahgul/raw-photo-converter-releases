package com.shahgul.rawphotoconverter.conversion

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException

data class RawInput(val uri: Uri, val label: String)
data class ConversionResult(
    val source: String,
    val output: Uri? = null,
    val quality: Int? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val targetMet: Boolean? = null,
    val skipped: Boolean = false,
    val error: String? = null,
    val cameraLook: Boolean = false,
    val sourceUri: String? = null,
    val format: OutputFormat = OutputFormat.JPEG,
    val colorGamut: ColorGamut = ColorGamut.SRGB,
)

class RawConversionEngine(private val context: Context) {
    fun convert(
        input: RawInput,
        folder: DocumentFile,
        recipe: ConversionRecipe,
        cancellation: ConversionCancellation,
        phase: (String) -> Unit,
    ): ConversionResult {
        cancellation.check()
        val name = ConversionPolicy.outputName(input.label, recipe.format)
        fun existingFile() = folder.listFiles().firstOrNull { it.name?.equals(name, ignoreCase = true) == true }
        existingFile()?.let { return skippedExisting(input, it, recipe) }
        check(folder.isDirectory && folder.canWrite()) { "The output folder is not writable. Select it again." }
        val work = File(context.cacheDir, "raw-conversion-${UUID.randomUUID()}")
        check(work.mkdir()) { "Unable to create temporary conversion storage." }
        var output: DocumentFile? = null
        var ownsOutput = false
        var saved = false
        try {
            phase("Reading RAW")
            val extension = input.label.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
            val raw = File(work, "source.$extension")
            context.contentResolver.openInputStream(input.uri)?.use { source ->
                raw.outputStream().use { destination ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        cancellation.check()
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= 512L * 1024 * 1024) { "This RAW exceeds the alpha's 512 MiB input limit." }
                        destination.write(buffer, 0, count)
                    }
                }
            } ?: error("The RAW cannot be read. Select the file again.")
            check(raw.length() > 0) { "The selected RAW is empty." }
            cancellation.check()
            val convertedFile = File(work, "output.${recipe.format.extension}")
            val prepared = try {
                if (recipe.mode == ConversionMode.CAMERA_LOOK) {
                    CameraPreviewEncoder.prepare(raw, convertedFile, recipe, cancellation, phase)
                } else {
                    phase("Developing RAW")
                    val bitmap = NativeRawDecoder.decode(raw.absolutePath, recipe.maxLongEdge, cancellation)
                    try {
                        val sourceMetadata = when {
                            !recipe.preserveMetadata -> null
                            extension != "cr3" -> ExifInterface(raw)
                            else -> {
                                val preview = try {
                                    NativeRawDecoder.cameraPreview(raw.absolutePath, cancellation)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (previewError: Exception) {
                                    throw IllegalStateException(
                                        "Cannot read metadata from this Canon CR3. Turn off metadata preservation to convert it.",
                                        previewError,
                                    )
                                }
                                ExifInterface(ByteArrayInputStream(preview))
                            }
                        }
                        PreparedJpeg(
                            JpegEncoder.encode(bitmap, raw, convertedFile, recipe, cancellation, sourceMetadata = sourceMetadata, phase = phase),
                            bitmap.width,
                            bitmap.height,
                        )
                    } finally { bitmap.recycle() }
                }
            } catch (error: UnsatisfiedLinkError) {
                throw IllegalStateException("The native RAW engine is unavailable for this device. Install a supported APK.", error)
            } catch (error: OutOfMemoryError) {
                throw IllegalStateException("Not enough memory for this output. Choose Standard resolution or Camera look and retry.", error)
            }
            val choice = prepared.choice
            val width = prepared.width
            val height = prepared.height
            cancellation.check()
            phase("Saving ${recipe.format.label}")
            // Check again after development: never open an existing document for write.
            existingFile()?.let { return skippedExisting(input, it, recipe) }
            val stagingName = ".raw-photo-converter-${UUID.randomUUID()}.pending.${recipe.format.extension}"
            output = folder.createFile(recipe.format.mimeType, stagingName) ?: error("The output provider could not create a temporary ${recipe.format.label}.")
            val document = output
            check(document.uri != input.uri) { "The output provider returned the original RAW URI." }
            check(document.name == stagingName) { "The output provider changed the temporary filename. No ${recipe.format.label} was saved." }
            ownsOutput = true
            val preferences = context.getSharedPreferences("conversion", Context.MODE_PRIVATE)
            check(preferences.edit().putString("pendingOutput", document.uri.toString()).commit()) {
                "Unable to record output recovery information. No ${recipe.format.label} was saved."
            }
            context.contentResolver.openOutputStream(document.uri, "w")?.use { destination ->
                convertedFile.inputStream().use { source ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        cancellation.check()
                        val count = source.read(buffer)
                        if (count < 0) break
                        destination.write(buffer, 0, count)
                    }
                }
                destination.flush()
            } ?: error("Unable to write the ${recipe.format.label} in the selected output folder.")
            cancellation.check()
            // Re-read the provider's document, rather than trusting a successful close.
            val bytes = context.contentResolver.openInputStream(document.uri)?.use { stream ->
                var total = 0L
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    cancellation.check()
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                }
                total
            } ?: error("The saved ${recipe.format.label} could not be verified.")
            check(bytes == choice.bytes) { "The output provider saved an incomplete ${recipe.format.label}." }
            cancellation.check()
            existingFile()?.let { return skippedExisting(input, it, recipe) }
            check(document.renameTo(name)) { "The output provider cannot finalize filenames. Choose another output folder." }
            check(document.name == name) { "The output provider changed the final filename. No ${recipe.format.label} was saved." }
            saved = true
            preferences.edit().remove("pendingOutput").commit()
            return ConversionResult(
                input.label,
                document.uri,
                choice.quality.takeIf { it > 0 },
                bytes,
                width,
                height,
                choice.targetMet,
                cameraLook = recipe.mode == ConversionMode.CAMERA_LOOK,
                sourceUri = input.uri.toString(),
                format = recipe.format,
                colorGamut = recipe.colorGamut,
            )
        } finally {
            if (!saved && ownsOutput && output?.uri != input.uri) {
                output?.let { document ->
                    try {
                        if (!document.delete()) Log.w("RawConversion", "Failed to remove incomplete output ${document.uri}")
                    } catch (error: Exception) { Log.w("RawConversion", "Incomplete output cleanup failed", error) }
                }
            }
            if (!work.deleteRecursively()) Log.w("RawConversion", "Temporary conversion cleanup failed: ${work.name}")
            if (saved || output == null || !output.exists()) {
                context.getSharedPreferences("conversion", Context.MODE_PRIVATE).edit().remove("pendingOutput").commit()
            }
        }
    }

    /** Existing output stays untouched. Bounds and size are read so results can describe that file. */
    private fun skippedExisting(input: RawInput, file: DocumentFile, recipe: ConversionRecipe): ConversionResult {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        }
        return ConversionResult(
            source = input.label,
            output = file.uri,
            sizeBytes = file.length().takeIf { it > 0 },
            width = options.outWidth.takeIf { it > 0 },
            height = options.outHeight.takeIf { it > 0 },
            skipped = true,
            cameraLook = recipe.mode == ConversionMode.CAMERA_LOOK,
            sourceUri = input.uri.toString(),
            format = recipe.format,
            colorGamut = recipe.colorGamut,
        )
    }

    fun cleanInterruptedTemps() {
        val preferences = context.getSharedPreferences("conversion", Context.MODE_PRIVATE)
        preferences.getString("pendingOutput", null)?.let { uri ->
            val document = DocumentFile.fromSingleUri(context, Uri.parse(uri))
            // A renamed, verified final JPEG must never be deleted during recovery.
            val name = document?.name.orEmpty()
            if (name.startsWith(".raw-photo-converter-") && name.endsWith(".pending.jpg")) {
                check(document?.delete() == true) { "Unable to remove an interrupted temporary JPEG. Re-select its output folder or remove $name before retrying." }
            }
            preferences.edit().remove("pendingOutput").commit()
        }
        context.cacheDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("raw-conversion-") }?.forEach {
            if (!it.deleteRecursively()) Log.w("RawConversion", "Interrupted temporary cleanup failed: ${it.name}")
        }
    }
}
