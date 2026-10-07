package com.shahgul.rawphotoconverter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.shahgul.rawphotoconverter.ui.RawConverterApp

class MainActivity : ComponentActivity() {
    private val processingRequest = mutableIntStateOf(0)

    companion object {
        const val SHOW_PROCESSING = "show_processing"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RawConverterApp(processingRequest.intValue)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShowProcessing()
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
