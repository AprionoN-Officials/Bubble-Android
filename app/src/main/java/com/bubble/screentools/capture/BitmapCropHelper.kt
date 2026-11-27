package com.bubble.screentools.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import com.bubble.screentools.model.SelectionRegion
import com.bubble.screentools.model.SelectionShape
import com.bubble.screentools.model.TranslationQualityMode
import com.bubble.screentools.ocr.OcrImagePreprocessor
import com.bubble.screentools.ui.overlay.SelectionPathBuilder
import kotlin.math.roundToInt

/**
 * Result of cropping a full-screen bitmap to a user selection region.
 * Preserves view-coordinate [cropLeft], [cropTop], [padPx], and [scaleX]/[scaleY]
 * so OCR bounding boxes map accurately back to overlay view coordinates even when
 * view size != bitmap size or when small crops are upscaled for better OCR accuracy.
 */
data class CroppedBitmapResult(
    val bitmap: Bitmap,
    val cropLeft: Float,
    val cropTop: Float,
    val scaleFactor: Float,
    val scaleX: Float = scaleFactor,
    val scaleY: Float = scaleFactor,
    val padPx: Int = 0
)

object BitmapCropHelper {

    fun computeScaleFactor(width: Int, height: Int, maxDimensionPx: Int): Float {
        val maxSide = maxOf(width, height)
        if (maxSide <= 0 || maxDimensionPx <= 0 || maxSide <= maxDimensionPx) return 1f
        return maxDimensionPx.toFloat() / maxSide.toFloat()
    }

    /**
     * Crops [source] to [region] (converting from [viewWidth]x[viewHeight] to [source] pixels if provided).
     * - Supports ALL 5 selection shapes: RECTANGLE, ROUNDED_RECTANGLE, ELLIPSE, FREEFORM, CUSTOM_CURVED.
     * - For non-rectangular Visual Search/Export crops: applies transparent shape mask via [SelectionPathBuilder].
     * - For non-rectangular OCR crops: fills pixels outside the shape mask with the sampled background color
     *   so OCR strictly reads text inside the shape without dark-edge recognition artifacts.
     * - Respects [qualityMode] for OCR upscaling and contrast preprocessing.
     * Caller owns [CroppedBitmapResult.bitmap] and must recycle it when done.
     */
    fun cropAndMask(
        source: Bitmap,
        region: SelectionRegion,
        maxDimensionPx: Int,
        viewWidth: Int = 0,
        viewHeight: Int = 0,
        forOcr: Boolean = false,
        qualityMode: TranslationQualityMode = TranslationQualityMode.NATURAL
    ): CroppedBitmapResult {
        val safeSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }

        try {
            val viewToBmpX = if (viewWidth > 0) safeSource.width.toFloat() / viewWidth.toFloat() else 1f
            val viewToBmpY = if (viewHeight > 0) safeSource.height.toFloat() / viewHeight.toFloat() else 1f

            fun safeCoord(v: Float, fallback: Float): Int {
                val safe = if (v.isFinite()) v else fallback
                return safe.roundToInt()
            }

            val rawLeft = safeCoord(region.bounds.left * viewToBmpX, 0f)
            val rawTop = safeCoord(region.bounds.top * viewToBmpY, 0f)
            val rawRight = safeCoord(region.bounds.right * viewToBmpX, safeSource.width.toFloat())
            val rawBottom = safeCoord(region.bounds.bottom * viewToBmpY, safeSource.height.toFloat())

            val isNonRectMask = region.shape == SelectionShape.ELLIPSE ||
                region.shape == SelectionShape.FREEFORM ||
                region.shape == SelectionShape.CUSTOM_CURVED ||
                (region.shape == SelectionShape.ROUNDED_RECTANGLE && !forOcr && region.cornerRadiusPx > 2f)

            val expandPx = if (forOcr && !isNonRectMask) 8 else 0
            val left = (rawLeft - expandPx).coerceIn(0, (safeSource.width - 1).coerceAtLeast(0))
            val top = (rawTop - expandPx).coerceIn(0, (safeSource.height - 1).coerceAtLeast(0))
            val right = (rawRight + expandPx).coerceIn((left + 1).coerceAtMost(safeSource.width), safeSource.width)
            val bottom = (rawBottom + expandPx).coerceIn((top + 1).coerceAtMost(safeSource.height), safeSource.height)

            val rect = Rect(left, top, right, bottom)
            val cropW = rect.width().coerceAtLeast(1)
            val cropH = rect.height().coerceAtLeast(1)

            var cropped = Bitmap.createBitmap(safeSource, rect.left, rect.top, cropW, cropH)

            if (isNonRectMask) {
                val maskPath = buildLocalMaskPath(region, rect, cropW, cropH, viewToBmpX, viewToBmpY)
                val masked = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(masked)
                if (forOcr) {
                    // Fill exterior with sampled interior backdrop color so OCR ignores outside text cleanly
                    val bgSample = runCatching {
                        cropped.getPixel(cropW / 2, (cropH * 0.1f).toInt().coerceIn(0, cropH - 1))
                    }.getOrDefault(Color.WHITE)
                    canvas.drawColor(bgSample)
                    canvas.save()
                    canvas.clipPath(maskPath)
                    canvas.drawBitmap(cropped, 0f, 0f, null)
                    canvas.restore()
                } else {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    canvas.drawPath(maskPath, paint)
                    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                    canvas.drawBitmap(cropped, 0f, 0f, paint)
                }
                cropped.recycle()
                cropped = masked
            }

            // Compute scaling: in FAST mode skip upscaling & use tighter dimension cap; in ACCURATE/NATURAL upscale small crops
            val effectiveMaxDim = if (forOcr && qualityMode == TranslationQualityMode.FAST) {
                minOf(maxDimensionPx, 1080)
            } else {
                maxDimensionPx
            }
            var bmpScale = computeScaleFactor(cropped.width, cropped.height, effectiveMaxDim)
            val allowUpscale = forOcr && qualityMode != TranslationQualityMode.FAST
            if (allowUpscale && bmpScale == 1f && minOf(cropped.width, cropped.height) < 180 &&
                maxOf(cropped.width, cropped.height) * 2 <= effectiveMaxDim
            ) {
                bmpScale = 2f
            }

            if (bmpScale != 1f) {
                val targetW = (cropped.width * bmpScale).roundToInt().coerceAtLeast(1)
                val targetH = (cropped.height * bmpScale).roundToInt().coerceAtLeast(1)
                val actualScale = targetW.toFloat() / cropped.width.toFloat()
                val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
                cropped.recycle()
                cropped = scaled
                bmpScale = actualScale
            }

            // Apply subtle contrast preprocessing for ACCURATE & NATURAL OCR quality modes
            if (forOcr && qualityMode != TranslationQualityMode.FAST) {
                val enhanced = OcrImagePreprocessor.preprocessIfNeeded(cropped, qualityMode)
                if (enhanced !== cropped) {
                    cropped.recycle()
                    cropped = enhanced
                }
            }

            val padPx = if (forOcr && qualityMode != TranslationQualityMode.FAST) 16 else 0
            if (padPx > 0) {
                val padded = Bitmap.createBitmap(
                    cropped.width + padPx * 2,
                    cropped.height + padPx * 2,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(padded)
                val cornerColor = runCatching { cropped.getPixel(0, 0) }.getOrDefault(Color.WHITE)
                canvas.drawColor(cornerColor)
                canvas.drawBitmap(cropped, padPx.toFloat(), padPx.toFloat(), null)
                cropped.recycle()
                cropped = padded
            }

            val totalScaleX = viewToBmpX * bmpScale
            val totalScaleY = viewToBmpY * bmpScale

            return CroppedBitmapResult(
                bitmap = cropped,
                cropLeft = rect.left / viewToBmpX,
                cropTop = rect.top / viewToBmpY,
                scaleFactor = totalScaleX,
                scaleX = totalScaleX,
                scaleY = totalScaleY,
                padPx = padPx
            )
        } finally {
            if (safeSource !== source && !safeSource.isRecycled) {
                safeSource.recycle()
            }
        }
    }

    private fun buildLocalMaskPath(
        region: SelectionRegion,
        rect: Rect,
        cropW: Int,
        cropH: Int,
        viewToBmpX: Float,
        viewToBmpY: Float
    ): Path {
        val localBounds = RectF(0f, 0f, cropW.toFloat(), cropH.toFloat())
        if (region.shape == SelectionShape.FREEFORM && region.freeformPath != null) {
            val transformed = Path()
            val matrix = Matrix().apply {
                setScale(viewToBmpX, viewToBmpY)
                postTranslate(-rect.left.toFloat(), -rect.top.toFloat())
            }
            region.freeformPath.transform(matrix, transformed)
            return transformed
        }
        val scaledCornerRadius = region.cornerRadiusPx * ((viewToBmpX + viewToBmpY) * 0.5f)
        return SelectionPathBuilder.buildShapePath(
            bounds = localBounds,
            shape = region.shape,
            cornerRadiusPx = scaledCornerRadius,
            curvatureLevel = region.curvatureLevel
        )
    }
}
