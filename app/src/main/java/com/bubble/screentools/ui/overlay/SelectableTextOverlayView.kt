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

/**
 * Interactive full-screen text highlight overlay (Live Text / Select & Copy without Google Lens).
 * Draws tappable highlight chips over all detected on-screen text blocks so users can tap
 * individual blocks or select all to copy/translate immediately.
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
    private val selectedIndices = LinkedHashSet<Int>()

    private val density = resources.displayMetrics.density
    private val cornerRadius = 6f * density
    private val touchExpand = 8f * density

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
            val box = RectF(blocks[i].boundingBox)
            box.inset(-3f * density, -2f * density)
            val isSelected = selectedIndices.contains(i)
            canvas.drawRoundRect(
                box,
                cornerRadius,
                cornerRadius,
                if (isSelected) selectedFillPaint else unselectedFillPaint
            )
            canvas.drawRoundRect(
                box,
                cornerRadius,
                cornerRadius,
                if (isSelected) selectedStrokePaint else unselectedStrokePaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            var hitIndex = -1
            for (i in blocks.indices) {
                val expanded = RectF(blocks[i].boundingBox)
                expanded.inset(-touchExpand, -touchExpand)
                if (expanded.contains(x, y)) {
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
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                invalidate()
                notifySelectionChanged()
                return true
            }
        }
        return true
    }

    private fun notifySelectionChanged() {
        val selected = getSelectedBlocks()
        listener?.onTextSelectionChanged(selected, selected.joinToString("\n") { it.text })
    }
}
