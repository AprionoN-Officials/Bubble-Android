package com.bubble.screentools.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.bubble.screentools.model.TextBlockResult
import com.bubble.screentools.util.ScreenLinkDetector

/**
 * Interactive full-screen text & link highlight overlay (Live Text / Select & Copy without Google Lens).
 * Draws tappable highlight chips over detected on-screen text blocks, visually distinguishing
 * blocks containing URLs/links with cyan accent framing and an underline indicator.
 */
class SelectableTextOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface OnTextSelectionChangedListener {
        fun onTextSelectionChanged(selectedBlocks: List<TextBlockResult>, combinedText: String)
    }

    var listener: OnTextSelectionChangedListener? = null

    private var blocks: List<TextBlockResult> = emptyList()
    private var linkBlockIndices: Set<Int> = emptySet()
    private val selectedIndices = LinkedHashSet<Int>()

    private val density = resources.displayMetrics.density
    private val cornerRadius = 6f * density
    private val touchExpand = 8f * density
    private val reusableBox = RectF()

    private val dimPaint = Paint().apply {
        color = Color.argb(90, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val unselectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(65, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val unselectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }

    private val unselectedLinkFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(72, 0, 188, 212)
        style = Paint.Style.FILL
    }

    private val unselectedLinkStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4DD0E1")
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
    }

    private val linkUnderlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80DEEA")
        style = Paint.Style.STROKE
        strokeWidth = 2.0f * density
    }

    private val selectedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 33, 150, 243)
        style = Paint.Style.FILL
    }

    private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
    }

    fun setDetectedBlocks(newBlocks: List<TextBlockResult>) {
        blocks = newBlocks
        linkBlockIndices = buildSet {
            newBlocks.forEachIndexed { idx, block ->
                if (ScreenLinkDetector.containsLink(block.text)) {
                    add(idx)
                }
            }
        }
        selectedIndices.clear()
        invalidate()
        notifySelectionChanged()
    }

    fun selectAll() {
        selectedIndices.clear()
        for (i in blocks.indices) {
            selectedIndices.add(i)
        }
        invalidate()
        notifySelectionChanged()
    }

    fun clearSelection() {
        selectedIndices.clear()
        invalidate()
        notifySelectionChanged()
    }

    fun getSelectedBlocks(): List<TextBlockResult> {
        return selectedIndices.mapNotNull { blocks.getOrNull(it) }
    }

    fun getCombinedSelectedText(): String {
        return getSelectedBlocks().joinToString("\n") { it.text }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        for (i in blocks.indices) {
            reusableBox.set(blocks[i].boundingBox)
            reusableBox.inset(-3f * density, -2f * density)
            val isSelected = selectedIndices.contains(i)
            val isLink = linkBlockIndices.contains(i)

            val fill = when {
                isSelected -> selectedFillPaint
                isLink -> unselectedLinkFillPaint
                else -> unselectedFillPaint
            }
            val stroke = when {
                isSelected -> selectedStrokePaint
                isLink -> unselectedLinkStrokePaint
                else -> unselectedStrokePaint
            }
            canvas.drawRoundRect(reusableBox, cornerRadius, cornerRadius, fill)
            canvas.drawRoundRect(reusableBox, cornerRadius, cornerRadius, stroke)

            if (isLink) {
                val underlineY = reusableBox.bottom - 1.5f * density
                canvas.drawLine(
                    reusableBox.left + 4f * density,
                    underlineY,
                    reusableBox.right - 4f * density,
                    underlineY,
                    linkUnderlinePaint
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            var hitIndex = -1
            for (i in blocks.indices) {
                reusableBox.set(blocks[i].boundingBox)
                reusableBox.inset(-touchExpand, -touchExpand)
                if (reusableBox.contains(x, y)) {
                    hitIndex = i
                    break
                }
            }
            if (hitIndex >= 0) {
                if (selectedIndices.contains(hitIndex)) {
                    selectedIndices.remove(hitIndex)
                } else {
                    selectedIndices.add(hitIndex)
                }
                performClick()
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                invalidate()
                notifySelectionChanged()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun notifySelectionChanged() {
        val selected = getSelectedBlocks()
        listener?.onTextSelectionChanged(selected, selected.joinToString("\n") { it.text })
    }
}
