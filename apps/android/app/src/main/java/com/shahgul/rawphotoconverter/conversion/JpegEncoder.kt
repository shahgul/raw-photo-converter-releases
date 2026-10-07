package com.shahgul.rawphotoconverter.conversion

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource
import org.xml.sax.SAXException

object JpegEncoder {
    fun encode(
        bitmap: Bitmap,
        source: File,
        output: File,
        recipe: ConversionRecipe,
        cancellation: ConversionCancellation,
        orientation: Int = 1,
        phase: (String) -> Unit,
    ): QualityChoice {
        val metadata = if (recipe.preserveMetadata) ExifInterface(source) else null
        return ConversionPolicy.chooseQuality(recipe) { quality ->
            cancellation.check()
            phase("Optimizing JPEG · Q$quality")
            output.outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)) { "JPEG encoding failed." }
            }
            cancellation.check()
            phase("Restoring metadata")
            writeMetadata(output, metadata, bitmap.width, bitmap.height, orientation)
            output.length()
        }
    }

    internal fun writeMetadata(output: File, source: ExifInterface?, width: Int, height: Int, orientation: Int = 1, colorSpace: Int = 1) {
        val destination = ExifInterface(output)
        source?.let { exif ->
            for (tag in preservedTags) exif.getAttribute(tag)?.let { destination.setAttribute(tag, it) }
            exif.getAttribute(ExifInterface.TAG_XMP)?.let {
                destination.setAttribute(ExifInterface.TAG_XMP, normalizeXmp(it, width, height, orientation, colorSpace))
            }
        }
        // RAW development rotates pixels; direct camera JPEGs retain their orientation tag.
        destination.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        destination.setAttribute(ExifInterface.TAG_COLOR_SPACE, colorSpace.toString())
        destination.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, width.toString())
        destination.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, height.toString())
        destination.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
        destination.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
        destination.saveAttributes()
    }

    private fun normalizeXmp(packet: String, width: Int, height: Int, orientation: Int, colorSpace: Int): String {
        // Android's DocumentBuilderFactory does not implement the desktop JAXP
        // security feature flags. XMP needs neither DTDs nor custom entities.
        require(!packet.contains("<!DOCTYPE", ignoreCase = true) && !packet.contains("<!ENTITY", ignoreCase = true)) {
            "XMP contains an unsupported document type or entity declaration. Disable metadata to convert this RAW."
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw SAXException("External XMP entities are not supported.") }
        }
        val document = builder.parse(InputSource(StringReader(packet)))
        val tiff = "http://ns.adobe.com/tiff/1.0/"
        val exif = "http://ns.adobe.com/exif/1.0/"
        val normalized = mapOf(
            (tiff to "Orientation") to orientation.toString(), (tiff to "ImageWidth") to width.toString(),
            (tiff to "ImageLength") to height.toString(), (exif to "PixelXDimension") to width.toString(),
            (exif to "PixelYDimension") to height.toString(), (exif to "ColorSpace") to colorSpace.toString(),
        )
        val elements = document.getElementsByTagName("*")
        for (i in 0 until elements.length) {
            val element = elements.item(i)
            normalized[element.namespaceURI to element.localName]?.let { element.textContent = it }
            val attributes = element.attributes
            for (j in 0 until attributes.length) {
                val attribute = attributes.item(j)
                normalized[attribute.namespaceURI to attribute.localName]?.let { attribute.nodeValue = it }
            }
        }
        val writer = StringWriter()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty("omit-xml-declaration", "yes")
        }.transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }

    private val preservedTags = listOf(
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LENS_SPECIFICATION, ExifInterface.TAG_BODY_SERIAL_NUMBER, ExifInterface.TAG_LENS_SERIAL_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_ISO_SPEED_RATINGS, ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_EXPOSURE_PROGRAM, ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_METERING_MODE, ExifInterface.TAG_FLASH, ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT, ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
    )
}
