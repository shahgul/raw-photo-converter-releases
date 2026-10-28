// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedInputSelectionTest {
    @Test fun outputFolderUriIsNotRestoredAsTheSourceFolderWhenTheyMatch() {
        val saved = SavedInputSelection(
            sourceFolderUri = "content://folders/raw-result",
            photoUris = listOf("content://photos/DSC06318.ARW"),
        )
        assertNull(saved.restorableSourceFolder("content://folders/raw-result"))
        assertEquals("content://folders/raw-test", saved.copy(sourceFolderUri = "content://folders/raw-test")
            .restorableSourceFolder("content://folders/raw-result"))
    }
}
