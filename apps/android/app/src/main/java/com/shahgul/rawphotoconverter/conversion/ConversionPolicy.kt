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
    /** Stem template before the format extension. Tokens: {name}, {edge}, {mode}. */
    val nameTemplate: String = "{name}",
) {
    init {
        require(targetBytes > 0)
        require(maxLongEdge == 0 || maxLongEdge in 256..8000)
        require(minQuality in 1..100 && maxQuality in minQuality..100)
        require(nameTemplate.isNotBlank()) { "Filename template cannot be blank." }
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

    fun outputStem(
        source: String,
        template: String = "{name}",
        maxLongEdge: Int = 4000,
        mode: ConversionMode = ConversionMode.DEVELOP_RAW,
    ): String {
        require(isSupportedRaw(source)) { "Select a supported RAW file (.ARW, .CR2 or .CR3)." }
        val name = source.substringBeforeLast('.')
        require(name.isNotBlank() && '/' !in name && '\\' !in name) { "Invalid RAW filename." }
        val edge = if (maxLongEdge == 0) "original" else maxLongEdge.toString()
        val modeToken = when (mode) {
            ConversionMode.DEVELOP_RAW -> "develop"
            ConversionMode.CAMERA_LOOK -> "camera"
        }
        val rendered = template
            .replace("{name}", name)
            .replace("{edge}", edge)
            .replace("{mode}", modeToken)
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .trim('.')
        require(rendered.isNotBlank()) { "Filename template produced an empty name." }
        require('/' !in rendered && '\\' !in rendered) { "Filename template cannot include path separators." }
        return rendered
    }

    fun outputName(
        source: String,
        format: OutputFormat = OutputFormat.JPEG,
        template: String = "{name}",
        maxLongEdge: Int = 4000,
        mode: ConversionMode = ConversionMode.DEVELOP_RAW,
    ): String = "${outputStem(source, template, maxLongEdge, mode)}.${format.extension}"

    fun outputName(source: String, recipe: ConversionRecipe): String =
        outputName(source, recipe.format, recipe.nameTemplate, recipe.maxLongEdge, recipe.mode)

    fun jpegName(source: String): String = outputName(source, OutputFormat.JPEG)
}
