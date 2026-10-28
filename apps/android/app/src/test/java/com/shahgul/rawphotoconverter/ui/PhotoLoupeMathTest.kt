// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoLoupeMathTest {

    @Test
    fun calculateFitDimensions_landscapeOnPortraitScreen() {
        // 1616x1080 photo on a 1080x2400 screen
        val fit = PhotoLoupeMath.calculateFitDimensions(
            containerWidth = 1080f,
            containerHeight = 2400f,
            imageWidth = 1616f,
            imageHeight = 1080f,
        )
        // Scaled by 1080 / 1616 = ~0.6683
        assertEquals(1080f, fit.width, 0.01f)
        assertEquals(721.78f, fit.height, 0.1f)
    }

    @Test
    fun calculateFitDimensions_portraitOnPortraitScreen() {
        // 1080x1616 photo on a 1080x2400 screen
        val fit = PhotoLoupeMath.calculateFitDimensions(
            containerWidth = 1080f,
            containerHeight = 2400f,
            imageWidth = 1080f,
            imageHeight = 1616f,
        )
        // Scaled by 1080 / 1080 = 1.0 (bounded by width 1080 <= 2400)
        assertEquals(1080f, fit.width, 0.01f)
        assertEquals(1616f, fit.height, 0.01f)
    }

    @Test
    fun calculateFitDimensions_handlesZeroOrNegative() {
        val fit = PhotoLoupeMath.calculateFitDimensions(
            containerWidth = 1080f,
            containerHeight = 2400f,
            imageWidth = 0f,
            imageHeight = 1080f,
        )
        assertEquals(0f, fit.width, 0.001f)
        assertEquals(0f, fit.height, 0.001f)
    }

    @Test
    fun calculateMaxPanOffsets_atFitScaleIsZero() {
        val bounds = PhotoLoupeMath.calculateMaxPanOffsets(
            renderedWidth = 1080f,
            renderedHeight = 721.78f,
            scale = 1.0f,
            containerWidth = 1080f,
            containerHeight = 2400f,
        )
        assertEquals(0f, bounds.width, 0.001f)
        assertEquals(0f, bounds.height, 0.001f)
    }

    @Test
    fun calculateMaxPanOffsets_landscapeZoomed_verticalDoesNotOverscrollWhenSmallerThanScreen() {
        // 1080x721.78 rendered image at 2.5x:
        // currentWidth = 2700, containerWidth = 1080 -> excess = 1620 -> maxOffsetX = 810
        // currentHeight = 1804.45, containerHeight = 2400 -> fits inside container -> maxOffsetY = 0
        val bounds = PhotoLoupeMath.calculateMaxPanOffsets(
            renderedWidth = 1080f,
            renderedHeight = 721.78f,
            scale = 2.5f,
            containerWidth = 1080f,
            containerHeight = 2400f,
        )
        assertEquals(810f, bounds.width, 0.1f)
        assertEquals(0f, bounds.height, 0.001f)
    }

    @Test
    fun calculateMaxPanOffsets_highZoom_bothAxesCanPan() {
        // At 4.0x: currentHeight = 721.78 * 4 = 2887.12 > 2400 -> excess = 487.12 -> maxOffsetY = 243.56
        val bounds = PhotoLoupeMath.calculateMaxPanOffsets(
            renderedWidth = 1080f,
            renderedHeight = 721.78f,
            scale = 4.0f,
            containerWidth = 1080f,
            containerHeight = 2400f,
        )
        assertEquals(1620f, bounds.width, 0.1f)
        assertEquals(243.56f, bounds.height, 0.1f)
    }

    @Test
    fun calculateDoubleTapOffset_centerTapStaysCentered() {
        val offset = PhotoLoupeMath.calculateDoubleTapOffset(
            tapOffset = Offset(540f, 1200f),
            containerWidth = 1080f,
            containerHeight = 2400f,
            targetScale = 2.5f,
            renderedWidth = 1080f,
            renderedHeight = 721.78f,
        )
        assertEquals(0f, offset.x, 0.01f)
        assertEquals(0f, offset.y, 0.01f)
    }

    @Test
    fun calculateDoubleTapOffset_offCenterClampsToBounds() {
        // Tapping near left edge (50, 1200)
        val offset = PhotoLoupeMath.calculateDoubleTapOffset(
            tapOffset = Offset(50f, 1200f),
            containerWidth = 1080f,
            containerHeight = 2400f,
            targetScale = 2.5f,
            renderedWidth = 1080f,
            renderedHeight = 721.78f,
        )
        // targetOffsetX = -(50 - 540) * 1.5 = 735; maxOffsetX is 810
        assertEquals(735f, offset.x, 0.1f)
        assertEquals(0f, offset.y, 0.01f)
    }

    @Test
    fun formatMagnification_displaysFitOrPercentage() {
        assertEquals("Fit", PhotoLoupeMath.formatMagnification(1.0f))
        assertEquals("Fit", PhotoLoupeMath.formatMagnification(1.02f))
        assertEquals("150%", PhotoLoupeMath.formatMagnification(1.5f))
        assertEquals("250%", PhotoLoupeMath.formatMagnification(2.5f))
        assertEquals("600%", PhotoLoupeMath.formatMagnification(6.0f))
    }
}
