package com.shahgul.rawphotoconverter.conversion

import android.os.Build
import kotlin.math.max
import kotlin.math.roundToInt

enum class ConversionMode { DEVELOP_RAW, CAMERA_LOOK }
enum class OutputResolution(val maxEdge: Int) { STANDARD(4000), ORIGINAL(0) }

enum class OutputFormat(val extension: String, val mimeType: String, val label: String) {
    JPEG("jpg", "image/jpeg", "JPEG"),
    WEBP("webp", "image/webp", "WebP"),
    AVIF("avif", "image/avif", "AVIF");

    val isSupported: Boolean
        get() = when (this) {
            JPEG, WEBP -> true
            AVIF -> Build.VERSION.SDK_INT >= 34
        }
}

enum class ColorGamut(val label: String, val exifColorSpace: Int) {
    SRGB("sRGB", 1),
    DISPLAY_P3("Display P3", 65535); // 1 = sRGB, 65535 = Uncalibrated / P3
}

data class ConversionRecipe(
    val targetBytes: Long = 3L * 1024 * 1024,
    val maxLongEdge: Int = 4000,
    val minQuality: Int = 80,
    val maxQuality: Int = 96,
    val preserveMetadata: Boolean = true,
    val mode: ConversionMode = ConversionMode.DEVELOP_RAW,
    val format: OutputFormat = OutputFormat.JPEG,
    val colorGamut: ColorGamut = ColorGamut.SRGB,
) {
    init {
        require(targetBytes > 0)
        require(maxLongEdge == 0 || maxLongEdge in 256..8000)
        require(minQuality in 1..100 && maxQuality in minQuality..100)
    }
}

data class PixelSize(val width: Int, val height: Int)
data class QualityChoice(val quality: Int, val bytes: Long, val targetMet: Boolean)

object ConversionPolicy {
    private val supportedRawExtensions = setOf("arw", "cr2", "cr3")

    /**
     * Document pickers hide files whose MIME is outside this list. The wildcard type keeps
     * providers that label Sony ARW as application/octet-stream from filtering them out.
     * Vendor types cover image/x-sony-arw and the Canon RAW types.
     */
    fun openDocumentMimeTypes(): Array<String> = arrayOf(
        "*/*",
        "application/octet-stream",
        "image/x-sony-arw",
        "image/arw",
        "image/x-canon-cr2",
        "image/x-canon-cr3",
    )

    fun isSupportedRaw(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in supportedRawExtensions

    /** Directory entries are never RAW, even when the display name ends in .ARW. Missing MIME is not a directory. */
    fun isSupportedRawFile(name: String, directory: Boolean): Boolean =
        !directory && isSupportedRaw(name)

    fun fit(width: Int, height: Int, maxLongEdge: Int): PixelSize {
        require(width > 0 && height > 0 && maxLongEdge >= 0)
        val scale = if (maxLongEdge == 0) 1.0 else minOf(1.0, maxLongEdge.toDouble() / max(width, height))
        return PixelSize(max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()))
    }

    fun chooseQuality(recipe: ConversionRecipe, encode: (Int) -> Long): QualityChoice {
        // Measure the complete JPEG, including metadata. Descending search also
        // handles encoders whose sizes are not strictly monotonic with quality.
        for (quality in recipe.maxQuality downTo recipe.minQuality) {
            val bytes = encode(quality)
            check(bytes > 0) { "JPEG encoder produced an empty file." }
            if (bytes <= recipe.targetBytes || quality == recipe.minQuality) {
                return QualityChoice(quality, bytes, bytes <= recipe.targetBytes)
            }
        }
        error("Invalid quality range")
    }

    fun outputName(source: String, format: OutputFormat = OutputFormat.JPEG): String {
        require(isSupportedRaw(source)) { "Select a supported RAW file (.ARW, .CR2 or .CR3)." }
        val stem = source.substringBeforeLast('.')
        require(stem.isNotBlank() && '/' !in stem && '\\' !in stem) { "Invalid RAW filename." }
        return "$stem.${format.extension}"
    }

    fun jpegName(source: String): String = outputName(source, OutputFormat.JPEG)

    /** User-facing explanation when a picked or shared file is not a supported RAW. */
    fun unsupportedSelectionMessage(label: String): String {
        val extension = label.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        return when (extension) {
            "jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "bmp", "tif", "tiff" ->
                "$label is a regular photo, not a RAW file. Add Sony ARW or Canon CR2/CR3 to convert."
            "" ->
                "$label is not a supported RAW file. Use Sony ARW or Canon CR2/CR3."
            else ->
                "$label is not supported. This app converts Sony ARW and Canon CR2/CR3 only."
        }
    }
}
