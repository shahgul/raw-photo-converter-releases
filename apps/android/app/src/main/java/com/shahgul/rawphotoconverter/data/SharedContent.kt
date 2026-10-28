// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.data

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.IntentCompat
import com.shahgul.rawphotoconverter.conversion.ConversionResult
import java.util.UUID

data class SharedContentRequest(val id: String, val uris: List<Uri>, val message: String? = null)

/** Extract data only; never forward a supplied Intent, component, or nested extra. */
fun incomingSharedContent(intent: Intent): SharedContentRequest? {
    if (intent.action !in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE) &&
        !(intent.action == Intent.ACTION_VIEW && intent.type == "vnd.android.document/directory")) return null
    return try {
        val candidates = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(candidates::add)
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(candidates::addAll)
        }
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(candidates::add)
        }
        if (candidates.isEmpty()) intent.data?.let(candidates::add)
        val distinct = candidates.distinct()
        val accepted = distinct.filter { it.scheme == "content" && !it.authority.isNullOrBlank() }
        val rejected = distinct.size - accepted.size
        val mimeHint = intent.type?.lowercase(java.util.Locale.ROOT).orEmpty()
        val looksLikeRegularPhoto = mimeHint.startsWith("image/") &&
            !mimeHint.contains("arw") && !mimeHint.contains("cr2") && !mimeHint.contains("cr3") &&
            mimeHint != "application/octet-stream"
        val message = when {
            accepted.isEmpty() -> when {
                looksLikeRegularPhoto ->
                    "That share looks like a regular photo (JPEG/PNG/HEIC), not a RAW file. Share Sony ARW or Canon CR2/CR3, or pick RAWs with Add photos."
                else -> "No readable content files were shared. Share RAW files, or use Folder to choose a directory."
            }
            rejected > 0 -> "$rejected shared item(s) use unsupported links or file paths and weren't added. Use the Folder picker for directories without shared access."
            looksLikeRegularPhoto && accepted.isNotEmpty() ->
                "Check that shared files are Sony ARW or Canon CR2/CR3 — regular JPEGs cannot be developed here."
            else -> null
        }
        SharedContentRequest(UUID.randomUUID().toString(), accepted, message)
    } catch (failure: RuntimeException) {
        Log.w("RawShare", "Invalid incoming share payload", failure)
        SharedContentRequest(UUID.randomUUID().toString(), emptyList(), "The shared files couldn't be read. Select them using Add photos or Folder.")
    }
}

internal fun savedJpegUris(results: List<ConversionResult>): List<Uri> = results
    .filter { it.error == null && !it.skipped }
    .mapNotNull { it.output }
    .distinct()

/** ClipData grants every JPEG to the chosen receiver, including SEND_MULTIPLE. */
internal fun jpegShareIntent(uris: List<Uri>): Intent {
    val files = uris.distinct()
    require(files.isNotEmpty() && files.all { it.scheme == "content" }) { "No saved JPEGs are available to share." }
    return Intent(if (files.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/jpeg"
        if (files.size == 1) putExtra(Intent.EXTRA_STREAM, files.single())
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files))
        clipData = ClipData("Converted JPEGs", arrayOf("image/jpeg"), ClipData.Item(files.first())).apply {
            files.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
