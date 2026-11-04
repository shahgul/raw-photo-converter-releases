package com.shahgul.rawphotoconverter.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.shahgul.rawphotoconverter.conversion.ConversionPolicy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahgul.rawphotoconverter.R
import com.shahgul.rawphotoconverter.conversion.BatchState
import com.shahgul.rawphotoconverter.conversion.ConversionMode
import com.shahgul.rawphotoconverter.conversion.ConversionRecipe
import com.shahgul.rawphotoconverter.conversion.OutputResolution
import com.shahgul.rawphotoconverter.data.DevelopedPreviewState
import com.shahgul.rawphotoconverter.data.RawSourceSummary
import com.shahgul.rawphotoconverter.data.RawPreviewState
import com.shahgul.rawphotoconverter.ui.theme.MeasurementFont
import com.shahgul.rawphotoconverter.conversion.ColorGamut
import com.shahgul.rawphotoconverter.conversion.OutputFormat
import com.shahgul.rawphotoconverter.ui.theme.ThemePreference

/** Presentation only; picker grants, conversion and lifecycle stay in RawConverterApp. */
@Composable
internal fun ConverterWorkspace(
    source: RawSourceSummary?, loading: Boolean, batch: BatchState,
    mode: ConversionMode, resolution: OutputResolution, onMode: (ConversionMode) -> Unit, onResolution: (OutputResolution) -> Unit,
    outputLabel: String?, preserveMetadata: Boolean, error: String?,
    onOpenRaw: () -> Unit, onOpenFolder: () -> Unit, onOutput: () -> Unit,
    onProcessing: () -> Unit, onResults: () -> Unit, onMetadata: (Boolean) -> Unit, onConvert: () -> Unit,
    themePreference: ThemePreference, onThemeCycle: () -> Unit,
    onRemovePhoto: (Uri) -> Unit = {},
    onCompareDevelop: () -> Unit = {},
    format: OutputFormat = OutputFormat.JPEG, onFormat: (OutputFormat) -> Unit = {},
    colorGamut: ColorGamut = ColorGamut.SRGB, onColorGamut: (ColorGamut) -> Unit = {},
    completionFeedback: Boolean = true, onCompletionFeedback: (Boolean) -> Unit = {},
    nameTemplate: String = "{name}", onNameTemplate: (String) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val compactHeight = compactWindow()
    val snackbar = remember { SnackbarHostState() }
    var wasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(batch.running) {
        if (wasRunning && !batch.running) snackbar.showSnackbar(
            "${batch.phase} · ${batch.results.count { it.output != null }} saved",
            withDismissAction = true,
        )
        wasRunning = batch.running
    }
    val enabled = !loading && !batch.running
    val count = when (source) {
        is RawSourceSummary.Folder -> source.rawCount
        is RawSourceSummary.Single -> 1
        is RawSourceSummary.Photos -> source.photos.size
        null -> 0
    }
    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = if (compactHeight) 10.dp else 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Convert", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Surface(
                        color = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer,
                        shape = shapes.extraSmall,
                    ) {
                        Text(
                            "Local",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    ThemeToggle(themePreference, onThemeCycle)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val expanded = maxWidth >= 600.dp
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(top = 4.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (expanded) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    SourceHero(
                                        source, loading, enabled, onOpenRaw, onOpenFolder,
                                        onRemovePhoto, onCompareDevelop,
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    RecipePanel(
                                        outputLabel, preserveMetadata, enabled, onOutput, onMetadata,
                                        mode, resolution, onMode, onResolution,
                                        format, onFormat, colorGamut, onColorGamut,
                                        completionFeedback, onCompletionFeedback,
                                        nameTemplate, onNameTemplate,
                                    )
                                }
                            }
                        } else {
                            SourceHero(
                                source, loading, enabled, onOpenRaw, onOpenFolder,
                                onRemovePhoto, onCompareDevelop,
                            )
                            RecipePanel(
                                outputLabel, preserveMetadata, enabled, onOutput, onMetadata,
                                mode, resolution, onMode, onResolution,
                                format, onFormat, colorGamut, onColorGamut,
                                completionFeedback, onCompletionFeedback,
                                nameTemplate, onNameTemplate,
                            )
                        }
                        error?.let {
                            Surface(
                                color = colors.errorContainer,
                                contentColor = colors.onErrorContainer,
                                shape = shapes.medium,
                            ) {
                                Column(Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                                    Text("Couldn't continue", style = MaterialTheme.typography.titleSmall)
                                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        if (batch.running || batch.results.isNotEmpty() || batch.message != null) {
                            TextButton(
                                onClick = if (batch.running) onProcessing else onResults,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text(if (batch.running) "Conversion running - View progress" else "View conversion results")
                            }
                        }
                    }
                }
                Surface(color = colors.surfaceContainerLow, tonalElevation = 2.dp) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (!compactHeight || batch.running) {
                            Text(
                                when {
                                    batch.running -> "${batch.processedResults.size} of ${batch.total} processed · ${batch.phase}"
                                    loading -> "Reading your photos…"
                                    source == null -> "Add source photos in Step 1, then choose a JPEG folder in Step 2"
                                    count == 0 -> "No supported RAW files found in this folder"
                                    outputLabel == null -> "Choose a folder for your ${format.label} files"
                                    else -> "Originals stay untouched · existing ${format.label} files are skipped"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        Button(
                            onClick = if (batch.running) onProcessing else onConvert,
                            enabled = batch.running || (enabled && count > 0 && outputLabel != null),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = shapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryContainer,
                                contentColor = colors.onPrimaryContainer,
                            ),
                        ) {
                            Text(
                                if (batch.running) "View processing"
                                else if (count > 1) "Convert $count photos to ${format.label}"
                                else "Convert to ${format.label}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 120.dp),
            )
        }
    }
}

@Composable
internal fun ThemeToggle(preference: ThemePreference, onCycle: () -> Unit) {
    val icon = when (preference) {
        ThemePreference.System -> R.drawable.ic_monitor
        ThemePreference.Light -> R.drawable.ic_sun
        ThemePreference.Dark -> R.drawable.ic_moon
    }
    IconButton(
        onClick = onCycle,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = "Theme: ${preference.label}. Tap to change." },
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SourceHero(
    source: RawSourceSummary?,
    loading: Boolean,
    enabled: Boolean,
    onRaw: () -> Unit,
    onFolder: () -> Unit,
    onRemovePhoto: (Uri) -> Unit,
    onCompareDevelop: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val landed = source != null && !loading
    val sourceKey = when (source) {
        null -> if (loading) "loading" else "empty"
        is RawSourceSummary.Single -> "single:${source.uri}"
        is RawSourceSummary.Folder -> "folder:${source.uri}:${source.rawCount}"
        is RawSourceSummary.Photos -> "photos"
    }
    val landIn = motionMillis(220)
    val landOut = motionMillis(160)
    Surface(
        color = colors.surfaceContainerLow,
        shape = shapes.extraLarge,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = if (landed) 0.95f else 0.8f)),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(landIn)),
        tonalElevation = if (landed) 2.dp else 1.dp,
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Step 1 · Source",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            AnimatedContent(
                targetState = sourceKey,
                transitionSpec = {
                    (fadeIn(tween(landIn)) + expandVertically(tween(landIn), expandFrom = Alignment.Top)) togetherWith
                        (fadeOut(tween(landOut)) + shrinkVertically(tween(landOut), shrinkTowards = Alignment.Top)) using
                        SizeTransform(clip = false)
                },
                label = "source-land",
            ) { key ->
                when {
                    key == "loading" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Reading source…", style = MaterialTheme.typography.titleLarge)
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth(),
                            color = colors.primary,
                            trackColor = colors.surfaceContainerHighest,
                        )
                    }
                    key == "empty" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add photos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Sony ARW or Canon CR2/CR3 photos as input. JPEGs are saved in Step 2 below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    else -> {
                        val selected = source
                        if (selected != null) {
                            val photoCount = when (selected) {
                                is RawSourceSummary.Single -> 1
                                is RawSourceSummary.Folder -> selected.rawCount
                                is RawSourceSummary.Photos -> selected.photos.size
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                when (selected) {
                                    is RawSourceSummary.Photos -> {
                                        Text(selected.label, style = MaterialTheme.typography.titleLarge)
                                        Text("Remove photos you don't want to convert.",
                                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                                            items(selected.photos, key = { it.uri.toString() }) { photo ->
                                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                                        Text(photo.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                            style = MaterialTheme.typography.bodyMedium)
                                                        Text(photo.sizeBytes?.let(::formatSize) ?: "Size unavailable",
                                                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                                    }
                                                    IconButton(onClick = { onRemovePhoto(photo.uri) }, enabled = enabled) {
                                                        Icon(painterResource(R.drawable.ic_x), contentDescription = "Remove ${photo.label}")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    is RawSourceSummary.Single -> {
                                        SinglePhotoPreview(
                                            selected,
                                            compactWindow(),
                                            onCompareDevelop = onCompareDevelop,
                                        )
                                        selected.metadata.camera?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                        }
                                    }
                                    is RawSourceSummary.Folder -> {
                                        if (selected.ignoredCount > 0) {
                                            Text("${selected.ignoredCount} unsupported file(s) or subfolder(s) aren't included.",
                                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                        }
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Surface(
                                                color = colors.primaryContainer,
                                                contentColor = colors.onPrimaryContainer,
                                                shape = shapes.medium,
                                            ) {
                                                Text(
                                                    "$photoCount",
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontFamily = MeasurementFont,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    selected.label,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    "${selected.rawCount} RAW · ${formatSize(selected.totalBytes)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.onSurfaceVariant,
                                                    fontFamily = MeasurementFont,
                                                )
                                            }
                                        }
                                        Text(
                                            "Source folder only — subfolders aren't included.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onRaw,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = shapes.medium,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                    ),
                ) {
                    Icon(painterResource(R.drawable.ic_images), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (source is RawSourceSummary.Folder) "Choose photos" else "Add photos")
                }
                OutlinedButton(
                    onClick = onFolder,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = shapes.medium,
                ) {
                    Icon(painterResource(R.drawable.ic_folder_open), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Source folder")
                }
            }
            Text(
                "Input only — files are saved via “Save to” in Step 2.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            if (source is RawSourceSummary.Single) {
                val previewBitmap = (source.preview as? RawPreviewState.Ready)?.bitmap
                HistogramCard(previewBitmap = previewBitmap)
                PhotoDetails(source)
                TextButton(onClick = { onRemovePhoto(source.uri) }, enabled = enabled) { Text("Remove photo") }
            }
        }
    }
}

@Composable
private fun SinglePhotoPreview(
    source: RawSourceSummary.Single,
    compactHeight: Boolean,
    onCompareDevelop: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val previewHeight = if (compactHeight) 170.dp else 260.dp
    var loupeBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    if (loupeBitmap != null) {
        PreviewLoupe(
            bitmap = loupeBitmap!!,
            label = source.label,
            onDismiss = { loupeBitmap = null },
        )
    }
    val cameraReady = source.preview as? RawPreviewState.Ready
    val developedReady = source.developedPreview as? DevelopedPreviewState.Ready
    val comparing = cameraReady != null && developedReady != null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(shapes.medium)
                .background(colors.surfaceContainerHighest),
        ) {
            when {
                comparing -> {
                    BeforeAfterSplit(
                        before = cameraReady!!.bitmap.asImageBitmap(),
                        after = developedReady!!.bitmap.asImageBitmap(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                else -> when (val preview = source.preview) {
                    RawPreviewState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp)
                        }
                    }
                    is RawPreviewState.Ready -> {
                        Image(
                            bitmap = preview.bitmap.asImageBitmap(),
                            contentDescription = "Embedded camera JPEG preview for ${source.label}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    is RawPreviewState.Unavailable -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                preview.message,
                                modifier = Modifier.padding(24.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (source.developedPreview is DevelopedPreviewState.Loading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Developing compare preview…",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
            }

            if (!comparing) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(82.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                            ),
                        ),
                )

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 48.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        source.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "1 RAW · ${formatSize(source.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.84f),
                        fontFamily = MeasurementFont,
                        maxLines = 1,
                    )
                }

                Text(
                    "01",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 15.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                    fontFamily = MeasurementFont,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (cameraReady != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (comparing) "Camera JPEG vs Develop RAW" else "Embedded camera JPEG preview",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    if (comparing) {
                        "Drag the split to compare. Framing stays Fit — no invented pixels."
                    } else {
                        "Develop RAW may render differently."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                when (val developed = source.developedPreview) {
                    DevelopedPreviewState.Idle -> {
                        TextButton(
                            onClick = onCompareDevelop,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Compare Develop")
                        }
                    }
                    DevelopedPreviewState.Loading -> {
                        Text(
                            "Developing a bounded compare preview…",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    is DevelopedPreviewState.Failed -> {
                        Text(
                            developed.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                        )
                        TextButton(
                            onClick = onCompareDevelop,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("Retry compare")
                        }
                    }
                    is DevelopedPreviewState.Ready -> Unit
                }
                TextButton(
                    onClick = { loupeBitmap = cameraReady.bitmap.asImageBitmap() },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Inspect 100%")
                }
            }
        }
    }
}

@Composable
private fun RecipePanel(
    outputLabel: String?,
    metadata: Boolean,
    enabled: Boolean,
    onOutput: () -> Unit,
    onMetadata: (Boolean) -> Unit,
    mode: ConversionMode,
    resolution: OutputResolution,
    onMode: (ConversionMode) -> Unit,
    onResolution: (OutputResolution) -> Unit,
    format: OutputFormat,
    onFormat: (OutputFormat) -> Unit,
    colorGamut: ColorGamut,
    onColorGamut: (ColorGamut) -> Unit,
    completionFeedback: Boolean,
    onCompletionFeedback: (Boolean) -> Unit,
    nameTemplate: String,
    onNameTemplate: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var showOutputInfo by rememberSaveable { mutableStateOf(false) }
    val recipe = remember(mode, resolution, format, colorGamut, nameTemplate) {
        ConversionRecipe(
            mode = mode,
            maxLongEdge = resolution.maxEdge,
            format = format,
            colorGamut = colorGamut,
            nameTemplate = nameTemplate.ifBlank { "{name}" },
        )
    }
    val exampleName = remember(recipe) {
        runCatching { ConversionPolicy.outputName("DSC00001.ARW", recipe) }.getOrDefault("DSC00001.${format.extension}")
    }
    if (showOutputInfo) {
        AlertDialog(
            onDismissRequest = { showOutputInfo = false },
            title = { Text("How conversion output works") },
            text = {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        when (mode) {
                            ConversionMode.DEVELOP_RAW -> "Develop creates an image from the RAW sensor data."
                            ConversionMode.CAMERA_LOOK -> "Camera Look starts from the camera's embedded JPEG preview when available. Its image data is kept without recompression when matching standard JPEG output and ${recipe.targetBytes / 1048576} MiB target; otherwise, it is developed/re-encoded into your chosen format."
                        },
                    )
                    Text("Format: JPEG is universal; WebP is efficient for web and sharing; AVIF offers next-gen compression on Android 14+.")
                    Text("Color space: sRGB is standard across all screens. Display P3 preserves wide color volume on modern OLED displays.")
                    Text("Filename templates build the stem before the format extension. {name} is the RAW stem, {edge} is 4000 or original, {mode} is develop or camera.")
                    Text("No crop or upscaling. The source aspect ratio is preserved. Standard caps the long edge at ${OutputResolution.STANDARD.maxEdge} px; Original keeps the available resolution and may use more memory.")
                    Text("When encoding is needed, quality goes from Q${recipe.maxQuality} down to Q${recipe.minQuality} to try to meet the ${recipe.targetBytes / 1048576} MiB target. Q${recipe.minQuality} is the floor, so image quality takes priority over the size target.")
                    Text("When enabled, supported EXIF/XMP is kept. Maker notes and binary IPTC aren't copied.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showOutputInfo = false }) { Text("Done") }
            },
        )
    }
    val hasOutput = !outputLabel.isNullOrBlank()
    Surface(
        color = colors.surfaceContainerLow,
        shape = shapes.extraLarge,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = if (hasOutput) 0.95f else 0.8f)),
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = if (hasOutput) 2.dp else 1.dp,
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Step 2 · Output",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recipe", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                IconButton(
                    onClick = { showOutputInfo = true },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "About conversion output" },
                ) {
                    Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = colors.onSurfaceVariant)
                }
            }
            Surface(
                onClick = onOutput,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = shapes.medium,
                color = colors.surfaceContainerHighest,
                tonalElevation = 0.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Save to", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        Text(
                            outputLabel ?: "Choose output folder",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = if (outputLabel == null) "Choose" else "Change",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            RecipeSection(label = "Format") {
                val formats = listOf(OutputFormat.JPEG, OutputFormat.WEBP, OutputFormat.AVIF)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    formats.forEachIndexed { index, item ->
                        val supported = item.isSupported
                        SegmentedButton(
                            selected = format == item,
                            onClick = { if (supported) onFormat(item) },
                            enabled = enabled && supported,
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = formats.size),
                            label = { Text(item.label) },
                        )
                    }
                }
            }
            RecipeSection(label = "Color space") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = colorGamut == ColorGamut.SRGB,
                        onClick = { onColorGamut(ColorGamut.SRGB) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text("sRGB") },
                    )
                    SegmentedButton(
                        selected = colorGamut == ColorGamut.DISPLAY_P3,
                        onClick = { onColorGamut(ColorGamut.DISPLAY_P3) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("Display P3") },
                    )
                }
            }
            RecipeSection(label = "Conversion") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == ConversionMode.DEVELOP_RAW,
                        onClick = { onMode(ConversionMode.DEVELOP_RAW) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text("Develop") },
                    )
                    SegmentedButton(
                        selected = mode == ConversionMode.CAMERA_LOOK,
                        onClick = { onMode(ConversionMode.CAMERA_LOOK) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("Camera look") },
                    )
                }
            }
            RecipeSection(label = "Resolution") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = resolution == OutputResolution.STANDARD,
                        onClick = { onResolution(OutputResolution.STANDARD) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text("Standard") },
                    )
                    SegmentedButton(
                        selected = resolution == OutputResolution.ORIGINAL,
                        onClick = { onResolution(OutputResolution.ORIGINAL) },
                        enabled = enabled,
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("Original") },
                    )
                }
            }
            RecipeSection(label = "Filename") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("{name}", "{name}_{edge}", "{name}_{mode}").forEach { preset ->
                        val selected = nameTemplate == preset
                        if (selected) {
                            FilledTonalButton(
                                onClick = { onNameTemplate(preset) },
                                enabled = enabled,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colors.primaryContainer,
                                    contentColor = colors.onPrimaryContainer,
                                ),
                            ) { Text(preset, style = MaterialTheme.typography.labelMedium) }
                        } else {
                            OutlinedButton(
                                onClick = { onNameTemplate(preset) },
                                enabled = enabled,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            ) { Text(preset, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
                OutlinedTextField(
                    value = nameTemplate,
                    onValueChange = onNameTemplate,
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Template") },
                    supportingText = {
                        Text("Example: $exampleName · tokens {name} {edge} {mode}")
                    },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                RecipeToggleRow(
                    title = "Keep metadata",
                    subtitle = "Camera, lens & GPS",
                    checked = metadata,
                    enabled = enabled,
                    onCheckedChange = onMetadata,
                    contentDescription = "Keep metadata, including GPS location",
                )
                RecipeToggleRow(
                    title = "Completion alert",
                    subtitle = "Haptic pulse and chime when done",
                    checked = completionFeedback,
                    enabled = enabled,
                    onCheckedChange = onCompletionFeedback,
                    contentDescription = "Alert with haptics and audio when conversion finishes",
                )
            }
            Text(
                "Output only — RAW originals stay untouched.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecipeSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun RecipeToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.semantics { this.contentDescription = contentDescription },
        )
    }
}

@Composable
private fun PhotoDetails(source: RawSourceSummary.Single) {
    var open by rememberSaveable(source.uri.toString()) { mutableStateOf(false) }
    Column {
        TextButton(
            onClick = { open = !open },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(if (open) "Hide photo details" else "Photo details")
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(motionTween(180)),
            exit = fadeOut(motionTween(140)),
        ) {
            val metadata = source.metadata
            val rows = listOfNotNull(
                metadata.lens?.let { "Lens" to it },
                metadata.dateTimeOriginal?.let { "Captured" to it },
                metadata.iso?.let { "ISO" to it },
                metadata.aperture?.let { "Aperture" to "f/$it" },
                metadata.exposure?.let { "Exposure" to "${it}s" },
                metadata.focalLength?.let { "Focal length" to "$it mm" },
                metadata.orientation?.let { "Source orientation" to it.toString() },
            )
            Column {
                if (rows.isEmpty()) {
                    Text("No additional metadata found.", style = MaterialTheme.typography.bodySmall)
                }
                for ((label, value) in rows) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** Native interpretation of V2 StatusMark: glyph and spoken result, never colour alone. */
@Composable
internal fun StatusMark(state: String) {
    val colors = MaterialTheme.colorScheme
    val morph = motionMillis(180)
    Crossfade(targetState = state, animationSpec = tween(morph), label = "status-mark") { current ->
        when (current) {
            "running" -> CircularProgressIndicator(
                Modifier.size(18.dp),
                color = colors.primary,
                strokeWidth = 1.5.dp,
            )
            else -> Icon(
                painterResource(
                    when (current) {
                        "done" -> R.drawable.ic_check
                        "failed" -> R.drawable.ic_x
                        "skipped" -> R.drawable.ic_minus
                        else -> R.drawable.ic_chevron_right
                    },
                ),
                contentDescription = when (current) {
                    "done" -> "Saved"
                    "failed" -> "Failed"
                    "skipped" -> "Skipped"
                    "pending" -> "Pending"
                    else -> "Waiting"
                },
                tint = when (current) {
                    "failed" -> colors.error
                    "done" -> colors.tertiary
                    "skipped" -> colors.onSurfaceVariant
                    else -> colors.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** V2 ThoughtLine's useful contract: highlight only the phase the native engine emitted. */
@Composable
internal fun PhaseTrace(phase: String) {
    val phases = listOf(
        "Camera" to "Extracting",
        "Resize" to "Resizing",
        "Develop" to "Developing",
        "Optimize" to "Optimizing",
        "Metadata" to "Restoring",
        "Save" to "Saving",
    )
    val phaseIn = motionMillis(180)
    val phaseOut = motionMillis(120)
    AnimatedContent(
        targetState = phase,
        transitionSpec = { fadeIn(tween(phaseIn)) togetherWith fadeOut(tween(phaseOut)) },
        label = "phase-trace",
    ) { activePhase ->
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((label, prefix) in phases) {
                val active = activePhase.startsWith(prefix)
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun compactWindow(): Boolean {
    val height = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) { height.toDp() < 480.dp }
}

internal fun formatSize(bytes: Long?): String = bytes?.let { "%.1f MiB".format(it / 1048576.0) } ?: "Size unavailable"
