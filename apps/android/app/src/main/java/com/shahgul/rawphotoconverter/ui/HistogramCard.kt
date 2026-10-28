// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shahgul.rawphotoconverter.ui.theme.MeasurementFont

enum class HistogramMode { RGB, LUMA }

@Composable
fun HistogramCard(
    previewBitmap: Bitmap?,
    expanded: Boolean = false,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    showHeader: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (previewBitmap == null) return

    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes

    var internalExpanded by rememberSaveable { mutableStateOf(false) }
    val isExpanded = if (onExpandedChange != null) expanded else internalExpanded
    fun setExpanded(value: Boolean) {
        if (onExpandedChange != null) onExpandedChange(value)
        else internalExpanded = value
    }

    var mode by rememberSaveable { mutableStateOf(HistogramMode.RGB) }
    var histogramData by remember(previewBitmap) { mutableStateOf<HistogramData?>(null) }
    var computing by remember(previewBitmap) { mutableStateOf(false) }

    LaunchedEffect(previewBitmap) {
        computing = true
        histogramData = HistogramAnalyzer.analyze(previewBitmap)
        computing = false
    }

    Column(modifier = modifier) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { setExpanded(!isExpanded) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(44.dp),
                ) {
                    Text(
                        if (isExpanded) "Hide histogram" else "Exposure histogram",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                    )
                }

                if (isExpanded && histogramData != null) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.width(130.dp)) {
                        SegmentedButton(
                            selected = mode == HistogramMode.RGB,
                            onClick = { mode = HistogramMode.RGB },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            label = { Text("RGB", style = MaterialTheme.typography.labelSmall) },
                        )
                        SegmentedButton(
                            selected = mode == HistogramMode.LUMA,
                            onClick = { mode = HistogramMode.LUMA },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            label = { Text("Luma", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(motionTween(180)),
            exit = fadeOut(motionTween(140)),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                color = colors.surfaceContainer,
                shape = shapes.medium,
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.5f)),
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!showHeader) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Exposure distribution",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                            if (histogramData != null) {
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.width(130.dp)) {
                                    SegmentedButton(
                                        selected = mode == HistogramMode.RGB,
                                        onClick = { mode = HistogramMode.RGB },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        label = { Text("RGB", style = MaterialTheme.typography.labelSmall) },
                                    )
                                    SegmentedButton(
                                        selected = mode == HistogramMode.LUMA,
                                        onClick = { mode = HistogramMode.LUMA },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        label = { Text("Luma", style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }
                    }

                    if (computing || histogramData == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = colors.primary,
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        val data = histogramData!!
                        HistogramCanvas(
                            data = data,
                            mode = mode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .background(colors.surfaceContainerLowest, shapes.small),
                        )

                        // Exposure clipping alerts & grid indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "0",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = MeasurementFont,
                                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                            if (data.hasShadowClipping) {
                                Surface(
                                    color = colors.errorContainer.copy(alpha = 0.85f),
                                    shape = shapes.extraSmall,
                                ) {
                                    Text(
                                        "Shadow clip: %.1f%%".format(data.shadowClipPercent),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onErrorContainer,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            Text(
                                "128",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = MeasurementFont,
                                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                            if (data.hasHighlightClipping) {
                                Surface(
                                    color = colors.primaryContainer.copy(alpha = 0.85f),
                                    shape = shapes.extraSmall,
                                ) {
                                    Text(
                                        "Highlight clip: %.1f%%".format(data.highlightClipPercent),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                            Text(
                                "255",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = MeasurementFont,
                                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistogramCanvas(
    data: HistogramData,
    mode: HistogramMode,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    val redColor = Color(0xFFEF5350)
    val greenColor = Color(0xFF66BB6A)
    val blueColor = Color(0xFF42A5F5)
    val lumaColor = Color(0xFFE0E0E0)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Background subtle grid lines: 25%, 50%, 75%
        for (i in 1..3) {
            val gx = w * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(gx, 0f),
                end = Offset(gx, h),
                strokeWidth = 1f,
            )
            val gy = h * (i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = 1f,
            )
        }

        val maxVal = data.maxCount.toFloat().coerceAtLeast(1f)

        if (mode == HistogramMode.RGB) {
            drawChannel(data.red, redColor.copy(alpha = 0.45f), redColor.copy(alpha = 0.85f), maxVal, w, h)
            drawChannel(data.green, greenColor.copy(alpha = 0.45f), greenColor.copy(alpha = 0.85f), maxVal, w, h)
            drawChannel(data.blue, blueColor.copy(alpha = 0.45f), blueColor.copy(alpha = 0.85f), maxVal, w, h)
        } else {
            drawChannel(data.luminance, lumaColor.copy(alpha = 0.5f), lumaColor.copy(alpha = 0.95f), maxVal, w, h)
        }
    }
}

private fun DrawScope.drawChannel(
    bins: IntArray,
    fillColor: Color,
    strokeColor: Color,
    maxVal: Float,
    w: Float,
    h: Float,
) {
    if (bins.size != 256) return

    val path = Path()
    path.moveTo(0f, h)

    val stepX = w / 255f
    for (i in 0..255) {
        val x = i * stepX
        val y = h - ((bins[i].toFloat() / maxVal).coerceIn(0f, 1f) * (h - 2f))
        if (i == 0) path.lineTo(x, y) else path.lineTo(x, y)
    }
    path.lineTo(w, h)
    path.close()

    drawPath(path, fillColor, style = Fill)

    val strokePath = Path()
    for (i in 0..255) {
        val x = i * stepX
        val y = h - ((bins[i].toFloat() / maxVal).coerceIn(0f, 1f) * (h - 2f))
        if (i == 0) strokePath.moveTo(x, y) else strokePath.lineTo(x, y)
    }
    drawPath(strokePath, strokeColor, style = Stroke(width = 1.2f))
}
