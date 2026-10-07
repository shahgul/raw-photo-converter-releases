package com.shahgul.rawphotoconverter.conversion

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
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
        val name = ConversionPolicy.jpegName(input.label)
        fun existing() = folder.listFiles().any { it.name?.equals(name, ignoreCase = true) == true }
        if (existing()) return ConversionResult(input.label, skipped = true)
        check(folder.isDirectory && folder.canWrite()) { "The output folder is not writable. Select it again." }
        val work = File(context.cacheDir, "raw-conversion-${UUID.randomUUID()}")
        check(work.mkdir()) { "Unable to create temporary conversion storage." }
        var output: DocumentFile? = null
        var ownsOutput = false
        var saved = false
        try {
            phase("Reading RAW")
            val raw = File(work, "source.arw")
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
            val jpeg = File(work, "output.jpg")
            val prepared = try {
                if (recipe.mode == ConversionMode.CAMERA_LOOK) {
                    CameraPreviewEncoder.prepare(raw, jpeg, recipe, cancellation, phase)
                } else {
                    phase("Developing RAW")
                    val bitmap = NativeRawDecoder.decode(raw.absolutePath, recipe.maxLongEdge, cancellation)
                    try {
                        PreparedJpeg(JpegEncoder.encode(bitmap, raw, jpeg, recipe, cancellation, phase = phase), bitmap.width, bitmap.height)
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
            phase("Saving JPEG")
            // Check again after development: never open an existing document for write.
            if (existing()) return ConversionResult(input.label, skipped = true)
            val stagingName = ".raw-photo-converter-${UUID.randomUUID()}.pending.jpg"
            output = folder.createFile("image/jpeg", stagingName) ?: error("The output provider could not create a temporary JPEG.")
            val document = output
            check(document.uri != input.uri) { "The output provider returned the original RAW URI." }
            check(document.name == stagingName) { "The output provider changed the temporary filename. No JPEG was saved." }
            ownsOutput = true
            val preferences = context.getSharedPreferences("conversion", Context.MODE_PRIVATE)
            check(preferences.edit().putString("pendingOutput", document.uri.toString()).commit()) {
                "Unable to record output recovery information. No JPEG was saved."
            }
            context.contentResolver.openOutputStream(document.uri, "w")?.use { destination ->
                jpeg.inputStream().use { source ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        cancellation.check()
                        val count = source.read(buffer)
                        if (count < 0) break
                        destination.write(buffer, 0, count)
                    }
                }
                destination.flush()
            } ?: error("Unable to write the JPEG in the selected output folder.")
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
            } ?: error("The saved JPEG could not be verified.")
            check(bytes == choice.bytes) { "The output provider saved an incomplete JPEG." }
            cancellation.check()
            if (existing()) return ConversionResult(input.label, skipped = true)
            check(document.renameTo(name)) { "The output provider cannot finalize JPEG filenames. Choose another output folder." }
            check(document.name == name) { "The output provider changed the final filename. No JPEG was saved." }
            saved = true
            preferences.edit().remove("pendingOutput").commit()
            return ConversionResult(input.label, document.uri, choice.quality.takeIf { it > 0 }, bytes, width, height, choice.targetMet, cameraLook = recipe.mode == ConversionMode.CAMERA_LOOK)
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
