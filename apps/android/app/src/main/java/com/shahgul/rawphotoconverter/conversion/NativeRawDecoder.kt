package com.shahgul.rawphotoconverter.conversion

import android.graphics.Bitmap
import java.util.concurrent.CancellationException

class ConversionCancellation {
    @JvmField @Volatile var cancelled: Boolean = false
    fun check() { if (cancelled) throw CancellationException("Conversion cancelled") }
}

object NativeRawDecoder {
    init { System.loadLibrary("rawconverter") }
    external fun cameraPreview(sourcePath: String, cancellation: ConversionCancellation): ByteArray
    external fun decode(sourcePath: String, maxLongEdge: Int, cancellation: ConversionCancellation): Bitmap
}
