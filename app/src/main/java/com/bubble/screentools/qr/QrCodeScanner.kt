package com.bubble.screentools.qr

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class DetectedQrResult(
    val rawValue: String,
    val parsed: QrCodeParser.ParsedQr,
    val boundingBox: RectF?
)

/**
 * Fast, high-accuracy offline QR & 2D barcode scanner powered by unbundled
 * Google Play Services ML Kit Barcode Scanning (~200 KB in APK).
 */
class QrCodeScanner {

    private var scanner: BarcodeScanner? = null

    private fun getOrCreateScanner(): BarcodeScanner {
        return scanner ?: BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_DATA_MATRIX
                )
                .build()
        ).also { scanner = it }
    }

    suspend fun scanBitmap(
        bitmap: Bitmap,
        offsetLeft: Float = 0f,
        offsetTop: Float = 0f,
        scaleFactor: Float = 1f
    ): List<DetectedQrResult> = suspendCancellableCoroutine { cont ->
        if (bitmap.isRecycled) {
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        val image = InputImage.fromBitmap(bitmap, 0)
        getOrCreateScanner().process(image)
            .addOnSuccessListener { barcodes ->
                val out = barcodes.mapNotNull { barcode ->
                    val raw = barcode.rawValue?.trim()
                    if (raw.isNullOrEmpty()) return@mapNotNull null
                    val box = barcode.boundingBox?.let { b ->
                        RectF(
                            offsetLeft + b.left / scaleFactor,
                            offsetTop + b.top / scaleFactor,
                            offsetLeft + b.right / scaleFactor,
                            offsetTop + b.bottom / scaleFactor
                        )
                    }
                    DetectedQrResult(
                        rawValue = raw,
                        parsed = QrCodeParser.parse(raw),
                        boundingBox = box
                    )
                }
                cont.resume(out)
            }
            .addOnFailureListener {
                cont.resume(emptyList())
            }
    }

    fun release() {
        runCatching { scanner?.close() }
        scanner = null
    }
}
