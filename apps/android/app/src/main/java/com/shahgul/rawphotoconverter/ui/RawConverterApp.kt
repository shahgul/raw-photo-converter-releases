package com.shahgul.rawphotoconverter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.shahgul.rawphotoconverter.conversion.ColorGamut
import com.shahgul.rawphotoconverter.conversion.ConversionMode
import com.shahgul.rawphotoconverter.conversion.ConversionPolicy
import com.shahgul.rawphotoconverter.conversion.ConversionService
import com.shahgul.rawphotoconverter.conversion.ConversionStore
import com.shahgul.rawphotoconverter.conversion.OutputFormat
import com.shahgul.rawphotoconverter.conversion.OutputResolution
import com.shahgul.rawphotoconverter.conversion.RetryPolicy
import com.shahgul.rawphotoconverter.conversion.RawInput
import com.shahgul.rawphotoconverter.data.RawSourceRepository
import com.shahgul.rawphotoconverter.data.RawPreviewState
import com.shahgul.rawphotoconverter.data.RawSourceSummary
import com.shahgul.rawphotoconverter.data.SelectedRaw
import com.shahgul.rawphotoconverter.data.SharedContentRequest
import com.shahgul.rawphotoconverter.data.jpegShareIntent
import com.shahgul.rawphotoconverter.data.savedJpegUris
import com.shahgul.rawphotoconverter.data.ExportOptions
import com.shahgul.rawphotoconverter.data.JpegExportRepository
import com.shahgul.rawphotoconverter.ui.theme.RawPhotoConverterTheme
import com.shahgul.rawphotoconverter.ui.theme.rememberThemePreference
import com.shahgul.rawphotoconverter.ui.theme.resolvesDark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RawConverterApp(processingRequest: Int = 0, sharedContent: SharedContentRequest? = null,
    onSharedContentHandled: (String) -> Unit = {}) {
    val context = LocalContext.current
    val (themePreference, setThemePreference) = rememberThemePreference()
    RawPhotoConverterTheme(darkTheme = themePreference.resolvesDark()) {
        val repository = remember(context) { RawSourceRepository(context) }
        val scope = rememberCoroutineScope()
        var source by remember { mutableStateOf<RawSourceSummary?>(null) }
        var sourceFolderUri by rememberSaveable { mutableStateOf<String?>(null) }
        var selectedPhotoUris by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
        var previewJob by remember { mutableStateOf<Job?>(null) }
        var sourceRequestId by remember { mutableStateOf(0) }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var sharing by remember { mutableStateOf(false) }
        var outputUri by rememberSaveable { mutableStateOf<String?>(null) }
        var outputLabel by rememberSaveable { mutableStateOf<String?>(null) }
        var modeName by rememberSaveable { mutableStateOf(ConversionMode.DEVELOP_RAW.name) }
        var resolutionName by rememberSaveable { mutableStateOf(OutputResolution.STANDARD.name) }
        var formatName by rememberSaveable { mutableStateOf(OutputFormat.JPEG.name) }
        var colorGamutName by rememberSaveable { mutableStateOf(ColorGamut.SRGB.name) }
        var completionFeedbackEnabled by rememberSaveable { mutableStateOf(true) }
        val mode = ConversionMode.valueOf(modeName)
        val resolution = OutputResolution.valueOf(resolutionName)
        val format = runCatching { OutputFormat.valueOf(formatName) }.getOrDefault(OutputFormat.JPEG)
        val colorGamut = runCatching { ColorGamut.valueOf(colorGamutName) }.getOrDefault(ColorGamut.SRGB)
        var preserveMetadata by rememberSaveable { mutableStateOf(true) }
        var removeExportExif by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) { ConversionStore.initialize(context.applicationContext) }
        val batch by ConversionStore.state.collectAsState()
        var screenName by rememberSaveable { mutableStateOf(AppScreen.Setup.name) }
        val screen = runCatching { AppScreen.valueOf(screenName) }.getOrDefault(AppScreen.Setup)
        fun go(next: AppScreen) { screenName = next.name }
        var lastSeenBatch by rememberSaveable { mutableStateOf(0L) }
        var processingEnteredAt by remember { mutableLongStateOf(0L) }
        var feedbackArmedAt by remember { mutableLongStateOf(0L) }
        var feedbackPlayedFor by remember { mutableLongStateOf(0L) }
        fun showProcessing() {
            val now = SystemClock.elapsedRealtime()
            processingEnteredAt = now
            feedbackArmedAt = now
            go(AppScreen.Processing)
        }
        LaunchedEffect(batch.startedAtMs) {
            if (batch.running && lastSeenBatch != batch.startedAtMs) {
                showProcessing()
                lastSeenBatch = batch.startedAtMs
            }
        }
        LaunchedEffect(batch.finishedAtMs, batch.running, feedbackArmedAt, completionFeedbackEnabled) {
            val armed = feedbackArmedAt
            val finished = batch.finishedAtMs
            if (!completionFeedbackEnabled || armed == 0L || batch.running || finished == null || finished < armed) return@LaunchedEffect
            if (feedbackPlayedFor == finished) return@LaunchedEffect
            feedbackPlayedFor = finished
            CompletionFeedback.trigger(
                context,
                hasFailures = batch.results.any { it.error != null },
            )
        }
        fun openResultsWhenSweepFinishes() {
            if (screenName == AppScreen.Processing.name && !ConversionStore.state.value.running) {
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
                        .setDataAndType(uri, format.mimeType)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            } catch (failure: Exception) {
                error = "No app could open this photo: ${failure.message}"
            }
        }

        fun shareJpegs(uris: List<Uri>) {
            if (sharing) return
            sharing = true
            val options = ExportOptions(removeExif = removeExportExif)
            scope.launch {
                try {
                    val readable = withContext(Dispatchers.IO) {
                        uris.distinct().filter { uri ->
                            try {
                                uri.scheme == "content" && context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                android.util.Log.w("RawShare", "Saved JPEG is no longer readable", failure)
                                false
                            }
                        }
                    }
                    check(readable.isNotEmpty()) { "The saved JPEGs can't be read. Check the output folder or convert them again." }
                    error = if (readable.size < uris.distinct().size) "${uris.distinct().size - readable.size} JPEG(s) couldn't be read and weren't shared." else null
                    val exports = withContext(Dispatchers.IO) { JpegExportRepository(context).prepare(readable, options) }
                    context.startActivity(Intent.createChooser(jpegShareIntent(exports), "Share converted JPEGs"))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message ?: "Unable to share saved JPEGs."
                } finally { sharing = false }
            }
        }

        fun savedInput() = SavedInputSelection(sourceFolderUri, selectedPhotoUris)

        fun persistPhotos(uris: List<String>) {
            val next = savedInput().withPhotos(uris)
            sourceFolderUri = next.sourceFolderUri
            selectedPhotoUris = next.photoUris
        }

        fun loadSingle(uri: Uri) {
            val requestId = ++sourceRequestId
            previewJob?.cancel()
            previewJob = null
            repository.persistReadPermission(uri)
            persistPhotos(listOf(uri.toString()))
            loading = true
            source = null
            error = null
            scope.launch {
                try {
                    val inspected = repository.inspectRaw(uri)
                    if (requestId != sourceRequestId) return@launch
                    persistPhotos(listOf(uri.toString()))
                    source = inspected.copy(preview = RawPreviewState.Loading)
                    previewJob = scope.launch {
                        val preview = try {
                            repository.loadPreview(uri, inspected.sizeBytes)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            RawPreviewState.Unavailable("The camera preview couldn't be read. Develop RAW is still available.")
                        }
                        val current = source as? RawSourceSummary.Single
                        if (requestId == sourceRequestId && current != null && current.uri == uri) {
                            source = current.copy(preview = preview)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (requestId == sourceRequestId) error = failure.message ?: "Unable to inspect RAW."
                } finally {
                    if (requestId == sourceRequestId) loading = false
                }
            }
        }

        fun loadFolder(uri: Uri) {
            val requestId = ++sourceRequestId
            previewJob?.cancel()
            previewJob = null
            repository.persistReadPermission(uri)
            loading = true
            source = null
            error = null
            scope.launch {
                try {
                    val inspected = repository.inspectFolder(uri)
                    if (requestId == sourceRequestId) {
                        val next = savedInput().withSourceFolder(uri.toString())
                        sourceFolderUri = next.sourceFolderUri
                        selectedPhotoUris = next.photoUris
                        source = inspected
                        if (inspected.rawCount == 0) error = "This folder has no supported RAW files. Choose ARW, CR2, or CR3 photos."
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (requestId == sourceRequestId) error = failure.message ?: "Unable to inspect folder."
                } finally {
                    if (requestId == sourceRequestId) loading = false
                }
            }
        }

        fun loadPhotos(uris: List<Uri>, shared: Boolean = false, notice: String? = null,
            onFinished: (() -> Unit)? = null) {
            if (uris.isEmpty() || batch.running) return
            val previous = when (val selected = source) {
                is RawSourceSummary.Single -> listOf(SelectedRaw(selected.uri, selected.label, selected.sizeBytes))
                is RawSourceSummary.Photos -> selected.photos
                else -> emptyList()
            }
            val additions = uris.distinct().filter { uri -> previous.none { it.uri == uri } }
            if (additions.isEmpty()) {
                error = notice
                onFinished?.invoke()
                return
            }
            if (!shared && previous.isEmpty() && additions.size == 1) {
                loadSingle(additions.single())
                return
            }
            val requestId = ++sourceRequestId
            previewJob?.cancel()
            previewJob = null
            additions.forEach(repository::persistReadPermission)
            loading = true
            error = null
            scope.launch {
                try {
                    val inspected = if (shared) repository.inspectSharedContent(additions) else repository.inspectSelection(additions)
                    if (requestId != sourceRequestId) return@launch
                    val photos = (previous + inspected.photos).distinctBy { it.uri }
                    if (photos.isNotEmpty()) {
                        persistPhotos(photos.map { it.uri.toString() })
                        if (photos.size == 1) loadSingle(photos.single().uri)
                        else source = RawSourceSummary.Photos(photos)
                    }
                    error = listOfNotNull(notice, if (inspected.failures.isNotEmpty())
                        "${inspected.failures.size} item(s) weren't added. " + inspected.failures.distinct().take(3).joinToString(" ")
                        else if (shared && photos.isEmpty()) "No supported RAW files were found. Choose ARW, CR2, or CR3 photos." else null)
                        .joinToString(" ").ifBlank { null }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (requestId == sourceRequestId) error = failure.message ?: "Unable to read selected photos."
                } finally {
                    if (requestId == sourceRequestId) loading = false
                    onFinished?.invoke()
                }
            }
        }

        fun removePhoto(uri: Uri) {
            if (loading || batch.running) return
            when (val selected = source) {
                is RawSourceSummary.Single -> {
                    if (selected.uri == uri) {
                        ++sourceRequestId
                        previewJob?.cancel()
                        previewJob = null
                        source = null
                        sourceFolderUri = null
                        selectedPhotoUris = emptyList()
                    }
                }
                is RawSourceSummary.Photos -> {
                    val remaining = selected.photos.filterNot { it.uri == uri }
                    persistPhotos(remaining.map { it.uri.toString() })
                    when (remaining.size) {
                        0 -> source = null
                        1 -> loadSingle(remaining.single().uri)
                        else -> source = RawSourceSummary.Photos(remaining)
                    }
                }
                else -> Unit
            }
            error = null
        }

        fun restoreInputIfNeeded() {
            if (batch.running || loading || sharedContent != null || source != null) return
            val photos = selectedPhotoUris
            val folder = savedInput().restorableSourceFolder(outputUri)
            when {
                photos.isNotEmpty() -> loadPhotos(photos.map(Uri::parse))
                !folder.isNullOrBlank() -> loadFolder(Uri.parse(folder))
            }
        }

        val openDocuments = remember { ActivityResultContracts.OpenMultipleDocuments() }
        val rawPicker = rememberLauncherForActivityResult(openDocuments) { uris ->
            loadPhotos(uris)
        }
        LaunchedEffect(sharedContent?.id) {
            val request = sharedContent ?: return@LaunchedEffect
            when {
                batch.running || loading -> {
                    error = "Finish reading or converting the current photos, then share these files again."
                    onSharedContentHandled(request.id)
                }
                request.uris.isEmpty() -> {
                    go(AppScreen.Setup)
                    error = request.message ?: "No RAW files were shared."
                    onSharedContentHandled(request.id)
                }
                else -> {
                    go(AppScreen.Setup)
                    loadPhotos(request.uris, shared = true, notice = request.message,
                        onFinished = { onSharedContentHandled(request.id) })
                }
            }
        }
        fun applyOutputFolder(uri: Uri) {
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
                restoreInputIfNeeded()
            } catch (failure: Exception) {
                error = failure.message ?: "Unable to open output folder."
            }
        }
        // One remembered OpenDocumentTree instance, two launchers (unique registry keys).
        // A single launcher plus a saveable "role" flag still mis-routed on some devices
        // when the activity recreated during the system folder UI.
        val openTree = remember { ActivityResultContracts.OpenDocumentTree() }
        val sourceFolderPicker = rememberLauncherForActivityResult(openTree) { uri ->
            if (uri != null) loadFolder(uri)
        }
        val outputFolderPicker = rememberLauncherForActivityResult(openTree) { uri ->
            if (uri != null) applyOutputFolder(uri)
        }
        LaunchedEffect(batch.running, selectedPhotoUris, sourceFolderUri, outputUri) {
            restoreInputIfNeeded()
        }

        fun startConversion() {
            val selected = source ?: return
            val output = outputUri ?: return
            try {
                if (selected is RawSourceSummary.Photos) {
                    ConversionService.startPhotos(
                        context,
                        selected.photos.map { RawInput(it.uri, it.label) },
                        Uri.parse(output), preserveMetadata, mode, resolution,
                        format, colorGamut,
                    )
                } else {
                    val input = when (selected) {
                        is RawSourceSummary.Single -> selected.uri
                        is RawSourceSummary.Folder -> selected.uri
                        is RawSourceSummary.Photos -> error("Use the selected-photo batch path.")
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
                        format,
                        colorGamut,
                    )
                }
                previewJob?.cancel()
                previewJob = null
                val single = source as? RawSourceSummary.Single
                if (single != null && single.preview == RawPreviewState.Loading) {
                    source = single.copy(
                        preview = RawPreviewState.Unavailable(
                            "Preview loading stopped while conversion runs.",
                        ),
                    )
                }
                showProcessing()
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

        // Subtle cross-slide between Setup, Processing and Results. Durations honour
        // the system animator scale, so reduced-motion stays instant.
        val screenIn = motionMillis(240)
        val screenOut = motionMillis(180)
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                (fadeIn(tween(screenIn)) + slideInVertically(tween(screenIn)) { it / 10 }) togetherWith
                    (fadeOut(tween(screenOut)) + slideOutVertically(tween(screenOut)) { it / 10 }) using
                    SizeTransform(clip = false)
            },
            label = "app-screen",
        ) { current ->
            when (current) {
            AppScreen.Processing -> {
                ProcessingScreen(
                    batch = batch,
                    error = error,
                    sweepStartedAtMs = processingEnteredAt,
                    onBack = { go(AppScreen.Setup) },
                    onCancel = { ConversionService.cancel(context) },
                    onViewResults = { go(AppScreen.Results) },
                    onSweepFinished = ::openResultsWhenSweepFinishes,
                    themePreference = themePreference,
                    onThemeCycle = { setThemePreference(themePreference.next()) },
                )
            }
            AppScreen.Results -> {
                ResultsScreen(
                    batch = batch,
                    error = error,
                    onBack = { go(AppScreen.Setup) },
                    onConvertMore = { go(AppScreen.Setup) },
                    onOpenJpeg = ::openJpeg,
                    onShareJpeg = { shareJpegs(listOf(it)) },
                    onRetryFailed = { sourceUri ->
                        if (!batch.running) {
                            try {
                                check(ConversionService.retryFailed(context, sourceUri)) {
                                    "No failed RAW files are available to retry."
                                }
                                showProcessing()
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
                    onShareAll = { shareJpegs(savedJpegUris(batch.results)) },
                    sharing = sharing,
                    exportOptions = ExportOptions(removeExif = removeExportExif),
                    onExportOptionsChange = { removeExportExif = it.removeExif },
                )
            }
            AppScreen.Setup -> ConverterWorkspace(
                source = source,
                loading = loading,
                batch = batch,
                mode = mode,
                resolution = resolution,
                onMode = { modeName = it.name },
                onResolution = { resolutionName = it.name },
                format = format,
                onFormat = { formatName = it.name },
                colorGamut = colorGamut,
                onColorGamut = { colorGamutName = it.name },
                completionFeedback = completionFeedbackEnabled,
                onCompletionFeedback = { completionFeedbackEnabled = it },
                outputLabel = outputLabel,
                preserveMetadata = preserveMetadata,
                error = error,
                onOpenRaw = { rawPicker.launch(ConversionPolicy.openDocumentMimeTypes()) },
                onOpenFolder = {
                    try {
                        sourceFolderPicker.launch(sourceFolderUri?.let(Uri::parse))
                    } catch (failure: Exception) {
                        error = failure.message ?: "Unable to open the folder picker."
                    }
                },
                onOutput = {
                    try {
                        outputFolderPicker.launch(outputUri?.let(Uri::parse))
                    } catch (failure: Exception) {
                        error = failure.message ?: "Unable to open the output folder picker."
                    }
                },
                onMetadata = { preserveMetadata = it },
                onProcessing = { go(AppScreen.Processing) },
                onResults = { go(AppScreen.Results) },
                onConvert = { requestConvert() },
                themePreference = themePreference,
                onThemeCycle = { setThemePreference(themePreference.next()) },
                onRemovePhoto = ::removePhoto,
            )
        }
    }
}
}
