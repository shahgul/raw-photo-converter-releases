package com.shahgul.rawphotoconverter.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.shahgul.rawphotoconverter.conversion.ConversionCancellation
import com.shahgul.rawphotoconverter.conversion.ConversionPolicy
import com.shahgul.rawphotoconverter.conversion.RawInput
import android.util.Log

sealed interface RawSourceSummary {
    val label: String

    data class Single(
        override val label: String,
        val uri: Uri,
        val sizeBytes: Long?,
        val metadata: RawMetadata,
    ) : RawSourceSummary

    data class Folder(
        override val label: String,
        val uri: Uri,
        val rawCount: Int,
        val totalBytes: Long,
    ) : RawSourceSummary
}

data class RawMetadata(
    val camera: String?,
    val lens: String?,
    val iso: String?,
    val exposure: String?,
    val aperture: String?,
    val focalLength: String?,
    val dateTimeOriginal: String?,
    val orientation: Int?,
)

class RawSourceRepository(private val context: Context) {
    private val resolver = context.contentResolver

    fun persistReadPermission(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (error: SecurityException) {
            Log.w("RawSource", "Provider grants read access only for the current session", error)
        }
    }

    suspend fun inspectRaw(uri: Uri): RawSourceSummary.Single = withContext(Dispatchers.IO) {
        val (name, size) = queryNameAndSize(uri)
        ConversionPolicy.jpegName(name ?: error("The selected provider did not supply a filename."))
        val metadata = resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val exif = ExifInterface(descriptor.fileDescriptor)
            RawMetadata(
                camera = exif.getAttribute(ExifInterface.TAG_MODEL),
                lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
                iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
                    ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS),
                exposure = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
                aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER),
                focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
                dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED,
                ),
            )
        } ?: error("The selected RAW cannot be read. Select it again.")

        RawSourceSummary.Single(
            label = name ?: "Selected RAW",
            uri = uri,
            sizeBytes = size,
            metadata = metadata,
        )
    }

    suspend fun inspectFolder(uri: Uri): RawSourceSummary.Folder = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri)
            ?: error("The selected folder cannot be opened.")
        check(root.isDirectory && root.canRead()) { "The selected folder is not readable. Select it again." }

        val raws = root.listFiles().filter { file ->
            file.isFile && file.name?.endsWith(".arw", ignoreCase = true) == true
        }

        RawSourceSummary.Folder(
            label = root.name ?: "Selected folder",
            uri = uri,
            rawCount = raws.size,
            totalBytes = raws.sumOf { it.length().coerceAtLeast(0L) },
        )
    }

    suspend fun listFolderRaws(uri: Uri, cancellation: ConversionCancellation): List<RawInput> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri) ?: error("The source folder cannot be opened.")
        check(root.isDirectory && root.canRead()) { "The source folder is not readable. Select it again." }
        root.listFiles().mapNotNull { file ->
            cancellation.check()
            val name = file.name
            if (file.isFile && name?.endsWith(".arw", ignoreCase = true) == true) RawInput(file.uri, name) else null
        }.sortedBy { it.label.lowercase(java.util.Locale.ROOT) }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String?, Long?> {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                return name to size
            }
        }
        return null to null
    }
}
