package com.shahgul.rawphotoconverter.conversion

import kotlin.math.max
import kotlin.math.roundToInt

enum class ConversionMode { DEVELOP_RAW, CAMERA_LOOK }
enum class OutputResolution(val maxEdge: Int) { STANDARD(4000), ORIGINAL(0) }

data class ConversionRecipe(
    val targetBytes: Long = 3L * 1024 * 1024,
    val maxLongEdge: Int = 4000,
    val minQuality: Int = 80,
    val maxQuality: Int = 96,
    val preserveMetadata: Boolean = true,
    val mode: ConversionMode = ConversionMode.DEVELOP_RAW,
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

    fun isSupportedRaw(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in supportedRawExtensions

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

    fun jpegName(source: String): String {
        require(isSupportedRaw(source)) { "Select a supported RAW file (.ARW, .CR2 or .CR3)." }
        val stem = source.substringBeforeLast('.')
        require(stem.isNotBlank() && '/' !in stem && '\\' !in stem) { "Invalid RAW filename." }
        return "$stem.jpg"
    }
}
