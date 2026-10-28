package com.shahgul.rawphotoconverter.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import com.shahgul.rawphotoconverter.conversion.ConversionCancellation
import com.shahgul.rawphotoconverter.conversion.ConversionPolicy
import com.shahgul.rawphotoconverter.conversion.NativeRawDecoder
import com.shahgul.rawphotoconverter.conversion.RawInput
import android.util.Log
import android.provider.DocumentsContract
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID

sealed interface RawSourceSummary {
    val label: String

    data class Single(
        override val label: String,
        val uri: Uri,
        val sizeBytes: Long?,
        val metadata: RawMetadata,
        val preview: RawPreviewState = RawPreviewState.Loading,
    ) : RawSourceSummary

    data class Folder(
        override val label: String,
        val uri: Uri,
        val rawCount: Int,
        val totalBytes: Long,
        val ignoredCount: Int = 0,
    ) : RawSourceSummary

    data class Photos(val photos: List<SelectedRaw>) : RawSourceSummary {
        override val label: String get() = "${photos.size} selected photos"
    }
}

data class SelectedRaw(val uri: Uri, val label: String, val sizeBytes: Long?)
data class SelectionInspection(val photos: List<SelectedRaw>, val failures: List<String>)

sealed interface RawPreviewState {
    data object Loading : RawPreviewState
    data class Ready(val bitmap: Bitmap) : RawPreviewState
    data class Unavailable(val message: String) : RawPreviewState
}

data class RawMetadata(
    val camera: String? = null,
    val lens: String? = null,
    val iso: String? = null,
    val exposure: String? = null,
    val aperture: String? = null,
    val focalLength: String? = null,
    val dateTimeOriginal: String? = null,
    val orientation: Int? = null,
)

class RawSourceRepository(private val context: Context) {
    private val resolver = context.contentResolver

    suspend fun inspectSharedContent(uris: List<Uri>): SelectionInspection = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<Uri>()
        val failures = mutableListOf<String>()
        for (uri in uris.distinct()) {
            currentCoroutineContext().ensureActive()
            try {
                require(uri.scheme == "content") { "Use Add photos or Folder for non-content file paths." }
                val tree = DocumentsContract.isTreeUri(uri)
                val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                val directory = (tree && documentId == null) || resolver.getType(uri) == DocumentsContract.Document.MIME_TYPE_DIR
                if (!directory) {
                    candidates += uri
                    continue
                }
                val id = documentId ?: DocumentsContract.getTreeDocumentId(uri)
                val children = if (tree) DocumentsContract.buildChildDocumentsUriUsingTree(uri, id)
                    else DocumentsContract.buildChildDocumentsUri(requireNotNull(uri.authority), id)
                resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val typeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val name = cursor.getString(nameColumn) ?: "Unnamed item"
                        when {
                            cursor.getString(typeColumn) == DocumentsContract.Document.MIME_TYPE_DIR -> failures += "$name: subfolders aren't included."
                            !ConversionPolicy.isSupportedRaw(name) -> failures += "$name: unsupported file (use ARW, CR2, or CR3)."
                            else -> candidates += if (tree) DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(idColumn))
                                else DocumentsContract.buildDocumentUri(requireNotNull(uri.authority), cursor.getString(idColumn))
                        }
                    }
                } ?: error("Shared folder couldn't be listed. Choose it using Folder.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("RawShare", "Shared content inspection failed", failure)
                failures += "A shared item couldn't be opened. Choose it using Add photos or Folder."
            }
        }
        val inspected = inspectSelection(candidates)
        SelectionInspection(inspected.photos, failures + inspected.failures)
    }

    suspend fun inspectSelection(uris: List<Uri>): SelectionInspection = withContext(Dispatchers.IO) {
        val photos = mutableListOf<SelectedRaw>()
        val failures = mutableListOf<String>()
        for (uri in uris.distinct()) {
            currentCoroutineContext().ensureActive()
            try {
                val (name, size) = queryNameAndSize(uri)
                val label = name ?: error("The provider did not supply a filename.")
                if (!ConversionPolicy.isSupportedRaw(label)) {
                    failures += ConversionPolicy.unsupportedSelectionMessage(label)
                    continue
                }
                ConversionPolicy.jpegName(label)
                resolver.openFileDescriptor(uri, "r")?.use { } ?: error("$label cannot be read. Select it again.")
                photos += SelectedRaw(uri, label, size)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("RawSource", "Selected RAW inspection failed", failure)
                failures += failure.message ?: "A selected photo could not be read."
            }
        }
        SelectionInspection(photos, failures)
    }

    fun persistReadPermission(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (error: SecurityException) {
            Log.w("RawSource", "Provider grants read access only for the current session", error)
        }
    }

    suspend fun inspectRaw(uri: Uri): RawSourceSummary.Single = withContext(Dispatchers.IO) {
        val (name, size) = queryNameAndSize(uri)
        val rawName = name ?: error("The selected provider did not supply a filename.")
        require(ConversionPolicy.isSupportedRaw(rawName)) { ConversionPolicy.unsupportedSelectionMessage(rawName) }
        ConversionPolicy.jpegName(rawName)
        val metadata = resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            try {
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
            } catch (error: Exception) {
                if (!rawName.endsWith(".cr3", ignoreCase = true)) throw error
                Log.i("RawSource", "ExifInterface cannot inspect CR3 metadata before conversion", error)
                RawMetadata()
            }
        } ?: error("The selected RAW cannot be read. Select it again.")

        RawSourceSummary.Single(
            label = rawName,
            uri = uri,
            sizeBytes = size,
            metadata = metadata,
        )
    }

    suspend fun loadPreview(uri: Uri, sizeBytes: Long?): RawPreviewState = withContext(Dispatchers.IO) {
        if (sizeBytes != null && sizeBytes > 512L * 1024 * 1024) {
            return@withContext RawPreviewState.Unavailable(
                "This RAW exceeds the app's 512 MiB input limit, so a preview isn't available.",
            )
        }

        val coroutine = currentCoroutineContext()
        val cancellation = ConversionCancellation()
        val cancellationHandle = coroutine.job.invokeOnCompletion { cause ->
            if (cause != null) cancellation.cancelled = true
        }
        var stagedRaw: File? = null
        try {
            coroutine.ensureActive()
            val raw = File.createTempFile("raw-preview-${UUID.randomUUID()}-", ".cr3", context.cacheDir)
            stagedRaw = raw
            resolver.openInputStream(uri)?.use { input ->
                raw.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var totalBytes = 0L
                    while (true) {
                        coroutine.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        check(totalBytes <= 512L * 1024 * 1024) {
                            "This RAW exceeds the app's 512 MiB input limit, so a preview isn't available."
                        }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("The selected file provider did not open the RAW for preview.")
            check(raw.length() > 0) { "The selected RAW is empty." }
            coroutine.ensureActive()
            val previewBytes = NativeRawDecoder.cameraPreview(raw.absolutePath, cancellation)
            coroutine.ensureActive()
            RawPreviewState.Ready(decodePreview(previewBytes))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unavailable: UnsatisfiedLinkError) {
            RawPreviewState.Unavailable("The camera preview couldn't be loaded. Develop RAW is still available.")
        } catch (failure: Exception) {
            Log.w("RawSource", "Embedded preview load failed (${failure.javaClass.simpleName}): ${failure.message}", failure)
            val previewMissing = failure.message.orEmpty().contains("preview", ignoreCase = true) ||
                failure.message.orEmpty().contains("thumbnail", ignoreCase = true)
            RawPreviewState.Unavailable(
                when {
                    failure.message.orEmpty().contains("512 MiB", ignoreCase = true) -> failure.message!!
                    previewMissing ->
                    "This RAW has no supported embedded JPEG. Camera look needs one; Develop RAW is still available."
                    else -> "The camera preview couldn't be read. Develop RAW is still available."
                },
            )
        } finally {
            cancellationHandle.dispose()
            stagedRaw?.delete()
        }
    }

    private fun decodePreview(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 50_000_000) {
            "The embedded camera preview is invalid or too large."
        }

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 1200) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("The embedded camera preview couldn't be decoded.")
        return try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            val matrix = Matrix().apply {
                if (exif.isFlipped()) postScale(-1f, 1f)
                val rotation = exif.getRotationDegrees()
                if (rotation != 0) postRotate(rotation.toFloat())
            }
            if (matrix.isIdentity()) decoded
            else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } catch (failure: Exception) {
            decoded.recycle()
            throw failure
        }
    }

    suspend fun inspectFolder(uri: Uri): RawSourceSummary.Folder = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri)
            ?: error("The selected folder cannot be opened.")
        check(root.isDirectory && root.canRead()) { "The selected folder is not readable. Select it again." }

        val children = listDirectChildren(uri)
        val raws = children.filter { child ->
            child.name?.let { ConversionPolicy.isSupportedRawFile(it, child.directory) } == true
        }

        RawSourceSummary.Folder(
            label = root.name ?: "Selected folder",
            uri = uri,
            rawCount = raws.size,
            totalBytes = raws.sumOf { it.sizeBytes },
            ignoredCount = children.size - raws.size,
        )
    }

    suspend fun listFolderRaws(uri: Uri, cancellation: ConversionCancellation): List<RawInput> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri) ?: error("The source folder cannot be opened.")
        check(root.isDirectory && root.canRead()) { "The source folder is not readable. Select it again." }
        listDirectChildren(uri) { cancellation.check() }.mapNotNull { child ->
            val name = child.name
            if (name != null && ConversionPolicy.isSupportedRawFile(name, child.directory)) RawInput(child.uri, name) else null
        }.sortedBy { it.label.lowercase(java.util.Locale.ROOT) }
    }

    /**
     * Lists one folder level from the child cursor. [DocumentFile.isFile] is false when the
     * provider omits MIME, which drops Sony .ARW files on stores that don't map that extension.
     * A missing MIME type is not a directory; the display name decides RAW vs ignored.
     */
    private fun listDirectChildren(treeUri: Uri, onChild: () -> Unit = {}): List<FolderChild> {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val children = mutableListOf<FolderChild>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val typeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                onChild()
                val name = if (nameColumn >= 0 && !cursor.isNull(nameColumn)) cursor.getString(nameColumn) else null
                val mime = if (typeColumn >= 0 && !cursor.isNull(typeColumn)) cursor.getString(typeColumn) else null
                val size = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn).coerceAtLeast(0L) else 0L
                children += FolderChild(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn)),
                    name = name,
                    directory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                    sizeBytes = size,
                )
            }
        } ?: error("The selected folder cannot be listed. Select it again.")
        return children
    }

    private data class FolderChild(val uri: Uri, val name: String?, val directory: Boolean, val sizeBytes: Long)

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
