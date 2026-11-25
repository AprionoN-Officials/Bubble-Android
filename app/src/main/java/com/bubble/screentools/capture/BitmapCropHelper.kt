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
import com.bubble.screentools.model.SelectionRegion
import com.bubble.screentools.model.SelectionShape
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
     * - When [forOcr] is true: avoids transparent-black edge artifacts, upscales very small crops (2x)
     *   and adds clean padding so ML Kit / Tesseract OCR reliably detects small or tight text lines.
     * - When [forOcr] is false: applies Ellipse/Freeform shape masking cleanly for Visual Search.
     * Caller owns [CroppedBitmapResult.bitmap] and must recycle it when done.
     */
    fun cropAndMask(
        source: Bitmap,
        region: SelectionRegion,
        maxDimensionPx: Int,
        viewWidth: Int = 0,
        viewHeight: Int = 0,
        forOcr: Boolean = false
    ): CroppedBitmapResult {
        val safeSource = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }

        try {
            val viewToBmpX = if (viewWidth > 0) safeSource.width.toFloat() / viewWidth.toFloat() else 1f
            val viewToBmpY = if (viewHeight > 0) safeSource.height.toFloat() / viewHeight.toFloat() else 1f

            val rawLeft = (region.bounds.left * viewToBmpX).roundToInt()
            val rawTop = (region.bounds.top * viewToBmpY).roundToInt()
            val rawRight = (region.bounds.right * viewToBmpX).roundToInt()
            val rawBottom = (region.bounds.bottom * viewToBmpY).roundToInt()

            // For OCR, add a small safety expansion around the selection so letters touching the border aren't sliced
            val expandPx = if (forOcr) 8 else 0
            val left = (rawLeft - expandPx).coerceIn(0, (safeSource.width - 1).coerceAtLeast(0))
            val top = (rawTop - expandPx).coerceIn(0, (safeSource.height - 1).coerceAtLeast(0))
            val right = (rawRight + expandPx).coerceIn((left + 1).coerceAtMost(safeSource.width), safeSource.width)
            val bottom = (rawBottom + expandPx).coerceIn((top + 1).coerceAtMost(safeSource.height), safeSource.height)

            val rect = Rect(left, top, right, bottom)
            val cropW = rect.width().coerceAtLeast(1)
            val cropH = rect.height().coerceAtLeast(1)

            var cropped = Bitmap.createBitmap(safeSource, rect.left, rect.top, cropW, cropH)

            // Apply shape mask only for Visual Search (OCR works far more accurately on clean rectangular crops)
            if (!forOcr && (region.shape == SelectionShape.ELLIPSE || region.shape == SelectionShape.FREEFORM)) {
                val masked = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(masked)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val maskPath = Path()
                if (region.shape == SelectionShape.ELLIPSE) {
                    maskPath.addOval(0f, 0f, cropW.toFloat(), cropH.toFloat(), Path.Direction.CW)
                } else if (region.freeformPath != null) {
                    val matrix = Matrix().apply {
                        setScale(viewToBmpX, viewToBmpY)
                        postTranslate(-rect.left.toFloat(), -rect.top.toFloat())
                    }
                    region.freeformPath.transform(matrix, maskPath)
                } else {
                    maskPath.addRect(0f, 0f, cropW.toFloat(), cropH.toFloat(), Path.Direction.CW)
                }
                canvas.drawPath(maskPath, paint)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(cropped, 0f, 0f, paint)
                cropped.recycle()
                cropped = masked
            }

            // Compute scaling: upscale tiny OCR crops (2x) for crisp text recognition, or downscale huge crops
            var bmpScale = computeScaleFactor(cropped.width, cropped.height, maxDimensionPx)
            if (forOcr && bmpScale == 1f && minOf(cropped.width, cropped.height) < 160 && maxOf(cropped.width, cropped.height) * 2 <= maxDimensionPx) {
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

            // For OCR, add a subtle border pad so ML Kit never misses glyphs touching the bitmap edges
            val padPx = if (forOcr) 16 else 0
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
}
