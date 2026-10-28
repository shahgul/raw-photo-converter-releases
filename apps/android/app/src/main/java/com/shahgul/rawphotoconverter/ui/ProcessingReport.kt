package com.shahgul.rawphotoconverter.ui
import com.shahgul.rawphotoconverter.conversion.*
import kotlin.math.exp
import kotlin.math.max
internal fun modeLabel(mode: ConversionMode) = if (mode == ConversionMode.CAMERA_LOOK) "Camera look" else "Develop RAW"
internal fun resolutionLabel(resolution: OutputResolution) = if (resolution == OutputResolution.ORIGINAL) "Original resolution" else "Standard (4000 px)"
internal fun formatElapsed(ms: Long): String = "%02d:%02d".format(ms.coerceAtLeast(0) / 60000, (ms.coerceAtLeast(0) / 1000) % 60)

internal fun completedFileProgress(completed: Int, total: Int): Float? = when {
    total > 1 -> (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    total == 1 && completed >= 1 -> 1f
    else -> null
}

private fun smoothstep(unit: Float): Float {
    val x = unit.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * Smooth displayed progress in 0..1.
 *
 * The bar eases from 0 toward 100 over [minimumSweepMs] when the job is fast.
 * It stays below 1 while the engine is still running, and it reaches 1 only at the
 * end of that ease — there is no pause after the bar is already full.
 * Multiple files pull the bar toward finished-file count; a single file glides on time
 * because one file has no honest midpoint.
 */
internal fun smoothProcessingProgress(
    elapsedMs: Long,
    finishedElapsedMs: Long?,
    completedFiles: Int,
    totalFiles: Int,
    minimumSweepMs: Long = 1_800L,
): Float {
    val elapsed = elapsedMs.coerceAtLeast(0)
    val sweep = minimumSweepMs.coerceAtLeast(1L)
    val ceiling = 0.90f

    fun whileRunning(at: Long): Float {
        val glide = smoothstep(at.toFloat() / sweep.toFloat()) * ceiling
        if (totalFiles <= 1) {
            if (at < sweep) return glide
            val extra = (at - sweep).toFloat()
            return (ceiling + (1f - exp(-extra / 2800.0).toFloat()) * 0.07f).coerceAtMost(0.97f)
        }
        val files = (completedFiles.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f) * ceiling
        return max(glide * 0.2f, files).coerceAtMost(if (at < sweep) ceiling else 0.97f)
    }

    val finishedAt = finishedElapsedMs ?: return whileRunning(elapsed)
    val doneAt = finishedAt.coerceAtLeast(0)
    val arriveAt = if (doneAt < sweep) sweep else doneAt + 320L
    if (elapsed >= arriveAt) return 1f
    if (elapsed <= doneAt) return whileRunning(elapsed)
    val start = whileRunning(doneAt)
    val span = (arriveAt - doneAt).coerceAtLeast(1L)
    val u = smoothstep((elapsed - doneAt).toFloat() / span.toFloat())
    return (start + (1f - start) * u).coerceIn(0f, 1f)
}

internal fun resultFacts(result: ConversionResult): List<Pair<String, String>> = buildList {
    add("Outcome" to when {
        result.error != null -> "Failed"
        result.skipped -> "Skipped"
        else -> "Saved"
    })
    if (result.width != null && result.height != null) add("Dimensions" to "${result.width} × ${result.height}")
    result.sizeBytes?.let { add("File size" to formatSize(it)) }
    add("Recipe" to if (result.cameraLook) "Camera look" else "Develop RAW")
    add("Format" to "${result.format.label} · ${result.colorGamut.label}")
    result.quality?.let { add("Quality" to "Q$it") }
    if (result.targetMet == false) add("Size target" to "Above the 3 MiB target")
    if (result.skipped) add("Note" to "Existing ${result.format.label} was left unchanged")
    result.error?.let { add("Error" to it) }
}

internal fun resultDescription(result: ConversionResult): String = when {
    result.error != null -> "Failed: ${result.error}"
    result.skipped -> buildString {
        append("Skipped: ${result.format.label} already exists")
        if (result.width != null && result.height != null) append(" · ${result.width} × ${result.height}")
        result.sizeBytes?.let { append(" · ${formatSize(it)}") }
    }
    else -> "Saved ${result.width} x ${result.height} / ${if (result.cameraLook) "Camera look" else "Developed RAW"} / ${result.format.label} / ${result.colorGamut.label} / ${result.quality?.let { "Q$it / " }.orEmpty()}${formatSize(result.sizeBytes)}" + if (result.targetMet == false) " / above size target" else ""
}
internal fun conversionReport(batch: BatchState) = buildString {
    appendLine("Raw Photo Converter - ${batch.phase}")
    appendLine("${batch.processedResults.size} / ${batch.total} processed")
    batch.settings?.let {
        appendLine("${modeLabel(it.mode)} / ${resolutionLabel(it.resolution)} / ${it.format.label} / ${it.colorGamut.label}")
        appendLine("Destination: ${it.destination}")
        appendLine("Metadata: ${if (it.metadata) "enabled (including available GPS)" else "disabled"}")
    }
    if (batch.startedAtMs > 0 && batch.finishedAtMs != null) appendLine("Elapsed: ${formatElapsed(batch.finishedAtMs - batch.startedAtMs)}")
    batch.message?.let(::appendLine)
    for (result in batch.processedResults) appendLine("${result.source}: ${resultDescription(result)}")
    appendLine("Recent events (up to 200):")
    for (event in batch.events) appendLine("${formatElapsed(event.elapsedMs)} ${event.message}")
}
