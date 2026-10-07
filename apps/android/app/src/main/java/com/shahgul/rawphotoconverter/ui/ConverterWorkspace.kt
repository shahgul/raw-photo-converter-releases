package com.shahgul.rawphotoconverter.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.shahgul.rawphotoconverter.ui.theme.MeasurementFont
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
                                    SourceHero(source, loading, enabled, onOpenRaw, onOpenFolder)
                                }
                                Column(Modifier.weight(1f)) {
                                    RecipePanel(outputLabel, preserveMetadata, enabled, onOutput, onMetadata, mode, resolution, onMode, onResolution)
                                }
                            }
                        } else {
                            SourceHero(source, loading, enabled, onOpenRaw, onOpenFolder)
                            RecipePanel(outputLabel, preserveMetadata, enabled, onOutput, onMetadata, mode, resolution, onMode, onResolution)
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
                                    batch.running -> "${batch.results.size} of ${batch.total} processed · ${batch.phase}"
                                    loading -> "Reading your photos…"
                                    source == null -> "Choose a RAW photo or folder to begin"
                                    count == 0 -> "No Sony ARW files found in this folder"
                                    outputLabel == null -> "Choose a folder for your JPEGs"
                                    else -> "Originals stay untouched · existing JPEGs are skipped"
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
                                else if (count > 1) "Convert $count photos"
                                else "Convert to JPEG",
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
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val landed = source != null && !loading
    val sourceKey = when (source) {
        null -> if (loading) "loading" else "empty"
        is RawSourceSummary.Single -> "single:${source.uri}"
        is RawSourceSummary.Folder -> "folder:${source.uri}:${source.rawCount}"
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
                            "A Sony RAW, or a folder of ARWs. Everything stays on your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    else -> {
                        val selected = source!!
                        val photoCount = when (selected) {
                            is RawSourceSummary.Single -> 1
                            is RawSourceSummary.Folder -> selected.rawCount
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
                                    when (selected) {
                                        is RawSourceSummary.Single -> "1 RAW · ${formatSize(selected.sizeBytes)}"
                                        is RawSourceSummary.Folder -> "${selected.rawCount} RAW · ${formatSize(selected.totalBytes)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    fontFamily = MeasurementFont,
                                )
                                if (selected is RawSourceSummary.Single) {
                                    selected.metadata.camera?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (selected is RawSourceSummary.Folder) {
                            Text(
                                "This folder only — subfolders aren't included.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
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
                    Text(if (source == null) "Add photos" else "Change RAW")
                }
                OutlinedButton(
                    onClick = onFolder,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    shape = shapes.medium,
                ) {
                    Icon(painterResource(R.drawable.ic_folder_open), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Folder")
                }
            }
            if (source is RawSourceSummary.Single) PhotoDetails(source)
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
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var details by rememberSaveable { mutableStateOf(false) }
    val recipe = remember(mode, resolution) { ConversionRecipe(mode = mode, maxLongEdge = resolution.maxEdge) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Recipe", style = MaterialTheme.typography.titleMedium)
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
            Text(
                if (mode == ConversionMode.CAMERA_LOOK) {
                    "Uses the embedded JPEG preview when available."
                } else {
                    "Develops sensor data for full RAW quality."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
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
            Text(
                if (resolution == OutputResolution.ORIGINAL) {
                    "Keeps available resolution. Larger RAW outputs use more memory."
                } else {
                    "Up to 4000 px on the long edge."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Text(
                "${if (recipe.maxLongEdge == 0) "Original resolution" else "${recipe.maxLongEdge} px max"} · ${recipe.targetBytes / 1048576} MiB target",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = MeasurementFont,
                color = colors.onSurfaceVariant,
            )
        }
        Surface(color = colors.surfaceContainer, shape = shapes.large, modifier = Modifier.fillMaxWidth()) {
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
        }
        TextButton(
            onClick = { details = !details },
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(if (details) "Hide output details" else "Output details")
        }
        AnimatedVisibility(
            visible = details,
            enter = fadeIn(motionTween(180)),
            exit = fadeOut(motionTween(140)),
        ) {
            Text(
                "Original aspect ratio. No crop or upscaling. Camera JPEG pixels are copied without recompression when they fit the selected limit and size target; otherwise they are resized/re-encoded. Quality Q${recipe.maxQuality} down to Q${recipe.minQuality}; the quality floor takes priority if ${recipe.targetBytes / 1048576} MiB isn't possible. Supported EXIF/XMP is kept when enabled; maker notes and binary IPTC aren't copied.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
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
