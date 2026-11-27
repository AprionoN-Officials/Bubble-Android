package com.bubble.screentools.ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
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
import android.view.animation.LinearInterpolator
import com.bubble.screentools.model.SelectionBorderStyle
import com.bubble.screentools.model.SelectionConfig
import com.bubble.screentools.model.SelectionRegion
import com.bubble.screentools.model.SelectionShape
import com.bubble.screentools.theme.ThemePaletteGenerator
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight Canvas Custom View rendering the dim backdrop, clear selection cutout,
 * optional fill tint, customizable border styles (Solid/Dashed/Dotted/Dash-dot),
 * configurable resize handles, and Rectangle / Rounded Rectangle / Ellipse / Freeform / Custom Curved paths.
 * Shared directly between Live Overlay and Settings Live Preview so preview matches runtime 1:1.
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
    var isPreviewMode: Boolean = false
    var hapticEnabled: Boolean = true

    var shape: SelectionShape = SelectionShape.ROUNDED_RECTANGLE
        set(value) {
            field = value
            invalidate()
        }

    var dimOpacity: Float = 0.38f
        set(value) {
            field = value.coerceIn(0.10f, 0.75f)
            dimPaint.alpha = (field * 255).toInt()
            invalidate()
        }

    var fillOpacity: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 0.45f)
            updateFillPaint()
            invalidate()
        }

    var borderColor: Int = Color.parseColor("#2196F3")
        set(value) {
            field = value
            borderPaint.color = value
            if (handleUseAccentStroke) {
                handleStrokePaint.color = value
            }
            updateFillPaint()
            invalidate()
        }

    var borderThicknessDp: Float = 3.5f
        set(value) {
            field = value.coerceIn(1.5f, 8.0f)
            borderPaint.strokeWidth = dpToPx(field)
            updateBorderPathEffect()
            invalidate()
        }

    var cornerRadiusDp: Float = 16f
        set(value) {
            field = value.coerceIn(0f, 36f)
            invalidate()
        }

    var curvatureLevel: Float = 0.55f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var borderStyle: SelectionBorderStyle = SelectionBorderStyle.SOLID
        set(value) {
            field = value
            updateBorderPathEffect()
            updateAnimationState()
            invalidate()
        }

    var showResizeHandles: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var handleSizeDp: Float = 7.0f
        set(value) {
            field = value.coerceIn(4f, 13f)
            handleRadiusPx = dpToPx(field)
            anchorHandleRadiusPx = dpToPx(field + 2.5f)
            invalidate()
        }

    var handleColor: Int = Color.WHITE
        set(value) {
            field = value
            handleFillPaint.color = value
            invalidate()
        }

    var handleUseAccentStroke: Boolean = true
        set(value) {
            field = value
            handleStrokePaint.color = if (value) borderColor else Color.parseColor("#334155")
            invalidate()
        }

    var smoothFreeformPath: Boolean = true
        set(value) {
            field = value
            if (freeformPoints.size >= 3) {
                rebuildFreeformPath(close = true)
                invalidate()
            }
        }

    var edgeSmoothing: Boolean = true
        set(value) {
            field = value
            borderPaint.isAntiAlias = value
            clearPaint.isAntiAlias = value
            fillPaint.isAntiAlias = value
            handleFillPaint.isAntiAlias = value
            handleStrokePaint.isAntiAlias = value
            invalidate()
        }

    var animationEnabled: Boolean = true
        set(value) {
            field = value
            updateAnimationState()
        }

    var minSelectionSizeDp: Float = 42f
        set(value) {
            field = value.coerceIn(24f, 96f)
            minSelectionPx = dpToPx(field)
        }

    private val density = resources.displayMetrics.density
    private var minSelectionPx = dpToPx(42f)
    private var handleRadiusPx = dpToPx(7.0f)
    private var anchorHandleRadiusPx = dpToPx(9.5f)
    private val handleTouchRadiusPx = dpToPx(24f)

    private val selectionRect = RectF()
    private val freeformPath = Path()
    private val freeformPoints = ArrayList<Pair<Float, Float>>()
    private val reusableShapePath = Path()

    private var activeHandle = SelectionGestureHandler.HandleType.NONE
    private var isDrawingNew = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var hasMovedSignificantly = false

    private var dashPhase = 0f
    private var borderAnimator: ValueAnimator? = null

    private val dimPaint = Paint().apply {
        color = Color.BLACK
        alpha = (0.38f * 255).toInt()
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
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
        strokeWidth = dpToPx(1.8f)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun applyConfig(
        config: SelectionConfig,
        resolvedBorderColor: Int,
        allowAnimation: Boolean = true
    ) {
        shape = config.shape
        borderColor = resolvedBorderColor
        borderThicknessDp = config.borderThicknessDp
        cornerRadiusDp = config.cornerRadiusDp
        curvatureLevel = config.curvatureLevel
        fillOpacity = config.fillOpacity
        dimOpacity = config.outerDimOpacity
        borderStyle = config.borderStyle
        showResizeHandles = config.showResizeHandles
        handleSizeDp = config.handleSizeDp
        handleColor = config.handleColor
        handleUseAccentStroke = config.handleUseAccentStroke
        smoothFreeformPath = config.smoothFreeformPath
        edgeSmoothing = config.edgeSmoothing
        minSelectionSizeDp = config.minSelectionSizeDp
        animationEnabled = config.animationEnabled && allowAnimation
        invalidate()
    }

    private fun updateFillPaint() {
        fillPaint.color = if (fillOpacity <= 0.005f) {
            Color.TRANSPARENT
        } else {
            ThemePaletteGenerator.withAlpha(borderColor, fillOpacity)
        }
    }

    private fun updateBorderPathEffect() {
        val stroke = dpToPx(borderThicknessDp)
        borderPaint.pathEffect = when (borderStyle) {
            SelectionBorderStyle.SOLID -> null
            SelectionBorderStyle.DASHED -> DashPathEffect(floatArrayOf(stroke * 3.4f, stroke * 2.2f), dashPhase)
            SelectionBorderStyle.DOTTED -> DashPathEffect(floatArrayOf(stroke * 0.4f, stroke * 2.4f), dashPhase)
            SelectionBorderStyle.DASH_DOT -> DashPathEffect(
                floatArrayOf(stroke * 3.5f, stroke * 1.8f, stroke * 0.5f, stroke * 1.8f),
                dashPhase
            )
        }
    }

    private fun updateAnimationState() {
        val shouldAnimate = isAttachedToWindow &&
            animationEnabled &&
            borderStyle != SelectionBorderStyle.SOLID

        if (!shouldAnimate) {
            borderAnimator?.cancel()
            borderAnimator = null
            dashPhase = 0f
            updateBorderPathEffect()
            return
        }
        if (borderAnimator?.isRunning == true) return
        val cyclePx = dpToPx(borderThicknessDp) * 12f
        borderAnimator = ValueAnimator.ofFloat(0f, -cyclePx).apply {
            duration = 1100L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                dashPhase = anim.animatedValue as Float
                updateBorderPathEffect()
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimationState()
    }

    override fun onDetachedFromWindow() {
        borderAnimator?.cancel()
        borderAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            if (selectionRect.isEmpty) {
                setDefaultInitialSelection(w, h)
            } else {
                val fw = w.toFloat()
                val fh = h.toFloat()
                val safeMinW = min(minSelectionPx, fw)
                val safeMinH = min(minSelectionPx, fh)
                selectionRect.left = selectionRect.left.coerceIn(0f, (fw - safeMinW).coerceAtLeast(0f))
                selectionRect.top = selectionRect.top.coerceIn(0f, (fh - safeMinH).coerceAtLeast(0f))
                selectionRect.right = selectionRect.right.coerceIn((selectionRect.left + safeMinW).coerceAtMost(fw), fw)
                selectionRect.bottom = selectionRect.bottom.coerceIn((selectionRect.top + safeMinH).coerceAtMost(fh), fh)
            }
        }
    }

    fun setDefaultInitialSelection(w: Int = width, h: Int = height) {
        if (w <= 0 || h <= 0) return
        if (isPreviewMode) {
            val marginX = w * 0.14f
            val marginY = h * 0.16f
            selectionRect.set(marginX, marginY, w - marginX, h - marginY)
        } else {
            val marginX = w * 0.08f
            val top = h * 0.10f
            val bottom = h * 0.78f
            selectionRect.set(marginX, top, w - marginX, bottom)
        }
        freeformPoints.clear()
        freeformPath.reset()
        invalidate()
    }

    /**
     * Expands the active selection frame to cover the full screen (with safe inset/handle padding
     * so every edge handle stays easy to grab & resize). Intentionally does NOT trigger auto-scan
     * so the user can fine-tune edges or pick Translate / Search / Copy / Save at their own pace.
     */
    fun selectFullScreen(
        insetLeft: Int = 0,
        insetTop: Int = 0,
        insetRight: Int = 0,
        insetBottom: Int = 0
    ) {
        if (width <= 0 || height <= 0) return
        val margin = dpToPx(8f)
        val left = (insetLeft + margin).coerceIn(0f, (width - minSelectionPx).coerceAtLeast(0f))
        val top = (insetTop + margin).coerceIn(0f, (height - minSelectionPx).coerceAtLeast(0f))
        val right = (width - insetRight - margin).coerceIn((left + minSelectionPx).coerceAtMost(width.toFloat()), width.toFloat())
        val bottom = (height - insetBottom - margin).coerceIn((top + minSelectionPx).coerceAtMost(height.toFloat()), height.toFloat())

        if (shape == SelectionShape.FREEFORM) {
            shape = SelectionShape.ROUNDED_RECTANGLE
        }
        freeformPoints.clear()
        freeformPath.reset()
        selectionRect.set(left, top, right, bottom)
        if (hapticEnabled && !isPreviewMode) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        invalidate()
    }

    fun getCurrentRegion(): SelectionRegion {
        return SelectionRegion(
            bounds = RectF(selectionRect),
            shape = shape,
            freeformPath = if (shape == SelectionShape.FREEFORM && !freeformPath.isEmpty) Path(freeformPath) else null,
            cornerRadiusPx = if (shape == SelectionShape.ROUNDED_RECTANGLE) dpToPx(cornerRadiusDp) else 0f,
            curvatureLevel = curvatureLevel
        )
    }

    private fun rebuildFreeformPath(close: Boolean) {
        freeformPath.set(
            SelectionPathBuilder.buildFreeformPath(
                points = freeformPoints,
                smooth = smoothFreeformPath,
                close = close
            )
        )
        val b = SelectionPathBuilder.computePointsBounds(freeformPoints)
        selectionRect.set(b.left, b.top, b.right, b.bottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        if (selectionRect.width() <= 1f || selectionRect.height() <= 1f) return

        val cornerPx = dpToPx(cornerRadiusDp)
        reusableShapePath.set(
            SelectionPathBuilder.buildShapePath(
                bounds = selectionRect,
                shape = shape,
                cornerRadiusPx = cornerPx,
                curvatureLevel = curvatureLevel,
                freeformPath = if (shape == SelectionShape.FREEFORM && !freeformPath.isEmpty) freeformPath else null
            )
        )

        canvas.drawPath(reusableShapePath, clearPaint)
        if (fillOpacity > 0.005f) {
            canvas.drawPath(reusableShapePath, fillPaint)
        }
        canvas.drawPath(reusableShapePath, borderPaint)

        if (showResizeHandles && shape != SelectionShape.FREEFORM) {
            drawHandles(canvas)
        }
    }

    private fun drawHandles(canvas: Canvas) {
        val points = listOf(
            Pair(selectionRect.right, selectionRect.centerY()) to handleRadiusPx,
            Pair(selectionRect.centerX(), selectionRect.bottom) to handleRadiusPx,
            Pair(selectionRect.left, selectionRect.centerY()) to handleRadiusPx,
            Pair(selectionRect.centerX(), selectionRect.top) to handleRadiusPx,
            Pair(selectionRect.right, selectionRect.bottom) to anchorHandleRadiusPx
        )
        for ((pt, radius) in points) {
            canvas.drawCircle(pt.first, pt.second, radius, handleFillPaint)
            canvas.drawCircle(pt.first, pt.second, radius, handleStrokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isPreviewMode) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
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
                if (abs(x - downX) > dpToPx(5f) || abs(y - downY) > dpToPx(5f)) {
                    hasMovedSignificantly = true
                }

                if (isDrawingNew) {
                    if (shape == SelectionShape.FREEFORM) {
                        val lastPt = freeformPoints.lastOrNull()
                        if (lastPt == null || hypot(x - lastPt.first, y - lastPt.second) >= dpToPx(3f)) {
                            freeformPoints.add(x to y)
                            rebuildFreeformPath(close = false)
                        }
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
                        for (i in freeformPoints.indices) {
                            val pt = freeformPoints[i]
                            freeformPoints[i] = (pt.first + clampedDx) to (pt.second + clampedDy)
                        }
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
                if (isPreviewMode) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                if (isDrawingNew && shape == SelectionShape.FREEFORM && freeformPoints.size >= 3) {
                    rebuildFreeformPath(close = true)
                }

                if (!hasMovedSignificantly && isDrawingNew) {
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        performClick()
                    }
                    if (isPreviewMode) {
                        setDefaultInitialSelection()
                    } else {
                        selectionRect.setEmpty()
                        freeformPoints.clear()
                        freeformPath.reset()
                        invalidate()
                        listener?.onSelectionCleared()
                    }
                } else if (selectionRect.width() >= minSelectionPx && selectionRect.height() >= minSelectionPx) {
                    if (hapticEnabled && !isPreviewMode) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    val moveOnly = (!isDrawingNew && activeHandle == SelectionGestureHandler.HandleType.INSIDE_MOVE)
                    listener?.onSelectionChanged(getCurrentRegion(), wasMoveOnly = moveOnly)
                } else if (isPreviewMode) {
                    setDefaultInitialSelection()
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

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun dpToPx(dp: Float): Float = dp * density
}
