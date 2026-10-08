package com.shahgul.rawphotoconverter.conversion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.shahgul.rawphotoconverter.MainActivity
import com.shahgul.rawphotoconverter.data.RawSourceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CancellationException

data class BatchSettings(
    val mode: ConversionMode,
    val resolution: OutputResolution,
    val metadata: Boolean,
    val destination: String,
    val outputUri: String? = null,
)
data class BatchEvent(val elapsedMs: Long, val message: String)

data class BatchState(
    val running: Boolean = false,
    val current: String? = null,
    val phase: String = "Ready",
    val total: Int = 0,
    val results: List<ConversionResult> = emptyList(),
    val retrying: Boolean = false,
    val attemptResults: List<ConversionResult> = emptyList(),
    val message: String? = null,
    val startedAtMs: Long = 0,
    val finishedAtMs: Long? = null,
    val settings: BatchSettings? = null,
    val events: List<BatchEvent> = emptyList(),
    /** Stable source name for the processing header (file or folder label). */
    val sourceLabel: String? = null,
) {
    val processedResults: List<ConversionResult>
        get() = if (retrying) attemptResults else results
}

internal data class RetryRequest(
    val inputs: List<RawInput>,
    val previous: BatchState,
)

object ConversionStore {
    internal val gate = BatchGate()
    private val mutable = MutableStateFlow(BatchState())
    val state = mutable.asStateFlow()
    private var initialized = false
    private var pendingRetry: RetryRequest? = null

    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        if (context.getSharedPreferences("conversion", Context.MODE_PRIVATE).getBoolean("active", false)) {
            mutable.value = BatchState(phase = "Interrupted", message = "The previous conversion was interrupted. Re-select the source and output folder to retry; existing JPEGs will be skipped.")
        }
    }

    @Synchronized internal fun publish(state: BatchState, logResultChanges: Boolean = true) {
        val previous = mutable.value
        val fresh = state.running && !previous.running
        var events = if (fresh) emptyList() else previous.events
        val descriptions = mutableListOf<String>()
        val previousProcessed = previous.processedResults
        val currentProcessed = state.processedResults
        if (fresh || previous.phase != state.phase || previous.current != state.current)
            descriptions += listOfNotNull(state.current, state.phase).joinToString(" - ")
        if (logResultChanges && !fresh && currentProcessed.size > previousProcessed.size) currentProcessed.drop(previousProcessed.size).forEach {
            descriptions += "${it.source}: " + when {
                it.error != null -> "Failed - ${it.error}"
                it.skipped -> "Skipped - JPEG exists"
                else -> "Saved ${it.width} x ${it.height}"
            }
        }
        if (state.message != null && state.message != previous.message) descriptions += state.message
        val now = SystemClock.elapsedRealtime()
        events = (events + descriptions.map { BatchEvent(if (state.startedAtMs > 0) (now - state.startedAtMs).coerceAtLeast(0) else 0, it) }).takeLast(200)
        mutable.value = state.copy(events = events,
            finishedAtMs = if (!state.running && state.startedAtMs > 0) state.finishedAtMs ?: now else state.finishedAtMs)
    }

    @Synchronized internal fun createRetryRequest(sourceUri: String? = null): RetryRequest? {
        val previous = mutable.value
        if (previous.running || pendingRetry != null) return null
        val settings = previous.settings ?: return null
        if (settings.outputUri.isNullOrBlank()) return null
        val failed = RetryPolicy.failed(previous.results, sourceUri)
        if (failed.isEmpty()) return null
        return RetryRequest(
            inputs = failed.map { result -> RawInput(Uri.parse(requireNotNull(result.sourceUri)), result.source) },
            previous = previous,
        ).also { pendingRetry = it }
    }

    @Synchronized internal fun takeRetryRequest(): RetryRequest? = pendingRetry.also { pendingRetry = null }

    @Synchronized internal fun discardRetryRequest(request: RetryRequest) {
        if (pendingRetry === request) pendingRetry = null
    }
}

class ConversionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cancellation: ConversionCancellation? = null
    @Volatile private var active = false
    @Volatile private var timeoutMessage: String? = null
    @Volatile private var latestStartId: Int = 0
    private var lease: Any? = null
    private val manager by lazy { getSystemService(NotificationManager::class.java) }

    override fun onCreate() {
        super.onCreate()
        Log.i("RawConversion", "Conversion service created")
        ConversionStore.initialize(this)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "RAW conversion", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        Log.i("RawConversion", "Conversion command action=${intent?.action}, startId=$startId, active=$active")
        if (intent?.action == CANCEL) {
            cancellation?.cancelled = true
            if (active) update(ConversionStore.state.value.copy(phase = "Cancelling"))
            else stopSelf()
            return START_NOT_STICKY
        }
        if (active || intent == null) {
            if (intent?.action == RETRY_FAILED) {
                ConversionStore.takeRetryRequest()?.let { request ->
                    ConversionStore.publish(
                        request.previous.copy(message = "The previous conversion is still finishing. Try again shortly."),
                        logResultChanges = false,
                    )
                }
            }
            return START_NOT_STICKY
        }
        val retryRequest = if (intent.action == RETRY_FAILED) ConversionStore.takeRetryRequest() else null
        if (intent.action == RETRY_FAILED && retryRequest == null) {
            Log.w("RawConversion", "Retry request was missing or already consumed")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val ownership = ConversionStore.gate.tryAcquire()
        if (ownership == null) {
            Log.w("RawConversion", "Conversion is still owned by a worker; rejecting a second start")
            retryRequest?.let {
                ConversionStore.publish(
                    it.previous.copy(message = "The previous conversion is still cleaning up. Try again shortly."),
                    logResultChanges = false,
                )
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        lease = ownership
        timeoutMessage = null
        val token = ConversionCancellation()
        cancellation = token
        active = true
        val initial = if (retryRequest == null) {
            BatchState(
                running = true,
                phase = "Reading source",
                startedAtMs = SystemClock.elapsedRealtime(),
                sourceLabel = intent.getStringExtra(LABEL),
            )
        } else {
            BatchState(
                running = true,
                phase = if (retryRequest.inputs.size == 1) "Retrying failed file" else "Retrying failed files",
                total = retryRequest.inputs.size,
                results = retryRequest.previous.results,
                retrying = true,
                startedAtMs = SystemClock.elapsedRealtime(),
                settings = retryRequest.previous.settings,
                sourceLabel = retryRequest.previous.sourceLabel,
            )
        }
        ConversionStore.publish(initial)
        val type = when {
            Build.VERSION.SDK_INT >= 35 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }
        try {
            // ServiceCompat filters types through its API 34 allowlist, which
            // drops Android 15's mediaProcessing bit and passes forbidden NONE.
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification(initial), type)
            else startForeground(NOTIFICATION, notification(initial))
            Log.i("RawConversion", "Foreground conversion started with type=$type")
        } catch (error: Exception) {
            Log.e("RawConversion", "Unable to start foreground conversion", error)
            val failure = "Unable to start visible conversion: ${error.message}"
            val failedState = retryRequest?.previous?.copy(phase = "Failed", message = failure)
                ?: initial.copy(running = false, phase = "Failed", message = failure)
            ConversionStore.publish(failedState, logResultChanges = retryRequest == null)
            active = false
            ConversionStore.gate.release(ownership)
            stopSelf()
            return START_NOT_STICKY
        }
        val preferences = getSharedPreferences("conversion", MODE_PRIVATE)
        preferences.edit().putBoolean("active", true).commit()
        // Ensure the cleanup/finally block is entered even if destruction races
        // the IO dispatcher before this worker starts.
        scope.launch(start = CoroutineStart.ATOMIC) {
            val engine = RawConversionEngine(applicationContext)
            var state = initial
            try {
                engine.cleanInterruptedTemps()
                val settings = retryRequest?.previous?.settings
                val outputUri = Uri.parse(
                    settings?.outputUri ?: requireNotNull(intent.getStringExtra(OUTPUT)),
                )
                val folder = DocumentFile.fromTreeUri(this@ConversionService, outputUri)
                    ?: error("The output folder cannot be opened. Select it again.")
                val repository = RawSourceRepository(applicationContext)
                val inputs = if (retryRequest != null) {
                    retryRequest.inputs
                } else {
                    val sourceUri = Uri.parse(requireNotNull(intent.getStringExtra(SOURCE)))
                    if (intent.getBooleanExtra(FOLDER, false)) {
                        repository.listFolderRaws(sourceUri, token)
                    } else {
                        listOf(RawInput(sourceUri, requireNotNull(intent.getStringExtra(LABEL))))
                    }
                }
                token.check()
                check(inputs.isNotEmpty()) { "No supported RAW files were found in the selected folder." }
                val batchSettings = settings ?: run {
                    val resolution = OutputResolution.valueOf(intent.getStringExtra(RESOLUTION) ?: OutputResolution.STANDARD.name)
                    BatchSettings(
                        mode = ConversionMode.valueOf(intent.getStringExtra(MODE) ?: ConversionMode.DEVELOP_RAW.name),
                        resolution = resolution,
                        metadata = intent.getBooleanExtra(METADATA, true),
                        destination = folder.name ?: "Selected output folder",
                        outputUri = outputUri.toString(),
                    )
                }
                state = state.copy(total = inputs.size, settings = batchSettings)
                update(state)
                val recipe = ConversionRecipe(
                    preserveMetadata = batchSettings.metadata,
                    mode = batchSettings.mode,
                    maxLongEdge = batchSettings.resolution.maxEdge,
                )
                for (input in inputs) {
                    token.check()
                    state = state.copy(current = input.label)
                    update(state)
                    val result = try {
                        engine.convert(input, folder, recipe, token) { phase ->
                            state = state.copy(phase = phase)
                            update(state)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.e("RawConversion", "Conversion failed for ${input.label}", error)
                        ConversionResult(input.label, error = error.message ?: "RAW conversion failed.", sourceUri = input.uri.toString())
                    } catch (error: OutOfMemoryError) {
                        Log.e("RawConversion", "Insufficient memory for ${input.label}", error)
                        throw IllegalStateException("Not enough memory to develop ${input.label}. Retry a smaller RAW on this device.", error)
                    }
                    state = if (retryRequest != null) {
                        state.copy(attemptResults = state.attemptResults + result)
                    } else {
                        state.copy(results = state.results + result)
                    }
                    update(state)
                }
                val failed = state.processedResults.count { it.error != null }
                state = state.copy(running = false, current = null, phase = if (failed == 0) "Finished" else "Finished with errors")
            } catch (error: CancellationException) {
                state = state.copy(running = false, phase = "Cancelled", message = "Completed JPEGs are kept. The unfinished file was not saved.")
            } catch (error: Exception) {
                Log.e("RawConversion", "Batch failed", error)
                state = state.copy(running = false, phase = "Failed", message = error.message ?: "Conversion failed.")
            } finally {
                preferences.edit().putBoolean("active", false).commit()
                timeoutMessage?.let { state = state.copy(running = false, phase = "Interrupted", message = it) }
                val finalState = retryRequest?.let { request ->
                    val merged = RetryPolicy.merge(request.previous.results, state.attemptResults)
                    val phase = when (state.phase) {
                        "Cancelled", "Failed", "Interrupted", "Stopping after timeout" -> state.phase
                        else -> if (merged.any { it.error != null }) "Finished with errors" else "Finished"
                    }
                    state.copy(
                        results = merged,
                        retrying = false,
                        attemptResults = emptyList(),
                        total = request.previous.total,
                        settings = request.previous.settings,
                        sourceLabel = request.previous.sourceLabel,
                        phase = phase,
                    )
                } ?: state
                if (ConversionStore.gate.isOwner(ownership)) {
                    ConversionStore.publish(finalState, logResultChanges = retryRequest == null)
                }
                active = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                // Cancel is another start command with a newer ID. Stopping
                // only the original ID would leave this service alive forever.
                stopSelf(latestStartId)
                // Keep ownership until all synchronous JNI/provider work and its
                // finally cleanup are done, even after Android stops the service.
                ConversionStore.gate.release(ownership)
            }
        }
        return START_NOT_STICKY // Never silently restart or duplicate a batch after process death.
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        val ownership = lease ?: return
        if (!active || !ConversionStore.gate.isOwner(ownership)) return
        cancellation?.cancelled = true
        timeoutMessage = "Android's processing time limit was reached. Completed JPEGs are kept; retry the remaining files after cleanup."
        ConversionStore.publish(ConversionStore.state.value.copy(phase = "Stopping after timeout", message = timeoutMessage))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(latestStartId)
    }

    override fun onDestroy() {
        cancellation?.cancelled = true
        scope.cancel()
        super.onDestroy()
    }

    private fun update(state: BatchState) {
        val ownership = lease ?: return
        if (!ConversionStore.gate.isOwner(ownership) || timeoutMessage != null) return
        ConversionStore.publish(state)
        manager.notify(NOTIFICATION, notification(state))
    }

    private fun notification(state: BatchState): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra(MainActivity.SHOW_PROCESSING, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(this, 1, Intent(this, ConversionService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("Raw Photo Converter · ${state.processedResults.size}/${state.total}")
            .setContentText(listOfNotNull(state.current, state.phase).joinToString(" · "))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancel)
            .build()
    }

    companion object {
        private const val CHANNEL = "raw-conversion"
        private const val NOTIFICATION = 101
        private const val CANCEL = "com.shahgul.rawphotoconverter.CANCEL"
        private const val RETRY_FAILED = "com.shahgul.rawphotoconverter.RETRY_FAILED"
        private const val SOURCE = "source"
        private const val OUTPUT = "output"
        private const val LABEL = "label"
        private const val FOLDER = "folder"
        private const val MODE = "mode"
        private const val RESOLUTION = "resolution"
        private const val METADATA = "metadata"

        fun start(context: Context, source: Uri, label: String, isFolder: Boolean, output: Uri, metadata: Boolean, mode: ConversionMode = ConversionMode.DEVELOP_RAW, resolution: OutputResolution = OutputResolution.STANDARD) {
            ContextCompat.startForegroundService(context, Intent(context, ConversionService::class.java)
                .putExtra(SOURCE, source.toString()).putExtra(LABEL, label).putExtra(FOLDER, isFolder)
                .putExtra(OUTPUT, output.toString()).putExtra(METADATA, metadata)
                .putExtra(MODE, mode.name).putExtra(RESOLUTION, resolution.name))
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, ConversionService::class.java).setAction(CANCEL))
        }

        fun retryFailed(context: Context, sourceUri: String? = null): Boolean {
            val request = ConversionStore.createRetryRequest(sourceUri) ?: return false
            return try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ConversionService::class.java).setAction(RETRY_FAILED),
                )
                true
            } catch (error: Exception) {
                ConversionStore.discardRetryRequest(request)
                throw error
            }
        }
    }
}
