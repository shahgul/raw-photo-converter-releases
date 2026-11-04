// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shahgul.rawphotoconverter.conversion.ConversionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Downsampled decode of a saved conversion output for Results chrome — never invents pixels. */
internal object ResultThumbnailDecoder {
    fun decode(context: Context, uri: Uri, maxEdgePx: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                    if (longest > maxEdgePx) {
                        val scale = longest.toFloat() / maxEdgePx
                        decoder.setTargetSize(
                            (info.size.width / scale).toInt().coerceAtLeast(1),
                            (info.size.height / scale).toInt().coerceAtLeast(1),
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                    var sample = 1
                    while (longest / (sample * 2) >= maxEdgePx) sample *= 2
                    BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
internal fun rememberResultThumbnail(uri: Uri?, maxEdgePx: Int = 256): ImageBitmap? {
    val context = LocalContext.current
    var image by remember(uri, maxEdgePx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri, maxEdgePx) {
        image = null
        if (uri == null) return@LaunchedEffect
        image = withContext(Dispatchers.IO) {
            ResultThumbnailDecoder.decode(context, uri, maxEdgePx)?.asImageBitmap()
        }
    }
    return image
}

@Composable
internal fun SavedResultThumbnailStrip(
    results: List<ConversionResult>,
    onOpen: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val saved = remember(results) {
        results.filter { it.output != null && it.error == null && !it.skipped }
    }
    if (saved.isEmpty()) return
    val shapes = MaterialTheme.shapes
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Saved frames",
            style = MaterialTheme.typography.titleMedium,
        )
        LazyRow(
            contentPadding = PaddingValues(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(saved, key = { "${it.source}:${it.output}" }) { result ->
                val uri = result.output ?: return@items
                ResultThumb(
                    uri = uri,
                    label = result.source,
                    size = 88.dp,
                    onClick = { onOpen(uri) },
                    modifier = Modifier
                        .clip(shapes.medium)
                        .semantics { contentDescription = "Open saved ${result.source}" },
                )
            }
        }
        Text(
            "Tap a frame to open the saved file.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ResultThumb(
    uri: Uri,
    label: String,
    size: Dp,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val image = rememberResultThumbnail(uri, maxEdgePx = 256)
    val clickable = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        color = colors.surfaceContainerHighest,
        shape = shapes.medium,
        tonalElevation = 1.dp,
        modifier = modifier
            .size(size)
            .then(clickable),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colors.surfaceVariant),
                )
            }
        }
    }
}

@Composable
internal fun ResultRowThumb(uri: Uri?, modifier: Modifier = Modifier) {
    if (uri == null) {
        Box(modifier.width(48.dp).height(48.dp))
        return
    }
    ResultThumb(
        uri = uri,
        label = "Saved output",
        size = 48.dp,
        onClick = null,
        modifier = modifier,
    )
}
