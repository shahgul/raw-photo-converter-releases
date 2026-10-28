package com.shahgul.rawphotoconverter.conversion

import org.junit.Assert.*
import org.junit.Test

class ConversionPolicyTest {
    @Test fun originalResolutionHasNoResizeCap() {
        assertEquals(PixelSize(12000, 4000), ConversionPolicy.fit(12000, 4000, OutputResolution.ORIGINAL.maxEdge))
        assertEquals(PixelSize(1616, 1080), ConversionPolicy.fit(1616, 1080, OutputResolution.STANDARD.maxEdge))
        assertEquals(0, ConversionRecipe(maxLongEdge = 0).maxLongEdge)
        assertThrows(IllegalArgumentException::class.java) { ConversionRecipe(maxLongEdge = -1) }
    }

    @Test fun landscapeAndPortraitKeepTheirAspectWithoutUpscaling() {
        assertEquals(PixelSize(4000, 2667), ConversionPolicy.fit(6000, 4000, 4000))
        assertEquals(PixelSize(2667, 4000), ConversionPolicy.fit(4000, 6000, 4000))
        assertEquals(PixelSize(640, 480), ConversionPolicy.fit(640, 480, 4000))
        assertEquals(PixelSize(1, 4000), ConversionPolicy.fit(1, 10000, 4000))
    }

    @Test fun selectsHighestQualityIncludingMetadataBytes() {
        val attempts = mutableListOf<Int>()
        val choice = ConversionPolicy.chooseQuality(ConversionRecipe(targetBytes = 1000)) { quality ->
            attempts += quality
            if (quality <= 91) 1000L else 1001L
        }
        assertEquals(QualityChoice(91, 1000, true), choice)
        assertEquals(listOf(96, 95, 94, 93, 92, 91), attempts)
    }

    @Test fun preservesFloorWhenTargetCannotBeMet() {
        val attempts = mutableListOf<Int>()
        val choice = ConversionPolicy.chooseQuality(ConversionRecipe(targetBytes = 1000)) {
            attempts += it
            1200L
        }
        assertEquals(QualityChoice(80, 1200, false), choice)
        assertEquals(80, attempts.min())
        assertEquals(17, attempts.size)
    }

    @Test fun encodingFailureIsNotTreatedAsASizeResult() {
        assertThrows(IllegalStateException::class.java) {
            ConversionPolicy.chooseQuality(ConversionRecipe()) { error("disk full") }
        }
    }

    @Test fun validatesRecipeAndNamesWithoutReplacingSources() {
        assertThrows(IllegalArgumentException::class.java) { ConversionRecipe(minQuality = 97) }
        assertThrows(IllegalArgumentException::class.java) { ConversionPolicy.fit(0, 10, 4000) }
        assertEquals("DSC.photo.jpg", ConversionPolicy.jpegName("DSC.photo.ARW"))
        assertThrows(IllegalArgumentException::class.java) { ConversionPolicy.jpegName("picture.jpg") }
    }

    @Test fun outputFormatAndNamesSupportNextGenFormats() {
        assertEquals("DSC.photo.jpg", ConversionPolicy.outputName("DSC.photo.ARW", OutputFormat.JPEG))
        assertEquals("DSC.photo.webp", ConversionPolicy.outputName("DSC.photo.ARW", OutputFormat.WEBP))
        assertEquals("DSC.photo.avif", ConversionPolicy.outputName("DSC.photo.ARW", OutputFormat.AVIF))
        assertEquals("sample.webp", ConversionPolicy.outputName("sample.CR3", OutputFormat.WEBP))
        assertEquals(OutputFormat.JPEG, ConversionRecipe().format)
        assertEquals(ColorGamut.SRGB, ConversionRecipe().colorGamut)
        assertEquals(1, ColorGamut.SRGB.exifColorSpace)
        assertEquals(65535, ColorGamut.DISPLAY_P3.exifColorSpace)
    }

    @Test fun unsupportedSelectionMessageExplainsNonRawFormats() {
        assertTrue(ConversionPolicy.unsupportedSelectionMessage("holiday.jpg").contains("not a RAW"))
        assertTrue(ConversionPolicy.unsupportedSelectionMessage("scan.HEIC").contains("regular photo"))
        assertTrue(ConversionPolicy.unsupportedSelectionMessage("notes.txt").contains("not supported"))
    }

    @Test fun sonyArwNamesAreRawEvenWhenTheProviderOmitsMime() {
        assertTrue(ConversionPolicy.isSupportedRawFile("DSC01234.ARW", directory = false))
        assertTrue(ConversionPolicy.isSupportedRawFile("DSC01234.arw", directory = false))
        assertTrue(ConversionPolicy.isSupportedRaw("DSC0001.ARW"))
        assertEquals("DSC01234.jpg", ConversionPolicy.jpegName("DSC01234.ARW"))
        assertFalse(ConversionPolicy.isSupportedRawFile("DSC01234.ARW", directory = true))
        assertFalse(ConversionPolicy.isSupportedRawFile("DSC01234.JPG", directory = false))
        assertFalse(ConversionPolicy.isSupportedRawFile("notes.txt", directory = false))
        val mimes = ConversionPolicy.openDocumentMimeTypes().toSet()
        assertTrue(mimes.contains("*/*"))
        assertTrue(mimes.contains("image/x-sony-arw"))
        assertTrue(mimes.contains("application/octet-stream"))
    }
}
