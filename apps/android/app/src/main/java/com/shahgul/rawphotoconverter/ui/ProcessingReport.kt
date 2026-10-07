package com.shahgul.rawphotoconverter.ui
import com.shahgul.rawphotoconverter.conversion.*
internal fun modeLabel(mode: ConversionMode) = if (mode == ConversionMode.CAMERA_LOOK) "Camera look" else "Develop RAW"
internal fun resolutionLabel(resolution: OutputResolution) = if (resolution == OutputResolution.ORIGINAL) "Original resolution" else "Standard (4000 px)"
internal fun formatElapsed(ms: Long): String = "%02d:%02d".format(ms.coerceAtLeast(0) / 60000, (ms.coerceAtLeast(0) / 1000) % 60)
internal fun resultDescription(result: ConversionResult): String = when {
    result.error != null -> "Failed: ${result.error}"
    result.skipped -> "Skipped: JPEG already exists"
    else -> "Saved ${result.width} x ${result.height} / ${if (result.cameraLook) "Camera look" else "Developed RAW"} / ${result.quality?.let { "Q$it / " }.orEmpty()}${formatSize(result.sizeBytes)}" + if (result.targetMet == false) " / above size target" else ""
}
internal fun conversionReport(batch: BatchState) = buildString {
    appendLine("Raw Photo Converter - ${batch.phase}")
    appendLine("${batch.results.size} / ${batch.total} processed")
    batch.settings?.let {
        appendLine("${modeLabel(it.mode)} / ${resolutionLabel(it.resolution)}")
        appendLine("Destination: ${it.destination}")
        appendLine("Metadata: ${if (it.metadata) "enabled (including available GPS)" else "disabled"}")
    }
    if (batch.startedAtMs > 0 && batch.finishedAtMs != null) appendLine("Elapsed: ${formatElapsed(batch.finishedAtMs - batch.startedAtMs)}")
    batch.message?.let(::appendLine)
    for (result in batch.results) appendLine("${result.source}: ${resultDescription(result)}")
    appendLine("Recent events (up to 200):")
    for (event in batch.events) appendLine("${formatElapsed(event.elapsedMs)} ${event.message}")
}
