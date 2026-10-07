package com.shahgul.rawphotoconverter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Debug
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shahgul.rawphotoconverter.R
import com.shahgul.rawphotoconverter.conversion.BatchState
import com.shahgul.rawphotoconverter.ui.theme.MeasurementFont
import com.shahgul.rawphotoconverter.ui.theme.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun ProcessingScreen(
    batch: BatchState,
    error: String?,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onViewResults: () -> Unit,
    themePreference: ThemePreference,
    onThemeCycle: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val shapes = MaterialTheme.shapes
    var recipeTab by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var memoryKiB by remember { mutableStateOf<Long?>(null) }
    val phaseIn = motionMillis(200)
    val phaseOut = motionMillis(140)
    LaunchedEffect(batch.running, batch.startedAtMs) {
        now = SystemClock.elapsedRealtime()
        while (batch.running) {
            delay(1000)
            now = SystemClock.elapsedRealtime()
        }
    }
    LaunchedEffect(batch.running) {
        do {
            memoryKiB = withContext(Dispatchers.Default) { Debug.getPss() }
            if (batch.running) delay(2000)
        } while (batch.running)
    }
    val elapsed = if (batch.startedAtMs == 0L) null else
        ((batch.finishedAtMs ?: now) - batch.startedAtMs).coerceAtLeast(0)
    val saved = batch.results.count { it.output != null }
    val skipped = batch.results.count { it.skipped }
    val failed = batch.results.count { it.error != null }
    val progressFraction = if (batch.total > 1) {
        (batch.results.size.toFloat() / batch.total).coerceIn(0f, 1f)
    } else {
        null
    }
    val title = batch.sourceLabel
        ?: batch.current
        ?: batch.results.lastOrNull()?.source
        ?: "Conversion"
    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Back to setup")
                    }
                    Spacer(Modifier.weight(1f))
                    ThemeToggle(themePreference, onThemeCycle)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            AnimatedContent(
                                targetState = batch.phase,
                                transitionSpec = {
                                    (fadeIn(tween(phaseIn)) + slideInVertically(tween(phaseIn)) { it / 6 }) togetherWith
                                        (fadeOut(tween(phaseOut)) + slideOutVertically(tween(phaseOut)) { -it / 6 })
                                },
                                label = "phase-label",
                            ) { phase ->
                                Text(
                                    phase,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.primary,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ProgressChip("${batch.results.size} / ${batch.total}")
                            if (progressFraction != null) {
                                ProgressChip("${(progressFraction * 100).toInt()}%")
                            } else {
                                ProgressChip(elapsed?.let(::formatElapsed) ?: "—")
                            }
                        }
                    }
                    if (batch.running && batch.total <= 1) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = colors.primary,
                            trackColor = colors.surfaceContainerHighest,
                        )
                    } else if (batch.total > 0) {
                        LinearProgressIndicator(
                            progress = { progressFraction ?: (if (batch.running) 0f else 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = colors.primary,
                            trackColor = colors.surfaceContainerHighest,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetricCard(
                            label = "Memory",
                            value = memoryKiB?.let { "${it / 1024} MiB" } ?: "...",
                            hint = "App PSS",
                            modifier = Modifier.weight(1f),
                        )
                        MetricCard(
                            label = "Elapsed",
                            value = elapsed?.let(::formatElapsed) ?: "00:00",
                            hint = "Monotonic",
                            modifier = Modifier.weight(1f),
                        )
                        MetricCard(
                            label = "Outcome",
                            value = "$saved · $skipped · $failed",
                            hint = "Saved · skip · fail",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        color = colors.surfaceContainerLow,
                        shape = shapes.large,
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f)),
                    ) {
                        Column(Modifier.fillMaxSize().padding(10.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SegmentTab(
                                    selected = !recipeTab,
                                    onClick = { recipeTab = false },
                                    label = "Converter logs",
                                    icon = R.drawable.ic_terminal,
                                    modifier = Modifier.weight(1f),
                                )
                                SegmentTab(
                                    selected = recipeTab,
                                    onClick = { recipeTab = true },
                                    label = "Recipe",
                                    icon = R.drawable.ic_images,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            if (recipeTab) {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(4.dp),
                                ) {
                                    item {
                                        Surface(
                                            color = colors.surfaceContainer,
                                            shape = shapes.medium,
                                            border = BorderStroke(1.dp, colors.outlineVariant),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Column(
                                                Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                Text(
                                                    if (batch.running) "Conversion in progress" else batch.phase,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = colors.primary,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                PhaseTrace(batch.phase)
                                                batch.settings?.let { settings ->
                                                    InfoGrid(
                                                        entries = listOf(
                                                            "MODE" to modeLabel(settings.mode),
                                                            "RESOLUTION" to resolutionLabel(settings.resolution),
                                                            "METADATA" to if (settings.metadata) "Enabled" else "Disabled",
                                                            "DESTINATION" to settings.destination,
                                                            "FILES" to "${batch.total}",
                                                            "CURRENT" to (batch.current ?: "—"),
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(4.dp),
                                ) {
                                    item {
                                        Surface(
                                            color = colors.surfaceContainer,
                                            shape = shapes.medium,
                                            border = BorderStroke(1.dp, colors.outlineVariant),
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Column(
                                                Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                Text(
                                                    when {
                                                        batch.running -> "Conversion started"
                                                        else -> batch.phase
                                                    },
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = colors.primary,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                                InfoGrid(
                                                    entries = buildList {
                                                        batch.settings?.let {
                                                            add("MODE" to modeLabel(it.mode))
                                                            add("RESOLUTION" to resolutionLabel(it.resolution))
                                                            add(
                                                                "METADATA" to if (it.metadata) {
                                                                    "On (GPS when available)"
                                                                } else {
                                                                    "Off"
                                                                },
                                                            )
                                                            add("DESTINATION" to it.destination)
                                                        }
                                                        add("PROCESSED" to "${batch.results.size} / ${batch.total}")
                                                        add("SAVED" to "$saved")
                                                        add("SKIPPED" to "$skipped")
                                                        add("FAILED" to "$failed")
                                                        memoryKiB?.let { add("RUNTIME" to "App PSS ${it / 1024} MiB") }
                                                        elapsed?.let { add("ELAPSED" to formatElapsed(it)) }
                                                    },
                                                )
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
                                    if (batch.events.isEmpty()) {
                                        item {
                                            Text(
                                                "Waiting for processing events.",
                                                color = colors.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    } else {
                                        items(batch.events, key = { "${it.elapsedMs}:${it.message}" }) { event ->
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.Top,
                                            ) {
                                                Surface(
                                                    color = colors.outlineVariant,
                                                    shape = shapes.extraSmall,
                                                    modifier = Modifier.padding(top = 4.dp).size(width = 3.dp, height = 14.dp),
                                                ) {}
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        formatElapsed(event.elapsedMs),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontFamily = MeasurementFont,
                                                        color = colors.onSurfaceVariant,
                                                    )
                                                    Text(event.message, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Surface(color = colors.surfaceContainerLow) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (batch.running) {
                            OutlinedButton(
                                onClick = onCancel,
                                enabled = batch.phase != "Cancelling" && batch.phase != "Stopping after timeout",
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                                    .testTag("processing-cancel"),
                                shape = shapes.large,
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_x),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(if (batch.phase == "Cancelling") "Cancelling..." else "Cancel")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onViewResults,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                shape = shapes.large,
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
                            ) {
                                Text("View results")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                context.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("Conversion report", conversionReport(batch)))
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            shape = shapes.large,
                            border = BorderStroke(1.dp, colors.outlineVariant),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_copy),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("Copy")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressChip(text: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.primary.copy(alpha = 0.14f),
        contentColor = colors.primary,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = MeasurementFont,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = colors.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    color = colors.tertiary,
                    shape = CircleShape,
                    modifier = Modifier.size(6.dp),
                ) {}
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = MeasurementFont,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SegmentTab(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primary.copy(alpha = 0.18f) else colors.surfaceContainer,
        contentColor = if (selected) colors.primary else colors.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) colors.primary.copy(alpha = 0.55f) else colors.outlineVariant,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun InfoGrid(entries: List<Pair<String, String>>) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (label == "RUNTIME" || (label == "FAILED" && value != "0")) {
                                colors.primary
                            } else {
                                colors.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
