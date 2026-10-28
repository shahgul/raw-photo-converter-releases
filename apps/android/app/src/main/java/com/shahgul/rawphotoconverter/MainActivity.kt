package com.shahgul.rawphotoconverter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import android.net.Uri
import com.shahgul.rawphotoconverter.data.SharedContentRequest
import com.shahgul.rawphotoconverter.data.incomingSharedContent
import com.shahgul.rawphotoconverter.ui.RawConverterApp

class MainActivity : ComponentActivity() {
    private val processingRequest = mutableIntStateOf(0)
    private val sharedContent = mutableStateOf<SharedContentRequest?>(null)

    companion object {
        const val SHOW_PROCESSING = "show_processing"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedContent.value = if (savedInstanceState == null) incomingSharedContent(intent)
        else savedInstanceState.getString("pendingShareId")?.let { id ->
            SharedContentRequest(id, savedInstanceState.getStringArrayList("pendingShareUris").orEmpty().map(Uri::parse),
                savedInstanceState.getString("pendingShareMessage"))
        }
        enableEdgeToEdge()
        setContent {
            RawConverterApp(processingRequest.intValue, sharedContent.value, ::finishSharedContent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingSharedContent(intent)?.let { sharedContent.value = it }
        consumeShowProcessing()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        sharedContent.value?.let {
            outState.putString("pendingShareId", it.id)
            outState.putStringArrayList("pendingShareUris", ArrayList(it.uris.map(Uri::toString)))
            outState.putString("pendingShareMessage", it.message)
        }
        super.onSaveInstanceState(outState)
    }

    private fun finishSharedContent(id: String) {
        if (sharedContent.value?.id != id) return
        sharedContent.value = null
        // Recreation must restore the queue, not import the launch payload again.
        intent.removeExtra(Intent.EXTRA_STREAM)
        intent.clipData = null
        intent.data = null
        intent.type = null
        intent.action = Intent.ACTION_MAIN
    }

    override fun onResume() {
        super.onResume()
        // Cold start / recreate: notification extras arrive on the launch intent.
        consumeShowProcessing()
    }

    /** Mirrors notification SINGLE_TOP delivery for instrumented tests. */
    fun deliverShowProcessingRequest() {
        intent.putExtra(SHOW_PROCESSING, true)
        consumeShowProcessing()
    }

    private fun consumeShowProcessing() {
        if (!intent.getBooleanExtra(SHOW_PROCESSING, false)) return
        intent.removeExtra(SHOW_PROCESSING)
        processingRequest.intValue++
    }
}
