// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.sqrt

data class HistogramData(
    val red: IntArray,
    val green: IntArray,
    val blue: IntArray,
    val luminance: IntArray,
    val maxCount: Int,
    val hasShadowClipping: Boolean,
    val hasHighlightClipping: Boolean,
    val shadowClipPercent: Float,
    val highlightClipPercent: Float,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HistogramData) return false
        return red.contentEquals(other.red) &&
            green.contentEquals(other.green) &&
            blue.contentEquals(other.blue) &&
            luminance.contentEquals(other.luminance) &&
            maxCount == other.maxCount &&
            hasShadowClipping == other.hasShadowClipping &&
            hasHighlightClipping == other.hasHighlightClipping
    }

    override fun hashCode(): Int {
        var result = red.contentHashCode()
        result = 31 * result + green.contentHashCode()
        result = 31 * result + blue.contentHashCode()
        result = 31 * result + luminance.contentHashCode()
        result = 31 * result + maxCount
        return result
    }
}

object HistogramAnalyzer {
    suspend fun analyze(bitmap: Bitmap): HistogramData = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width.toLong() * height
        // Sample down large previews to ~200k-500k pixels for instant non-blocking analysis
        val stride = max(1, sqrt(totalPixels / 250_000.0).toInt())

        val rBins = IntArray(256)
        val gBins = IntArray(256)
        val bBins = IntArray(256)
        val lBins = IntArray(256)

        var sampledCount = 0L

        val rowPixels = IntArray(width)
        for (y in 0 until height step stride) {
            bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1)
            for (x in 0 until width step stride) {
                val pixel = rowPixels[x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                // Standard Rec. 709 / sRGB luminance weights
                val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)

                rBins[r]++
                gBins[g]++
                bBins[b]++
                lBins[lum]++
                sampledCount++
            }
        }

        var maxC = 1
        for (i in 1 until 255) { // omit bins 0 and 255 from max count to avoid blown clipping dominating graph scaling
            if (rBins[i] > maxC) maxC = rBins[i]
            if (gBins[i] > maxC) maxC = gBins[i]
            if (bBins[i] > maxC) maxC = bBins[i]
            if (lBins[i] > maxC) maxC = lBins[i]
        }

        val shadowPixels = lBins[0]
        val highlightPixels = lBins[255]
        val shadowPercent = if (sampledCount > 0) (shadowPixels.toFloat() / sampledCount) * 100f else 0f
        val highlightPercent = if (sampledCount > 0) (highlightPixels.toFloat() / sampledCount) * 100f else 0f

        HistogramData(
            red = rBins,
            green = gBins,
            blue = bBins,
            luminance = lBins,
            maxCount = maxC,
            hasShadowClipping = shadowPercent >= 1.0f,
            hasHighlightClipping = highlightPercent >= 1.0f,
            shadowClipPercent = shadowPercent,
            highlightClipPercent = highlightPercent,
        )
    }
}
