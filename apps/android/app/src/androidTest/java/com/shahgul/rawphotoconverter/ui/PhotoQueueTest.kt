// SPDX-License-Identifier: MPL-2.0
package com.shahgul.rawphotoconverter.ui

import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.shahgul.rawphotoconverter.MainActivity
import com.shahgul.rawphotoconverter.conversion.*
import com.shahgul.rawphotoconverter.data.RawSourceSummary
import com.shahgul.rawphotoconverter.data.SelectedRaw
import com.shahgul.rawphotoconverter.ui.theme.RawPhotoConverterTheme
import com.shahgul.rawphotoconverter.ui.theme.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PhotoQueueTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun queueRemovalUpdatesCountAndDisablesConversionWhenEmpty() {
        val first = SelectedRaw(Uri.parse("content://photos/first"), "first.ARW", 1024)
        val second = SelectedRaw(Uri.parse("content://photos/second"), "second.CR3", null)
        val source = mutableStateOf<RawSourceSummary?>(RawSourceSummary.Photos(listOf(first, second)))
        var added = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                RawPhotoConverterTheme {
                    ConverterWorkspace(source.value, false, BatchState(),
                        ConversionMode.DEVELOP_RAW, OutputResolution.STANDARD, {}, {},
                        "JPEGs", true, null, { added++ }, {}, {}, {}, {}, {}, {},
                        ThemePreference.System, {}, onRemovePhoto = { uri ->
                            val current = source.value as RawSourceSummary.Photos
                            val remaining = current.photos.filterNot { it.uri == uri }
                            source.value = if (remaining.isEmpty()) null else RawSourceSummary.Photos(remaining)
                        })
                }
            }
        }
        compose.onNodeWithText("Convert 2 photos").assertIsEnabled()
        compose.onNodeWithText("Add photos").performClick()
        assertEquals(1, added)
        compose.onNodeWithContentDescription("Remove first.ARW").performClick()
        compose.onNodeWithText("first.ARW").assertDoesNotExist()
        compose.onNodeWithText("Convert to JPEG").assertIsEnabled()
        compose.onNodeWithContentDescription("Remove second.CR3").performClick()
        compose.onNodeWithText("Convert to JPEG").assertIsNotEnabled()
    }

    @Test fun queueEditingIsDisabledDuringConversion() {
        val photo = SelectedRaw(Uri.parse("content://photos/first"), "first.ARW", 1024)
        compose.runOnUiThread {
            compose.activity.setContent {
                RawPhotoConverterTheme {
                    ConverterWorkspace(RawSourceSummary.Photos(listOf(photo)), false, BatchState(running = true),
                        ConversionMode.DEVELOP_RAW, OutputResolution.STANDARD, {}, {},
                        "JPEGs", true, null, {}, {}, {}, {}, {}, {}, {}, ThemePreference.System, {})
                }
            }
        }
        compose.onNodeWithContentDescription("Remove first.ARW").assertIsNotEnabled()
        compose.onNodeWithText("Add photos").assertIsNotEnabled()
    }
}
