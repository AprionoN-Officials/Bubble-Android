package com.bubble.screentools.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.bubble.screentools.model.TranslatedBlock
import com.bubble.screentools.model.TranslationOverlayStyle

/**
 * Renders translated text blocks directly over original screen bounding boxes
 * with auto-fitted font sizes and adaptive readability scrims.
 */
class TranslationOverlayRenderer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var blocks: List<TranslatedBlock> = emptyList()
    private var showOriginal: Boolean = false
    private var overlayStyle: TranslationOverlayStyle = TranslationOverlayStyle.ADAPTIVE_COVER

    private val density = resources.displayMetrics.density
    private val boxRadiusPx = 6f * density
    private val paddingPx = 4f * density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(232, 32, 33, 36)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.argb(95, 66, 165, 245)
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * density
    }

    fun setTranslations(
        translatedBlocks: List<TranslatedBlock>,
        showOriginalText: Boolean,
        style: TranslationOverlayStyle
    ) {
        blocks = translatedBlocks
        showOriginal = showOriginalText
        overlayStyle = style
        bgPaint.color = when (style) {
            TranslationOverlayStyle.ADAPTIVE_COVER -> Color.argb(236, 32, 33, 36)
            TranslationOverlayStyle.SEMI_TRANSPARENT_BOX -> Color.argb(188, 18, 24, 38)
        }
        invalidate()
    }

    fun clear() {
        blocks = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (blocks.isEmpty()) return

        for (block in blocks) {
            val content = if (showOriginal) block.originalText else block.translatedText
            val box = RectF(block.boundingBox)
            if (box.width() < 16f || box.height() < 12f) continue

            val availableW = (box.width() - paddingPx * 2).toInt().coerceAtLeast(24)
            val layout = buildBestFitLayout(content, availableW, box.height() - paddingPx * 2)

            val drawRect = RectF(
                box.left,
                box.top,
                maxOf(box.right, box.left + layout.width + paddingPx * 2),
                maxOf(box.bottom, box.top + layout.height + paddingPx * 2)
            )
            canvas.drawRoundRect(drawRect, boxRadiusPx, boxRadiusPx, bgPaint)
            canvas.drawRoundRect(drawRect, boxRadiusPx, boxRadiusPx, borderPaint)

            canvas.save()
            canvas.translate(drawRect.left + paddingPx, drawRect.top + paddingPx)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    private fun buildBestFitLayout(text: String, maxWidthPx: Int, maxHeightPx: Float): StaticLayout {
        var fontSize = (15f * density).coerceAtLeast(10f * density)
        val minFontSize = 9.5f * density
        var layout: StaticLayout

        while (true) {
            textPaint.textSize = fontSize
            layout = StaticLayout.Builder
                .obtain(text, 0, text.length, textPaint, maxWidthPx)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
            if (layout.height <= maxHeightPx || fontSize <= minFontSize) {
                break
            }
            fontSize -= 1.2f * density
        }
        return layout
    }
}
