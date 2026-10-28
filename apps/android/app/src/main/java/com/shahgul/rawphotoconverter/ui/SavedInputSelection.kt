package com.shahgul.rawphotoconverter.ui

/**
 * Persisted input slots. Output folder URIs must never be written into [sourceFolderUri].
 */
internal data class SavedInputSelection(
    val sourceFolderUri: String? = null,
    val photoUris: List<String> = emptyList(),
) {
    fun withPhotos(uris: List<String>): SavedInputSelection =
        copy(sourceFolderUri = null, photoUris = uris.distinct())

    fun withSourceFolder(uri: String): SavedInputSelection =
        copy(sourceFolderUri = uri, photoUris = emptyList())

    /** Folder URI saved for input restore; skips the current output folder. */
    fun restorableSourceFolder(outputFolderUri: String?): String? {
        val folder = sourceFolderUri?.takeIf { it.isNotBlank() } ?: return null
        if (outputFolderUri != null && folder == outputFolderUri) return null
        return folder
    }
}
