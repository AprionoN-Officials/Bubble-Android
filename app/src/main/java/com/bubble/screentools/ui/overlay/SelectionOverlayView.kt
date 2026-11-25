package com.bubble.screentools.ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.bubble.screentools.model.SelectionRegion
import com.bubble.screentools.model.SelectionShape
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight Canvas Custom View rendering the dim backdrop, clear selection cutout,
 * rounded blue border (#2196F3), white resize handles, and Rectangle / Ellipse / Freeform paths.
 */
class SelectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface SelectionListener {
        fun onSelectionInteractionStarted() {}
        fun onSelectionInteractionEnded() {}
        fun onSelectionChanged(region: SelectionRegion, wasMoveOnly: Boolean)
        fun onSelectionCleared()
    }

    var listener: SelectionListener? = null
    var shape: SelectionShape = SelectionShape.RECTANGLE
        set(value) {
            field = value
            invalidate()
        }

    var hapticEnabled: Boolean = true
    var dimOpacity: Float = 0.38f
        set(value) {
            field = value.coerceIn(0.1f, 0.75f)
            dimPaint.alpha = (field * 255).toInt()
            invalidate()
        }

    var borderColor: Int = Color.parseColor("#2196F3")
        set(value) {
            field = value
            borderPaint.color = value
            invalidate()
        }

    var borderThicknessDp: Float = 3.5f
        set(value) {
            field = value
            borderPaint.strokeWidth = dpToPx(value)
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val minSelectionPx = dpToPx(42f)
    private val handleRadiusPx = dpToPx(6.5f)
    private val anchorHandleRadiusPx = dpToPx(9.5f)
    private val cornerRadiusPx = dpToPx(16f)
    private val handleTouchRadiusPx = dpToPx(24f)

    private val selectionRect = RectF()
    private val freeformPath = Path()
    private val freeformPoints = ArrayList<Pair<Float, Float>>()

    private var activeHandle = SelectionGestureHandler.HandleType.NONE
    private var isDrawingNew = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var hasMovedSignificantly = false

    private val dimPaint = Paint().apply {
        color = Color.BLACK
        alpha = (0.38f * 255).toInt()
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            if (selectionRect.isEmpty) {
                setDefaultInitialSelection(w, h)
            } else {
                // Keep selection inside screen after orientation change
                selectionRect.left = selectionRect.left.coerceIn(0f, max(0f, w - minSelectionPx))
                selectionRect.top = selectionRect.top.coerceIn(0f, max(0f, h - minSelectionPx))
                selectionRect.right = selectionRect.right.coerceIn(selectionRect.left + minSelectionPx, w.toFloat())
                selectionRect.bottom = selectionRect.bottom.coerceIn(selectionRect.top + minSelectionPx, h.toFloat())
            }
        }
    }

    fun setDefaultInitialSelection(w: Int = width, h: Int = height) {
        if (w <= 0 || h <= 0) return
        val marginX = w * 0.08f
        val top = h * 0.10f
        val bottom = h * 0.78f
        selectionRect.set(marginX, top, w - marginX, bottom)
        freeformPath.reset()
        invalidate()
    }

    fun getCurrentRegion(): SelectionRegion {
        return SelectionRegion(
            bounds = RectF(selectionRect),
            shape = shape,
            freeformPath = if (shape == SelectionShape.FREEFORM && !freeformPath.isEmpty) Path(freeformPath) else null
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        if (selectionRect.width() <= 1f || selectionRect.height() <= 1f) return

        when (shape) {
            SelectionShape.RECTANGLE -> {
                canvas.drawRoundRect(selectionRect, cornerRadiusPx, cornerRadiusPx, clearPaint)
                canvas.drawRoundRect(selectionRect, cornerRadiusPx, cornerRadiusPx, borderPaint)
                drawHandles(canvas)
            }
            SelectionShape.ELLIPSE -> {
                canvas.drawOval(selectionRect, clearPaint)
                canvas.drawOval(selectionRect, borderPaint)
                drawHandles(canvas)
            }
            SelectionShape.FREEFORM -> {
                if (!freeformPath.isEmpty) {
                    canvas.drawPath(freeformPath, clearPaint)
                    canvas.drawPath(freeformPath, borderPaint)
                } else {
                    canvas.drawRoundRect(selectionRect, cornerRadiusPx, cornerRadiusPx, clearPaint)
                    canvas.drawRoundRect(selectionRect, cornerRadiusPx, cornerRadiusPx, borderPaint)
                }
            }
        }
    }

    private fun drawHandles(canvas: Canvas) {
        val points = listOf(
            Pair(selectionRect.right, selectionRect.centerY()) to handleRadiusPx,
            Pair(selectionRect.centerX(), selectionRect.bottom) to handleRadiusPx,
            Pair(selectionRect.left, selectionRect.centerY()) to handleRadiusPx,
            Pair(selectionRect.centerX(), selectionRect.top) to handleRadiusPx,
            Pair(selectionRect.bottom.let { selectionRect.right }, selectionRect.bottom) to anchorHandleRadiusPx
        )
        for ((pt, radius) in points) {
            canvas.drawCircle(pt.first, pt.second, radius, handleFillPaint)
            canvas.drawCircle(pt.first, pt.second, radius, handleStrokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.coerceIn(0f, width.toFloat())
        val y = event.y.coerceIn(0f, height.toFloat())

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                listener?.onSelectionInteractionStarted()
                downX = x
                downY = y
                lastX = x
                lastY = y
                hasMovedSignificantly = false

                if (shape == SelectionShape.FREEFORM) {
                    if (selectionRect.contains(x, y) && !freeformPath.isEmpty) {
                        activeHandle = SelectionGestureHandler.HandleType.INSIDE_MOVE
                        isDrawingNew = false
                    } else {
                        isDrawingNew = true
                        activeHandle = SelectionGestureHandler.HandleType.NONE
                        freeformPoints.clear()
                        freeformPoints.add(x to y)
                        freeformPath.reset()
                        freeformPath.moveTo(x, y)
                        selectionRect.set(x, y, x, y)
                    }
                } else {
                    activeHandle = SelectionGestureHandler.detectHitHandle(
                        x, y, selectionRect, handleTouchRadiusPx
                    )
                    isDrawingNew = (activeHandle == SelectionGestureHandler.HandleType.NONE)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY
                if (abs(x - downX) > dpToPx(6f) || abs(y - downY) > dpToPx(6f)) {
                    hasMovedSignificantly = true
                }

                if (isDrawingNew) {
                    if (shape == SelectionShape.FREEFORM) {
                        freeformPoints.add(x to y)
                        freeformPath.lineTo(x, y)
                        freeformPath.computeBounds(selectionRect, true)
                    } else {
                        selectionRect.set(
                            min(downX, x),
                            min(downY, y),
                            max(downX, x),
                            max(downY, y)
                        )
                    }
                } else {
                    if (shape == SelectionShape.FREEFORM && activeHandle == SelectionGestureHandler.HandleType.INSIDE_MOVE) {
                        val w = selectionRect.width()
                        val h = selectionRect.height()
                        val clampedDx = (selectionRect.left + dx).coerceIn(0f, max(0f, width - w)) - selectionRect.left
                        val clampedDy = (selectionRect.top + dy).coerceIn(0f, max(0f, height - h)) - selectionRect.top
                        val matrix = Matrix().apply { setTranslate(clampedDx, clampedDy) }
                        freeformPath.transform(matrix)
                        selectionRect.offset(clampedDx, clampedDy)
                    } else {
                        SelectionGestureHandler.applyResize(
                            selectionRect, activeHandle, dx, dy,
                            minSelectionPx, width.toFloat(), height.toFloat()
                        )
                    }
                }
                lastX = x
                lastY = y
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawingNew && shape == SelectionShape.FREEFORM && freeformPoints.size >= 3) {
                    freeformPath.close()
                    freeformPath.computeBounds(selectionRect, true)
                }

                if (!hasMovedSignificantly && isDrawingNew) {
                    // Tap outside active selection resets to clear state
                    selectionRect.setEmpty()
                    freeformPath.reset()
                    invalidate()
                    listener?.onSelectionCleared()
                } else if (selectionRect.width() >= minSelectionPx && selectionRect.height() >= minSelectionPx) {
                    if (hapticEnabled) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    val moveOnly = (!isDrawingNew && activeHandle == SelectionGestureHandler.HandleType.INSIDE_MOVE)
                    listener?.onSelectionChanged(getCurrentRegion(), wasMoveOnly = moveOnly)
                }
                isDrawingNew = false
                activeHandle = SelectionGestureHandler.HandleType.NONE
                invalidate()
                listener?.onSelectionInteractionEnded()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dpToPx(dp: Float): Float = dp * density
}
