package com.bubble.screentools.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.bubble.screentools.model.TranslationQualityMode

/**
 * Lightweight bitmap preprocessing for OCR quality modes:
 * - FAST: Zero preprocessing overhead (returns original bitmap untouched).
 * - ACCURATE / NATURAL: Enhances text contrast subtly via GPU/Canvas ColorMatrix
 *   without allocating large intermediate buffers, improving OCR accuracy on low-contrast UI/manga/games.
 */
object OcrImagePreprocessor {

    fun preprocessIfNeeded(source: Bitmap, qualityMode: TranslationQualityMode): Bitmap {
        if (qualityMode == TranslationQualityMode.FAST || source.isRecycled) {
            return source
        }
        val contrast = when (qualityMode) {
            TranslationQualityMode.FAST -> 1.0f
            TranslationQualityMode.ACCURATE -> 1.18f
            TranslationQualityMode.NATURAL -> 1.15f
        }
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val matrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }
}
