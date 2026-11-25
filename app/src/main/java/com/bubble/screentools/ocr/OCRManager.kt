package com.bubble.screentools.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.bubble.screentools.capture.BitmapCropHelper
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.model.TextBlockResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Hybrid OCR Engine:
 * - Supports custom Tesseract 5 `.traineddata` models when enabled.
 * - Uses fast unbundled Google Play Services ML Kit Text Recognition as default / fallback.
 * - Supports both cropped-region OCR (with padding & upscaling) and full-screen live text scanning.
 */
class OCRManager(
    private val context: Context? = null,
    private val preferences: BubblePreferences? = null
) {

    private var recognizer: TextRecognizer? = null
    private var tesseractManager: TesseractOcrManager? = null

    private fun getOrCreateRecognizer(): TextRecognizer {
        return recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .also { recognizer = it }
    }

    private fun getOrCreateTesseract(): TesseractOcrManager? {
        val ctx = context ?: return null
        return tesseractManager ?: TesseractOcrManager(ctx).also { tesseractManager = it }
    }

    suspend fun recognizeBitmap(
        bitmap: Bitmap,
        cropOffsetLeft: Float,
        cropOffsetTop: Float,
        scaleFactor: Float = 1f,
        scaleX: Float = scaleFactor,
        scaleY: Float = scaleFactor,
        padPx: Int = 0
    ): List<TextBlockResult> {
        val prefs = preferences
        val ctx = context
        if (prefs != null && ctx != null && prefs.useTesseractOcr && prefs.activeTesseractLang.isNotBlank()) {
            val tess = getOrCreateTesseract()
            if (tess != null && TesseractOcrManager.hasAllRequestedModels(ctx, prefs.activeTesseractLang)) {
                val tessBlocks = tess.recognizeBitmap(
                    bitmap = bitmap,
                    cropOffsetLeft = cropOffsetLeft - padPx / scaleX,
                    cropOffsetTop = cropOffsetTop - padPx / scaleY,
                    scaleFactor = scaleX,
                    langCode = prefs.activeTesseractLang,
                    scaleY = scaleY
                )
                if (tessBlocks.isNotEmpty()) {
                    return tessBlocks
                }
            }
        }

        return recognizeWithMlKit(bitmap, cropOffsetLeft, cropOffsetTop, scaleX, scaleY, padPx)
    }

    /**
     * Scans the entire screen snapshot to detect all selectable text blocks in view coordinates.
     */
    suspend fun recognizeFullScreen(
        source: Bitmap,
        viewWidth: Int,
        viewHeight: Int,
        maxDimensionPx: Int = 1440
    ): List<TextBlockResult> {
        if (source.isRecycled) return emptyList()
        val safeSource = withContext(Dispatchers.Default) {
            if (source.config == Bitmap.Config.HARDWARE) {
                source.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                source
            }
        }

        var workingBitmap = safeSource
        try {
            val viewToBmpX = if (viewWidth > 0) safeSource.width.toFloat() / viewWidth.toFloat() else 1f
            val viewToBmpY = if (viewHeight > 0) safeSource.height.toFloat() / viewHeight.toFloat() else 1f

            var bmpScale = BitmapCropHelper.computeScaleFactor(safeSource.width, safeSource.height, maxDimensionPx)
            if (bmpScale < 1f) {
                val targetW = (safeSource.width * bmpScale).roundToInt().coerceAtLeast(1)
                val targetH = (safeSource.height * bmpScale).roundToInt().coerceAtLeast(1)
                val actualScale = targetW.toFloat() / safeSource.width.toFloat()
                workingBitmap = withContext(Dispatchers.Default) {
                    Bitmap.createScaledBitmap(safeSource, targetW, targetH, true)
                }
                bmpScale = actualScale
            }

            return recognizeBitmap(
                bitmap = workingBitmap,
                cropOffsetLeft = 0f,
                cropOffsetTop = 0f,
                scaleFactor = viewToBmpX * bmpScale,
                scaleX = viewToBmpX * bmpScale,
                scaleY = viewToBmpY * bmpScale,
                padPx = 0
            )
        } finally {
            if (workingBitmap !== safeSource && !workingBitmap.isRecycled) {
                workingBitmap.recycle()
            }
            if (safeSource !== source && !safeSource.isRecycled) {
                safeSource.recycle()
            }
        }
    }

    private suspend fun recognizeWithMlKit(
        bitmap: Bitmap,
        cropOffsetLeft: Float,
        cropOffsetTop: Float,
        scaleX: Float,
        scaleY: Float,
        padPx: Int
    ): List<TextBlockResult> = suspendCancellableCoroutine { cont ->
        if (bitmap.isRecycled) {
            cont.resume(emptyList())
            return@suspendCancellableCoroutine
        }
        val image = InputImage.fromBitmap(bitmap, 0)
        getOrCreateRecognizer().process(image)
            .addOnSuccessListener { visionText ->
                val blocks = visionText.textBlocks.mapNotNull { block ->
                    val clean = block.text.trim()
                    val box = block.boundingBox ?: return@mapNotNull null
                    if (clean.isEmpty()) return@mapNotNull null
                    val mapped = RectF(
                        cropOffsetLeft + (box.left - padPx) / scaleX,
                        cropOffsetTop + (box.top - padPx) / scaleY,
                        cropOffsetLeft + (box.right - padPx) / scaleX,
                        cropOffsetTop + (box.bottom - padPx) / scaleY
                    )
                    TextBlockResult(
                        text = clean,
                        boundingBox = mapped,
                        fromAccessibility = false
                    )
                }
                cont.resume(blocks)
            }
            .addOnFailureListener {
                cont.resume(emptyList())
            }
    }

    fun release() {
        recognizer?.close()
        recognizer = null
        tesseractManager?.release()
        tesseractManager = null
    }
}
