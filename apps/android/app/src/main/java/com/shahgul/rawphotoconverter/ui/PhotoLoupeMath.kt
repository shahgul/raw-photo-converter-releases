// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import androidx.compose.ui.geometry.Offset

internal object PhotoLoupeMath {
    data class Dimensions(val width: Float, val height: Float)

    /**
     * Calculates the rendered size of an image fitted within a container
     * maintaining aspect ratio (equivalent to ContentScale.Fit).
     */
    fun calculateFitDimensions(
        containerWidth: Float,
        containerHeight: Float,
        imageWidth: Float,
        imageHeight: Float,
    ): Dimensions {
        if (imageWidth <= 0f || imageHeight <= 0f || containerWidth <= 0f || containerHeight <= 0f) {
            return Dimensions(0f, 0f)
        }
        val scale = minOf(containerWidth / imageWidth, containerHeight / imageHeight)
        return Dimensions(imageWidth * scale, imageHeight * scale)
    }

    /**
     * Calculates the maximum absolute pan offsets (in px) allowed from center.
     * When the image does not exceed the container dimension, pan is 0 to keep it centered.
     */
    fun calculateMaxPanOffsets(
        renderedWidth: Float,
        renderedHeight: Float,
        scale: Float,
        containerWidth: Float,
        containerHeight: Float,
    ): Dimensions {
        val currentWidth = renderedWidth * scale
        val currentHeight = renderedHeight * scale
        val maxOffsetX = maxOf(0f, (currentWidth - containerWidth) / 2f)
        val maxOffsetY = maxOf(0f, (currentHeight - containerHeight) / 2f)
        return Dimensions(maxOffsetX, maxOffsetY)
    }

    /**
     * Calculates the pan offset required to center on a double-tapped point,
     * clamped to the allowable pan bounds.
     */
    fun calculateDoubleTapOffset(
        tapOffset: Offset,
        containerWidth: Float,
        containerHeight: Float,
        targetScale: Float,
        renderedWidth: Float,
        renderedHeight: Float,
    ): Offset {
        val centerX = containerWidth / 2f
        val centerY = containerHeight / 2f
        val targetOffsetX = -(tapOffset.x - centerX) * (targetScale - 1f)
        val targetOffsetY = -(tapOffset.y - centerY) * (targetScale - 1f)
        val bounds = calculateMaxPanOffsets(
            renderedWidth = renderedWidth,
            renderedHeight = renderedHeight,
            scale = targetScale,
            containerWidth = containerWidth,
            containerHeight = containerHeight,
        )
        return Offset(
            x = targetOffsetX.coerceIn(-bounds.width, bounds.width),
            y = targetOffsetY.coerceIn(-bounds.height, bounds.height),
        )
    }

    /**
     * Human-readable magnification indicator label.
     */
    fun formatMagnification(scale: Float): String {
        return if (scale <= 1.02f) "Fit" else "${(scale * 100).toInt()}%"
    }
}
