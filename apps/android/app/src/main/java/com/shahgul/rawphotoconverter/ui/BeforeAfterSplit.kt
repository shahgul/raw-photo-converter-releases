// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Dragable split between embedded camera JPEG (before) and LibRaw develop (after).
 * Both images share Fit + center so framing stays honest — no invented pixels.
 */
@Composable
internal fun BeforeAfterSplit(
    before: ImageBitmap,
    after: ImageBitmap,
    modifier: Modifier = Modifier,
) {
    var fraction by remember { mutableFloatStateOf(0.5f) }
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .semantics {
                contentDescription =
                    "Before and after compare. Drag to reveal Develop RAW versus the camera JPEG."
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    fraction = (fraction + dragAmount / size.width.toFloat()).coerceIn(0.08f, 0.92f)
                }
            },
    ) {
        val fullWidth = maxWidth
        val splitPx = with(density) { (fullWidth * fraction).toPx() }

        Image(
            bitmap = after,
            contentDescription = "Develop RAW preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxHeight()
                .width(fullWidth * fraction)
                .clipToBounds()
                .align(Alignment.CenterStart),
        ) {
            Image(
                bitmap = before,
                contentDescription = "Camera JPEG preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(fullWidth)
                    .align(Alignment.CenterStart),
            )
        }

        Box(
            Modifier
                .fillMaxHeight()
                .width(2.dp)
                .offset { IntOffset(splitPx.roundToInt() - with(density) { 1.dp.roundToPx() }, 0) }
                .background(Color.White.copy(alpha = 0.92f)),
        )
        Box(
            Modifier
                .offset { IntOffset(splitPx.roundToInt() - with(density) { 18.dp.roundToPx() }, 0) }
                .size(36.dp)
                .align(Alignment.CenterStart)
                .background(Color.White.copy(alpha = 0.95f), CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.25f), CircleShape),
        )

        Text(
            "Camera",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "Develop",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}
