// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeSelectionTest {
    @Test fun outputFolderDoesNotReplaceSourceFolderOrPhotos() {
        val folder = TreeSelection(sourceFolderUri = "content://folders/raw-test", outputFolderUri = null)
        val afterFolder = folder.withTree(TreePickTarget.Output, "content://folders/raw-result")
        assertEquals("content://folders/raw-test", afterFolder.sourceFolderUri)
        assertEquals("content://folders/raw-result", afterFolder.outputFolderUri)
        assertTrue(afterFolder.photoUris.isEmpty())

        val photos = TreeSelection(photoUris = listOf("content://photos/DSC0001.ARW", "content://photos/DSC0002.ARW"))
        val afterPhotos = photos.withTree(TreePickTarget.Output, "content://folders/raw-result")
        assertEquals(listOf("content://photos/DSC0001.ARW", "content://photos/DSC0002.ARW"), afterPhotos.photoUris)
        assertNull(afterPhotos.sourceFolderUri)
        assertEquals("content://folders/raw-result", afterPhotos.outputFolderUri)
    }

    @Test fun sourceFolderReplacesPhotosWithoutClearingOutput() {
        val selected = TreeSelection(
            photoUris = listOf("content://photos/DSC0001.ARW"),
            outputFolderUri = "content://folders/raw-result",
        )
        val after = selected.withTree(TreePickTarget.Source, "content://folders/raw-test")
        assertEquals("content://folders/raw-test", after.sourceFolderUri)
        assertTrue(after.photoUris.isEmpty())
        assertEquals("content://folders/raw-result", after.outputFolderUri)
    }

    @Test fun photosReplaceSourceFolderWithoutClearingOutput() {
        val folder = TreeSelection(
            sourceFolderUri = "content://folders/raw-test",
            outputFolderUri = "content://folders/raw-result",
        )
        val after = folder.withPhotos(listOf("content://photos/DSC0001.ARW", "content://photos/DSC0001.ARW"))
        assertNull(after.sourceFolderUri)
        assertEquals(listOf("content://photos/DSC0001.ARW"), after.photoUris)
        assertEquals("content://folders/raw-result", after.outputFolderUri)
    }
}
