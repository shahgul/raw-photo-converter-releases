package com.shahgul.rawphotoconverter.ui

/** Which folder the in-flight document-tree picker will write into. */
internal enum class TreePickTarget { Source, Output }

/**
 * Input and output locations are independent. Applying an output folder must not
 * clear or replace the source folder or the selected photo URIs.
 */
internal data class TreeSelection(
    val sourceFolderUri: String? = null,
    val photoUris: List<String> = emptyList(),
    val outputFolderUri: String? = null,
) {
    fun withTree(target: TreePickTarget, uri: String): TreeSelection = when (target) {
        TreePickTarget.Source -> copy(sourceFolderUri = uri, photoUris = emptyList())
        TreePickTarget.Output -> copy(outputFolderUri = uri)
    }

    fun withPhotos(uris: List<String>): TreeSelection =
        copy(sourceFolderUri = null, photoUris = uris.distinct())
}
