// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shahgul.rawphotoconverter.ui.theme.MeasurementFont
import kotlin.math.max
import kotlin.math.min

/** Full-screen inspect of the embedded camera preview — pinch/pan; 1:1 snaps to pixel-true scale. */
@Composable
internal fun PreviewLoupe(
    bitmap: ImageBitmap,
    label: String,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var fitScale by remember { mutableFloatStateOf(1f) }
    val oneToOneScale = 1f

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val viewW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val viewH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val imageW = bitmap.width.toFloat().coerceAtLeast(1f)
            val imageH = bitmap.height.toFloat().coerceAtLeast(1f)
            val computedFit = min(viewW / imageW, viewH / imageH)
            LaunchedEffect(computedFit) {
                fitScale = computedFit
                scale = computedFit
                offset = Offset.Zero
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(fitScale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(
                                fitScale * 0.85f,
                                max(oneToOneScale, fitScale) * 8f,
                            )
                            offset += pan
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "100% inspect for $label",
                    contentScale = ContentScale.None,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                )
            }

            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                scale = fitScale
                                offset = Offset.Zero
                            },
                        ) { Text("Fit") }
                        TextButton(
                            onClick = {
                                scale = oneToOneScale
                                offset = Offset.Zero
                            },
                        ) { Text("1:1") }
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    "${bitmap.width} × ${bitmap.height} · pinch to zoom · 1:1 is pixel-true",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.78f),
                    fontFamily = MeasurementFont,
                )
            }
        }
    }
}
