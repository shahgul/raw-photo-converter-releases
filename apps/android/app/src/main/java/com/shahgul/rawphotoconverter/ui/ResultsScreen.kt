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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ResultsScreen(
    batch: BatchState,
    error: String?,
    onBack: () -> Unit,
    onConvertMore: () -> Unit,
    onOpenJpeg: (Uri) -> Unit,
    onShareJpeg: (Uri) -> Unit,
    onRetryFailed: () -> Unit,
    canRetry: Boolean,
    onViewProcessing: () -> Unit,
    themePreference: ThemePreference,
    onThemeCycle: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    val resultIn = motionMillis(220)
    val resultOut = motionMillis(160)
    val settleDelay = motionMillis(40).toLong()
    var settled by remember { mutableStateOf(false) }
    val failedCount = batch.results.count { it.error != null }
    val savedCount = batch.results.count { it.output != null }
    val skippedCount = batch.results.count { it.skipped }
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
                        Text("Back to setup")
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
                                Text(
                                    "$savedCount saved · $skippedCount skipped · $failedCount failed",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
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
                                        onClick = onRetryFailed,
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) {
                                        Text("Retry failed files")
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
                    items(batch.results, key = { "${it.source}:${it.output}:${it.error}:${it.skipped}" }) { result ->
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
private fun ResultRow(
    result: ConversionResult,
    onOpenJpeg: (Uri) -> Unit,
    onShareJpeg: (Uri) -> Unit,
    onRetryFailed: () -> Unit,
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
            Text(
                resultDescription(result),
                style = MaterialTheme.typography.bodySmall,
                color = if (result.error != null) colors.error else colors.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                result.output?.let { uri ->
                    TextButton(onClick = { onOpenJpeg(uri) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Open JPEG")
                    }
                    TextButton(onClick = { onShareJpeg(uri) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Share")
                    }
                }
                if (result.error != null && canRetry) {
                    TextButton(onClick = onRetryFailed, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
