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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
    format: OutputFormat = OutputFormat.JPEG, onFormat: (OutputFormat) -> Unit = {},
    colorGamut: ColorGamut = ColorGamut.SRGB, onColorGamut: (ColorGamut) -> Unit = {},
    completionFeedback: Boolean = true, onCompletionFeedback: (Boolean) -> Unit = {},
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
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        if (expanded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Column(Modifier.weight(1f)) {
                                    SourceHero(source, loading, enabled, onOpenRaw, onOpenFolder, onRemovePhoto)
                                }
                                Column(Modifier.weight(1f)) {
                                    RecipePanel(
                                        outputLabel, preserveMetadata, enabled, onOutput, onMetadata,
                                        mode, resolution, onMode, onResolution,
                                        format, onFormat, colorGamut, onColorGamut,
                                        completionFeedback, onCompletionFeedback,
                                    )
                                }
                            }
                        } else {
                            SourceHero(source, loading, enabled, onOpenRaw, onOpenFolder, onRemovePhoto)
                            RecipePanel(
                                outputLabel, preserveMetadata, enabled, onOutput, onMetadata,
                                mode, resolution, onMode, onResolution,
                                format, onFormat, colorGamut, onColorGamut,
                                completionFeedback, onCompletionFeedback,
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
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = if (landed) 0.75f else 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(landIn)),
        tonalElevation = if (landed) 2.dp else 1.dp,
    ) {
        Column(
            Modifier.padding(if (landed) 16.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (landed) 12.dp else 16.dp),
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
                                        var pendingRemoveUri by remember { mutableStateOf<Uri?>(null) }
                                        val pendingLabel = pendingRemoveUri?.let { uri ->
                                            selected.photos.firstOrNull { it.uri == uri }?.label
                                        }
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
                                                    IconButton(
                                                        onClick = { pendingRemoveUri = photo.uri },
                                                        enabled = enabled,
                                                    ) {
                                                        Icon(painterResource(R.drawable.ic_x), contentDescription = "Remove ${photo.label}")
                                                    }
                                                }
                                            }
                                        }
                                        pendingRemoveUri?.let { uri ->
                                            RemovePhotoConfirmationDialog(
                                                photoLabel = pendingLabel,
                                                onConfirm = {
                                                    onRemovePhoto(uri)
                                                    pendingRemoveUri = null
                                                },
                                                onDismiss = { pendingRemoveUri = null },
                                            )
                                        }
                                    }
                                    is RawSourceSummary.Single -> {
                                        var showHistogram by rememberSaveable(selected.uri.toString()) { mutableStateOf(false) }
                                        var showDetails by rememberSaveable(selected.uri.toString()) { mutableStateOf(false) }

                                        SinglePhotoPreview(
                                            source = selected,
                                            compactHeight = compactWindow(),
                                            enabled = enabled,
                                            histogramActive = showHistogram,
                                            detailsActive = showDetails,
                                            onToggleHistogram = { showHistogram = !showHistogram },
                                            onToggleDetails = { showDetails = !showDetails },
                                            onRemove = { onRemovePhoto(selected.uri) },
                                        )
                                        val previewBitmap = (selected.preview as? RawPreviewState.Ready)?.bitmap
                                        HistogramCard(
                                            previewBitmap = previewBitmap,
                                            expanded = showHistogram,
                                            onExpandedChange = { showHistogram = it },
                                            showHeader = false,
                                        )
                                        PhotoDetails(
                                            source = selected,
                                            open = showDetails,
                                            onOpenChange = { showDetails = it },
                                            showHeader = false,
                                        )
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
        }
    }
}

@Composable
private fun SinglePhotoPreview(
    source: RawSourceSummary.Single,
    compactHeight: Boolean,
    enabled: Boolean = true,
    histogramActive: Boolean = false,
    detailsActive: Boolean = false,
    onToggleHistogram: () -> Unit = {},
    onToggleDetails: () -> Unit = {},
    onRemove: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val previewHeight = if (compactHeight) 170.dp else 260.dp
    var loupeOpen by rememberSaveable(source.uri.toString()) { mutableStateOf(false) }
    var confirmRemove by rememberSaveable(source.uri.toString()) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(shapes.medium)
                .background(colors.surfaceContainerHighest),
        ) {
            when (val preview = source.preview) {
                RawPreviewState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.primary, strokeWidth = 2.dp)
                    }
                }
                is RawPreviewState.Ready -> {
                    Image(
                        bitmap = preview.bitmap.asImageBitmap(),
                        contentDescription = "Embedded camera JPEG preview for ${source.label}",
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { loupeOpen = true },
                                    onDoubleTap = { loupeOpen = true },
                                )
                            },
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

            // Top gradient scrim for high-key photo contrast & overlay legibility
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(68.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.68f), Color.Transparent),
                        ),
                    ),
            )

            // Top action controls: Overlaid pills & ✕ dismiss button
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 10.dp, end = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left cluster: Histogram & EXIF pills (horizontally scrollable if screen/font requires)
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val hasPreview = source.preview is RawPreviewState.Ready
                    if (hasPreview) {
                        OverlayChip(
                            label = "Histogram",
                            iconRes = R.drawable.ic_chart_bar,
                            active = histogramActive,
                            onClick = onToggleHistogram,
                        )
                    }
                    OverlayChip(
                        label = "EXIF",
                        iconRes = R.drawable.ic_aperture,
                        active = detailsActive,
                        onClick = onToggleDetails,
                    )
                }

                // Right cluster: Remove photo circle button (48dp accessible touch target, 32dp tactile disc)
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        onClick = { confirmRemove = true },
                        enabled = enabled,
                        shape = CircleShape,
                        color = Color(0xFF121418).copy(alpha = 0.88f),
                        contentColor = Color.White,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_x),
                                contentDescription = "Remove photo",
                                modifier = Modifier.size(15.dp),
                                tint = Color.White.copy(alpha = 0.92f),
                            )
                        }
                    }
                }
            }

            // Bottom gradient scrim
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
                val metaSubtitle = buildString {
                    source.metadata.camera?.let { append("$it · ") }
                    append("1 RAW · ")
                    append(formatSize(source.sizeBytes))
                }
                Text(
                    metaSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.84f),
                    fontFamily = MeasurementFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

        if (source.preview is RawPreviewState.Ready) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Embedded camera JPEG preview", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Develop RAW may render differently. Tap the photo to inspect 100% details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        if (confirmRemove) {
            RemovePhotoConfirmationDialog(
                photoLabel = source.label,
                onConfirm = {
                    confirmRemove = false
                    onRemove()
                },
                onDismiss = { confirmRemove = false },
            )
        }

        if (loupeOpen && source.preview is RawPreviewState.Ready) {
            PhotoLoupeDialog(
                previewBitmap = source.preview.bitmap,
                title = source.label,
                onDismiss = { loupeOpen = false },
            )
        }
    }
}

@Composable
private fun RemovePhotoConfirmationDialog(
    photoLabel: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove photo?") },
        text = {
            Text(
                if (photoLabel != null) {
                    "$photoLabel will be removed from this queue. Your original file is not deleted."
                } else {
                    "This removes the photo from your conversion queue. Your original file is not deleted."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep") }
        },
    )
}

@Composable
private fun OverlayChip(
    label: String,
    iconRes: Int,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeBorder = Color(0xFFF97316) // Safelight orange accent
    val inactiveBorder = Color.White.copy(alpha = 0.25f)
    val activeBg = Color(0xFF1E1712).copy(alpha = 0.94f)
    val inactiveBg = Color(0xFF121418).copy(alpha = 0.88f)
    val activeContent = Color(0xFFFFA057)
    val inactiveContent = Color.White.copy(alpha = 0.92f)

    Box(
        modifier = modifier.heightIn(min = 48.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (active) activeBg else inactiveBg,
            contentColor = if (active) activeContent else inactiveContent,
            border = BorderStroke(1.dp, if (active) activeBorder else inactiveBorder),
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = if (active) activeContent else inactiveContent,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) activeContent else inactiveContent,
                )
            }
        }
    }
}

/**
 * Fullscreen Interactive 100% Loupe / Pinch-to-Zoom inspection dialog.
 * Allows photographers to check critical focus, fine detail, and exposure clipping without leave.
 */
@Composable
private fun PhotoLoupeDialog(
    previewBitmap: android.graphics.Bitmap,
    title: String,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var controlsVisible by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090A0C)),
        ) {
            // Interactive Pan & Zoom Viewport
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val containerWidth = constraints.maxWidth.toFloat()
                val containerHeight = constraints.maxHeight.toFloat()
                val fitDims = remember(containerWidth, containerHeight, previewBitmap) {
                    PhotoLoupeMath.calculateFitDimensions(
                        containerWidth = containerWidth,
                        containerHeight = containerHeight,
                        imageWidth = previewBitmap.width.toFloat(),
                        imageHeight = previewBitmap.height.toFloat(),
                    )
                }

                LaunchedEffect(containerWidth, containerHeight) {
                    if (scale <= 1f) {
                        offset = Offset.Zero
                    } else {
                        val bounds = PhotoLoupeMath.calculateMaxPanOffsets(
                            renderedWidth = fitDims.width,
                            renderedHeight = fitDims.height,
                            scale = scale,
                            containerWidth = containerWidth,
                            containerHeight = containerHeight,
                        )
                        offset = Offset(
                            x = offset.x.coerceIn(-bounds.width, bounds.width),
                            y = offset.y.coerceIn(-bounds.height, bounds.height),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(containerWidth, containerHeight) {
                            detectTapGestures(
                                onTap = { controlsVisible = !controlsVisible },
                                onDoubleTap = { tapOffset ->
                                    if (scale > 1.05f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        val targetScale = 2.5f
                                        offset = PhotoLoupeMath.calculateDoubleTapOffset(
                                            tapOffset = tapOffset,
                                            containerWidth = containerWidth,
                                            containerHeight = containerHeight,
                                            targetScale = targetScale,
                                            renderedWidth = fitDims.width,
                                            renderedHeight = fitDims.height,
                                        )
                                        scale = targetScale
                                    }
                                },
                            )
                        }
                        .pointerInput(containerWidth, containerHeight) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1.0f, 6.0f)
                                scale = newScale
                                if (newScale <= 1.0f) {
                                    offset = Offset.Zero
                                } else {
                                    val bounds = PhotoLoupeMath.calculateMaxPanOffsets(
                                        renderedWidth = fitDims.width,
                                        renderedHeight = fitDims.height,
                                        scale = newScale,
                                        containerWidth = containerWidth,
                                        containerHeight = containerHeight,
                                    )
                                    val newOffset = offset + pan
                                    offset = Offset(
                                        x = newOffset.x.coerceIn(-bounds.width, bounds.width),
                                        y = newOffset.y.coerceIn(-bounds.height, bounds.height),
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Zoomed preview for $title",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                        contentScale = ContentScale.Fit,
                    )
                }

                // Top Bar Scrim and Controls (fades with controlsVisible)
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(motionTween(180)),
                    exit = fadeOut(motionTween(140)),
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
                                    ),
                                ),
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${previewBitmap.width} × ${previewBitmap.height} px · ${PhotoLoupeMath.formatMagnification(scale)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.72f),
                                    fontFamily = MeasurementFont,
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val isZoomed = scale > 1.05f
                                Surface(
                                    onClick = {
                                        if (isZoomed) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f
                                            offset = Offset.Zero
                                        }
                                    },
                                    shape = CircleShape,
                                    color = if (isZoomed) Color(0xFF1E1712).copy(alpha = 0.94f) else Color(0xFF121418).copy(alpha = 0.88f),
                                    contentColor = if (isZoomed) Color(0xFFFFA057) else Color.White.copy(alpha = 0.92f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isZoomed) Color(0xFFF97316) else Color.White.copy(alpha = 0.25f),
                                    ),
                                    modifier = Modifier.height(34.dp),
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                    ) {
                                        Text(
                                            text = if (isZoomed) "Fit" else "2.5×",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }

                                // Close button (48dp touch target, 36dp pill surface)
                                Box(
                                    modifier = Modifier.size(48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Surface(
                                        onClick = onDismiss,
                                        shape = CircleShape,
                                        color = Color(0xFF121418).copy(alpha = 0.88f),
                                        contentColor = Color.White,
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                        modifier = Modifier.size(36.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_x),
                                                contentDescription = "Close loupe",
                                                modifier = Modifier.size(16.dp),
                                                tint = Color.White.copy(alpha = 0.92f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom hint bar (fades with controlsVisible)
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(motionTween(180)),
                    exit = fadeOut(motionTween(140)),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                ),
                            ),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text(
                            text = "Pinch to zoom · Double-tap for 2.5× · Tap for clean view",
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(bottom = 14.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
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
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var showOutputInfo by rememberSaveable { mutableStateOf(false) }
    val recipe = remember(mode, resolution, format, colorGamut) {
        ConversionRecipe(
            mode = mode,
            maxLongEdge = resolution.maxEdge,
            format = format,
            colorGamut = colorGamut,
        )
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Step 2 · Output",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Recipe", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
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
            shape = shapes.large,
            color = colors.surfaceContainer,
            tonalElevation = 1.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Save JPEGs to", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Format", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Color space", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(
                    if (colorGamut == ColorGamut.DISPLAY_P3) "Wide gamut" else "Standard sRGB",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Conversion", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Resolution", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
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
        Surface(color = colors.surfaceContainer, shape = shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Keep metadata", style = MaterialTheme.typography.bodyLarge)
                        Text("Camera, lens & GPS", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Switch(
                        checked = metadata,
                        onCheckedChange = onMetadata,
                        enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = "Keep metadata, including GPS location" },
                    )
                }
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Completion alert", style = MaterialTheme.typography.bodyLarge)
                        Text("Haptic pulse and chime when done", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Switch(
                        checked = completionFeedback,
                        onCheckedChange = onCompletionFeedback,
                        enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = "Alert with haptics and audio when conversion finishes" },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoDetails(
    source: RawSourceSummary.Single,
    open: Boolean = false,
    onOpenChange: ((Boolean) -> Unit)? = null,
    showHeader: Boolean = true,
) {
    var internalOpen by rememberSaveable(source.uri.toString()) { mutableStateOf(false) }
    val isOpen = if (onOpenChange != null) open else internalOpen
    fun setOpen(value: Boolean) {
        if (onOpenChange != null) onOpenChange(value)
        else internalOpen = value
    }

    Column {
        if (showHeader) {
            TextButton(
                onClick = { setOpen(!isOpen) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(if (isOpen) "Hide photo details" else "Photo details")
            }
        }
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(motionTween(180)),
            exit = fadeOut(motionTween(140)),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Camera & EXIF metadata",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    val metadata = source.metadata
                    val rows = listOfNotNull(
                        metadata.camera?.let { "Camera" to it },
                        metadata.lens?.let { "Lens" to it },
                        metadata.dateTimeOriginal?.let { "Captured" to it },
                        metadata.iso?.let { "ISO" to it },
                        metadata.aperture?.let { "Aperture" to "f/$it" },
                        metadata.exposure?.let { "Exposure" to "${it}s" },
                        metadata.focalLength?.let { "Focal length" to "$it mm" },
                        metadata.orientation?.let { "Source orientation" to it.toString() },
                    )
                    if (rows.isEmpty()) {
                        Text("No additional metadata found.", style = MaterialTheme.typography.bodySmall)
                    }
                    for ((label, value) in rows) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontFamily = MeasurementFont)
                        }
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
        if (current == "running") {
            CircularProgressIndicator(Modifier.size(18.dp), color = colors.primary, strokeWidth = 1.5.dp)
        } else {
            Icon(
                painterResource(
                    if (current == "done") R.drawable.ic_check
                    else if (current == "failed") R.drawable.ic_x
                    else R.drawable.ic_chevron_right,
                ),
                contentDescription = when (current) {
                    "done" -> "Saved"
                    "failed" -> "Failed"
                    else -> "Skipped or stopped"
                },
                tint = if (current == "failed") colors.error else if (current == "done") colors.tertiary else colors.onSurfaceVariant,
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
