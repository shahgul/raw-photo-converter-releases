package com.shahgul.rawphotoconverter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.shahgul.rawphotoconverter.conversion.ConversionMode
import com.shahgul.rawphotoconverter.conversion.ConversionService
import com.shahgul.rawphotoconverter.conversion.ConversionStore
import com.shahgul.rawphotoconverter.conversion.OutputResolution
import com.shahgul.rawphotoconverter.conversion.RetryPolicy
import com.shahgul.rawphotoconverter.data.RawSourceRepository
import com.shahgul.rawphotoconverter.data.RawSourceSummary
import com.shahgul.rawphotoconverter.ui.theme.RawPhotoConverterTheme
import com.shahgul.rawphotoconverter.ui.theme.rememberThemePreference
import com.shahgul.rawphotoconverter.ui.theme.resolvesDark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RawConverterApp(processingRequest: Int = 0) {
    val context = LocalContext.current
    val (themePreference, setThemePreference) = rememberThemePreference()
    RawPhotoConverterTheme(darkTheme = themePreference.resolvesDark()) {
        val repository = remember(context) { RawSourceRepository(context) }
        val scope = rememberCoroutineScope()
        var source by remember { mutableStateOf<RawSourceSummary?>(null) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var outputUri by rememberSaveable { mutableStateOf<String?>(null) }
        var outputLabel by rememberSaveable { mutableStateOf<String?>(null) }
        var modeName by rememberSaveable { mutableStateOf(ConversionMode.DEVELOP_RAW.name) }
        var resolutionName by rememberSaveable { mutableStateOf(OutputResolution.STANDARD.name) }
        val mode = ConversionMode.valueOf(modeName)
        val resolution = OutputResolution.valueOf(resolutionName)
        var preserveMetadata by rememberSaveable { mutableStateOf(true) }
        LaunchedEffect(Unit) { ConversionStore.initialize(context.applicationContext) }
        val batch by ConversionStore.state.collectAsState()
        var screenName by rememberSaveable { mutableStateOf(AppScreen.Setup.name) }
        val screen = runCatching { AppScreen.valueOf(screenName) }.getOrDefault(AppScreen.Setup)
        fun go(next: AppScreen) { screenName = next.name }
        var lastSeenBatch by rememberSaveable { mutableStateOf(0L) }
        val settleMs = motionMillis(40).toLong()
        LaunchedEffect(batch.startedAtMs) {
            if (batch.running && lastSeenBatch != batch.startedAtMs) {
                go(AppScreen.Processing)
                lastSeenBatch = batch.startedAtMs
            }
        }
        LaunchedEffect(batch.running, batch.finishedAtMs, screen) {
            if (!batch.running && batch.finishedAtMs != null && screen == AppScreen.Processing) {
                delay(settleMs)
                go(AppScreen.Results)
            }
        }
        LaunchedEffect(processingRequest) {
            if (processingRequest <= 0) return@LaunchedEffect
            // Read the store directly so notification resume does not depend on a stale composed snapshot.
            val active = ConversionStore.state.value
            go(if (active.running) AppScreen.Processing else AppScreen.Results)
        }
        BackHandler(enabled = screen != AppScreen.Setup) { go(AppScreen.Setup) }

        fun openJpeg(uri: Uri) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "image/jpeg")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            } catch (failure: Exception) {
                error = "No app could open this JPEG: ${failure.message}"
            }
        }

        fun shareJpeg(uri: Uri) {
            try {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("image/jpeg")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(send, "Share JPEG"))
            } catch (failure: Exception) {
                error = "Unable to share this JPEG: ${failure.message}"
            }
        }

        fun loadSingle(uri: Uri) {
            repository.persistReadPermission(uri)
            loading = true
            source = null
            error = null
            scope.launch {
                try {
                    source = repository.inspectRaw(uri)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message ?: "Unable to inspect RAW."
                } finally {
                    loading = false
                }
            }
        }

        fun loadFolder(uri: Uri) {
            repository.persistReadPermission(uri)
            loading = true
            source = null
            error = null
            scope.launch {
                try {
                    source = repository.inspectFolder(uri)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message ?: "Unable to inspect folder."
                } finally {
                    loading = false
                }
            }
        }

        val rawPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(::loadSingle)
        }
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(::loadFolder)
        }
        val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    } catch (permission: SecurityException) {
                        android.util.Log.w("RawSource", "Output access is limited to this session", permission)
                    }
                    val folder = DocumentFile.fromTreeUri(context, uri)
                    check(folder?.isDirectory == true && folder.canWrite()) { "Select a writable output folder." }
                    outputUri = uri.toString()
                    outputLabel = folder.name ?: "Selected output folder"
                    error = null
                } catch (failure: Exception) {
                    error = failure.message ?: "Unable to open output folder."
                }
            }
        }

        fun startConversion() {
            val selected = source ?: return
            val output = outputUri ?: return
            try {
                val input = when (selected) {
                    is RawSourceSummary.Single -> selected.uri
                    is RawSourceSummary.Folder -> selected.uri
                }
                ConversionService.start(
                    context,
                    input,
                    selected.label,
                    selected is RawSourceSummary.Folder,
                    Uri.parse(output),
                    preserveMetadata,
                    mode,
                    resolution,
                )
                go(AppScreen.Processing)
                error = null
            } catch (failure: Exception) {
                error = failure.message ?: "Unable to start conversion."
            }
        }

        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            startConversion()
        }

        fun requestConvert() {
            if (batch.running) ConversionService.cancel(context)
            else if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else startConversion()
        }

        when (screen) {
            AppScreen.Processing -> {
                ProcessingScreen(
                    batch = batch,
                    error = error,
                    onBack = { go(AppScreen.Setup) },
                    onCancel = { ConversionService.cancel(context) },
                    onViewResults = { go(AppScreen.Results) },
                    themePreference = themePreference,
                    onThemeCycle = { setThemePreference(themePreference.next()) },
                )
                return@RawPhotoConverterTheme
            }
            AppScreen.Results -> {
                ResultsScreen(
                    batch = batch,
                    error = error,
                    onBack = { go(AppScreen.Setup) },
                    onConvertMore = { go(AppScreen.Setup) },
                    onOpenJpeg = ::openJpeg,
                    onShareJpeg = ::shareJpeg,
                    onRetryFailed = { sourceUri ->
                        if (!batch.running) {
                            try {
                                check(ConversionService.retryFailed(context, sourceUri)) {
                                    "No failed RAW files are available to retry."
                                }
                                error = null
                            } catch (failure: Exception) {
                                error = failure.message ?: "Unable to retry failed RAW files."
                            }
                        }
                    },
                    canRetry = !batch.running &&
                        !batch.settings?.outputUri.isNullOrBlank() &&
                        RetryPolicy.failed(batch.results).isNotEmpty(),
                    onViewProcessing = { go(AppScreen.Processing) },
                    themePreference = themePreference,
                    onThemeCycle = { setThemePreference(themePreference.next()) },
                )
                return@RawPhotoConverterTheme
            }
            AppScreen.Setup -> Unit
        }
        ConverterWorkspace(
            source = source,
            loading = loading,
            batch = batch,
            mode = mode,
            resolution = resolution,
            onMode = { modeName = it.name },
            onResolution = { resolutionName = it.name },
            outputLabel = outputLabel,
            preserveMetadata = preserveMetadata,
            error = error,
            onOpenRaw = { rawPicker.launch(arrayOf("*/*")) },
            onOpenFolder = { folderPicker.launch(null) },
            onOutput = { outputPicker.launch(outputUri?.let(Uri::parse)) },
            onMetadata = { preserveMetadata = it },
            onProcessing = { go(AppScreen.Processing) },
            onResults = { go(AppScreen.Results) },
            onConvert = { requestConvert() },
            themePreference = themePreference,
            onThemeCycle = { setThemePreference(themePreference.next()) },
        )
    }
}
