package com.shahgul.rawphotoconverter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shahgul.rawphotoconverter.conversion.BatchState
import com.shahgul.rawphotoconverter.conversion.ConversionResult
import com.shahgul.rawphotoconverter.ui.theme.ThemePreference
import kotlinx.coroutines.delay
import com.shahgul.rawphotoconverter.data.savedJpegUris
import com.shahgul.rawphotoconverter.data.ExportOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ResultsScreen(
    batch: BatchState,
    error: String?,
    onBack: () -> Unit,
    onConvertMore: () -> Unit,
    onOpenJpeg: (Uri) -> Unit,
    onShareJpeg: (Uri) -> Unit,
    onRetryFailed: (String?) -> Unit,
    canRetry: Boolean,
    onViewProcessing: () -> Unit,
    themePreference: ThemePreference,
    onThemeCycle: () -> Unit,
    onShareAll: () -> Unit = {},
    sharing: Boolean = false,
    exportOptions: ExportOptions = ExportOptions(),
    onExportOptionsChange: (ExportOptions) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val resultIn = motionMillis(220)
    val resultOut = motionMillis(160)
    val settleDelay = motionMillis(40).toLong()
    var settled by remember { mutableStateOf(false) }
    val failedCount = batch.results.count { it.error != null }
    val skippedCount = batch.results.count { it.skipped }
    val savedCount = batch.results.count { it.error == null && !it.skipped && it.output != null }
    val measuredBytes = batch.results.mapNotNull { result ->
        if (result.error == null) result.sizeBytes else null
    }
    val many = batch.results.size > 1
    val shareableCount = savedJpegUris(batch.results).size
    val summaryResult = when (batch.results.size) {
        0 -> null
        1 -> batch.results.first()
        else -> batch.results.lastOrNull { it.output != null } ?: batch.results.last()
    }
    val batchElapsedMs = if (batch.startedAtMs > 0 && batch.finishedAtMs != null) {
        (batch.finishedAtMs - batch.startedAtMs).coerceAtLeast(0)
    } else {
        null
    }
    LaunchedEffect(batch.finishedAtMs, batch.results.size) {
        settled = false
        if (batch.finishedAtMs != null && batch.results.isNotEmpty()) {
            delay(settleDelay)
            settled = true
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Adjust recipe")
                    }
                    Spacer(Modifier.weight(1f))
                    if (batch.running) {
                        TextButton(onClick = onViewProcessing, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("View processing")
                        }
                    }
                    ThemeToggle(themePreference, onThemeCycle)
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Surface(
                            color = colors.surfaceContainerLow,
                            shape = shapes.extraLarge,
                            tonalElevation = if (settled) 3.dp else 1.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Results",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    batch.phase,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.primary,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                                batch.sourceLabel?.let { label ->
                                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }
                                Text(
                                    if (many) {
                                        "${batch.results.size} photos · $savedCount saved · $skippedCount skipped · $failedCount failed"
                                    } else {
                                        "$savedCount saved · $skippedCount skipped · $failedCount failed"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (many && measuredBytes.isNotEmpty()) {
                                    Text(
                                        "${formatSize(measuredBytes.sum())} across ${measuredBytes.size} output files",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                                batchElapsedMs?.let { elapsed ->
                                    Text(
                                        "Finished in ${formatElapsed(elapsed)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                                batch.settings?.let { settings ->
                                    Text(
                                        "${modeLabel(settings.mode)} / ${resolutionLabel(settings.resolution)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                    Text(
                                        "Save to: ${settings.destination}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                                if (failedCount > 0 && canRetry && !batch.running) {
                                    TextButton(
                                        onClick = { onRetryFailed(null) },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Text("Retry failed files")
                                    }
                                }
                                if (!many) summaryResult?.let { result ->
                                    Text(
                                        result.source,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    ResultFacts(result)
                                    result.output?.let { uri ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilledTonalButton(
                                                onClick = { onOpenJpeg(uri) },
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                                shape = shapes.medium,
                                            ) {
                                                Text(if (result.skipped) "Open existing" else "Open JPEG")
                                            }
                                            OutlinedButton(
                                                onClick = { onShareJpeg(uri) },
                                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                                shape = shapes.medium,
                                            ) {
                                                Text("Share")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    error?.let {
                        item {
                            Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    batch.message?.let {
                        item {
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (shareableCount > 0) {
                        item {
                            ExportOptionsSection(exportOptions, onExportOptionsChange, !sharing && !batch.running)
                        }
                    }
                    if (batch.results.isEmpty()) {
                        item {
                            Surface(
                                color = colors.surfaceContainer,
                                shape = shapes.large,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "No results yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center,
                                    )
                                    Text(
                                        "Converted, skipped, and failed files will list here after a run.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                    items(
                        if (batch.results.size == 1) emptyList() else batch.results,
                        key = { "${it.source}:${it.output}:${it.error}:${it.skipped}" },
                    ) { result ->
                        ResultRow(
                            result = result,
                            onOpenJpeg = onOpenJpeg,
                            onShareJpeg = onShareJpeg,
                            onRetryFailed = onRetryFailed,
                            canRetry = canRetry && !batch.running,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(resultIn),
                                fadeOutSpec = tween(resultOut),
                                placementSpec = tween(resultIn),
                            ),
                        )
                    }
                }
                Surface(color = colors.surfaceContainerLow, tonalElevation = 2.dp) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    context.getSystemService(ClipboardManager::class.java)
                                        .setPrimaryClip(ClipData.newPlainText("Conversion report", conversionReport(batch)))
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) { Text("Copy report") }
                            batch.results.lastOrNull { it.output != null }?.output?.let { uri ->
                                TextButton(
                                    onClick = { onOpenJpeg(uri) },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) { Text("Open latest JPEG") }
                            }
                        }
                        if (shareableCount > 1) {
                            TextButton(onClick = onShareAll, enabled = !batch.running && !sharing,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(if (sharing) "Preparing JPEGs…" else "Share $shareableCount JPEGs")
                            }
                        }
                        Button(
                            onClick = onConvertMore,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            shape = shapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryContainer,
                                contentColor = colors.onPrimaryContainer,
                            ),
                        ) {
                            Text("Convert more", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportOptionsSection(options: ExportOptions, onChange: (ExportOptions) -> Unit, enabled: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            contentPadding = PaddingValues(0.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Export options", style = MaterialTheme.typography.titleMedium)
                Text(if (expanded) "Hide" else "Show", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (expanded) {
            // Add future option rows here; all share actions use the same typed options.
            // The row carries the 48dp touch target; the box itself stays a quiet 20dp glyph.
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = options.removeExif, enabled = enabled, role = Role.Checkbox,
                onValueChange = { onChange(options.copy(removeExif = it)) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Checkbox(
                    checked = options.removeExif,
                    onCheckedChange = null,
                    enabled = enabled,
                    modifier = Modifier.size(20.dp),
                )
                Text("Remove EXIF metadata", style = MaterialTheme.typography.bodyMedium)
            }
            Text("Removes camera, location, dates and other embedded metadata from shared copies. Saved JPEGs stay unchanged.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            if (options.removeExif) Text("Photos that rely on EXIF rotation are rotated and re-encoded for correct viewing.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResultFacts(result: ConversionResult) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        resultFacts(result).forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    label,
                    modifier = Modifier.weight(0.38f),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    value,
                    modifier = Modifier.weight(0.62f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (label == "Error") colors.error else colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ResultRow(
    result: ConversionResult,
    onOpenJpeg: (Uri) -> Unit,
    onShareJpeg: (Uri) -> Unit,
    onRetryFailed: (String?) -> Unit,
    canRetry: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val mark = when {
        result.error != null -> "failed"
        result.skipped -> "pending"
        else -> "done"
    }
    Surface(
        color = colors.surfaceContainer,
        shape = shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusMark(mark)
                Text(result.source, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            ResultFacts(result)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                result.output?.let { uri ->
                    TextButton(onClick = { onOpenJpeg(uri) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text(if (result.skipped) "Open existing" else "Open JPEG")
                    }
                    TextButton(onClick = { onShareJpeg(uri) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Share")
                    }
                }
                if (result.error != null && result.sourceUri != null && canRetry) {
                    TextButton(onClick = { onRetryFailed(result.sourceUri) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Retry file")
                    }
                }
            }
        }
    }
}
