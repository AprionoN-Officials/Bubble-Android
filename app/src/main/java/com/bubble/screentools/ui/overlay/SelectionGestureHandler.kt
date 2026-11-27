package com.bubble.screentools.ui.overlay

import android.graphics.RectF
import kotlin.math.hypot

/**
 * Pure gesture hit-testing & geometry helper for SelectionOverlayView.
 * Provides pure-Kotlin [BoundsF] overloads for fast JVM unit tests alongside [RectF] APIs.
 */
object SelectionGestureHandler {

    data class BoundsF(
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float
    ) {
        fun width(): Float = right - left
        fun height(): Float = bottom - top
        fun centerX(): Float = (left + right) * 0.5f
        fun centerY(): Float = (top + bottom) * 0.5f
        fun contains(x: Float, y: Float): Boolean =
            left < right && top < bottom && x >= left && x < right && y >= top && y < bottom

        fun set(l: Float, t: Float, r: Float, b: Float) {
            left = l
            top = t
            right = r
            bottom = b
        }
    }

    enum class HandleType {
        NONE,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        LEFT,
        TOP,
        RIGHT,
        BOTTOM,
        INSIDE_MOVE
    }

    fun detectHitHandle(
        x: Float,
        y: Float,
        rect: RectF,
        touchRadiusPx: Float
    ): HandleType {
        return detectHitHandle(
            x = x,
            y = y,
            rect = BoundsF(rect.left, rect.top, rect.right, rect.bottom),
            touchRadiusPx = touchRadiusPx
        )
    }

    fun detectHitHandle(
        x: Float,
        y: Float,
        rect: BoundsF,
        touchRadiusPx: Float
    ): HandleType {
        if (!x.isFinite() || !y.isFinite() || !touchRadiusPx.isFinite() || touchRadiusPx < 0f) {
            return HandleType.NONE
        }
        if (rect.width() <= 0f || rect.height() <= 0f) return HandleType.NONE

        val handles = listOf(
            HandleType.TOP_LEFT to Pair(rect.left, rect.top),
            HandleType.TOP_RIGHT to Pair(rect.right, rect.top),
            HandleType.BOTTOM_LEFT to Pair(rect.left, rect.bottom),
            HandleType.BOTTOM_RIGHT to Pair(rect.right, rect.bottom),
            HandleType.RIGHT to Pair(rect.right, rect.centerY()),
            HandleType.BOTTOM to Pair(rect.centerX(), rect.bottom),
            HandleType.LEFT to Pair(rect.left, rect.centerY()),
            HandleType.TOP to Pair(rect.centerX(), rect.top)
        )

        var closest = HandleType.NONE
        var minDist = touchRadiusPx
        for ((type, pt) in handles) {
            val d = hypot(x - pt.first, y - pt.second)
            if (d <= minDist) {
                minDist = d
                closest = type
            }
        }
        if (closest != HandleType.NONE) return closest

        return if (rect.contains(x, y)) HandleType.INSIDE_MOVE else HandleType.NONE
    }

    fun applyResize(
        rect: RectF,
        handle: HandleType,
        dx: Float,
        dy: Float,
        minSizePx: Float,
        boundsWidth: Float,
        boundsHeight: Float
    ) {
        val bounds = BoundsF(rect.left, rect.top, rect.right, rect.bottom)
        applyResize(bounds, handle, dx, dy, minSizePx, boundsWidth, boundsHeight)
        rect.set(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    fun applyResize(
        rect: BoundsF,
        handle: HandleType,
        dx: Float,
        dy: Float,
        minSizePx: Float,
        boundsWidth: Float,
        boundsHeight: Float
    ) {
        if (!dx.isFinite() || !dy.isFinite() || !minSizePx.isFinite() ||
            !boundsWidth.isFinite() || !boundsHeight.isFinite() ||
            boundsWidth <= 0f || boundsHeight <= 0f
        ) {
            return
        }

        val safeMinW = minSizePx.coerceIn(1f, boundsWidth)
        val safeMinH = minSizePx.coerceIn(1f, boundsHeight)

        // Normalize rect within current bounds first so degenerate/out-of-bounds rects can never invert
        val normLeft = minOf(rect.left, rect.right).coerceIn(0f, boundsWidth)
        val normRight = maxOf(rect.left, rect.right).coerceIn(normLeft, boundsWidth)
        val normTop = minOf(rect.top, rect.bottom).coerceIn(0f, boundsHeight)
        val normBottom = maxOf(rect.top, rect.bottom).coerceIn(normTop, boundsHeight)
        rect.set(normLeft, normTop, normRight, normBottom)

        fun safeClamp(value: Float, minVal: Float, maxVal: Float): Float {
            return if (minVal <= maxVal) value.coerceIn(minVal, maxVal) else minVal
        }

        when (handle) {
            HandleType.INSIDE_MOVE -> {
                val w = rect.width().coerceIn(0f, boundsWidth)
                val h = rect.height().coerceIn(0f, boundsHeight)
                val newLeft = safeClamp(rect.left + dx, 0f, (boundsWidth - w).coerceAtLeast(0f))
                val newTop = safeClamp(rect.top + dy, 0f, (boundsHeight - h).coerceAtLeast(0f))
                rect.set(newLeft, newTop, newLeft + w, newTop + h)
            }
            HandleType.LEFT -> {
                val maxLeft = (rect.right - safeMinW).coerceIn(0f, boundsWidth)
                rect.left = safeClamp(rect.left + dx, 0f, maxLeft)
            }
            HandleType.RIGHT -> {
                val minRight = (rect.left + safeMinW).coerceIn(0f, boundsWidth)
                rect.right = safeClamp(rect.right + dx, minRight, boundsWidth)
            }
            HandleType.TOP -> {
                val maxTop = (rect.bottom - safeMinH).coerceIn(0f, boundsHeight)
                rect.top = safeClamp(rect.top + dy, 0f, maxTop)
            }
            HandleType.BOTTOM -> {
                val minBottom = (rect.top + safeMinH).coerceIn(0f, boundsHeight)
                rect.bottom = safeClamp(rect.bottom + dy, minBottom, boundsHeight)
            }
            HandleType.TOP_LEFT -> {
                val maxLeft = (rect.right - safeMinW).coerceIn(0f, boundsWidth)
                val maxTop = (rect.bottom - safeMinH).coerceIn(0f, boundsHeight)
                rect.left = safeClamp(rect.left + dx, 0f, maxLeft)
                rect.top = safeClamp(rect.top + dy, 0f, maxTop)
            }
            HandleType.TOP_RIGHT -> {
                val minRight = (rect.left + safeMinW).coerceIn(0f, boundsWidth)
                val maxTop = (rect.bottom - safeMinH).coerceIn(0f, boundsHeight)
                rect.right = safeClamp(rect.right + dx, minRight, boundsWidth)
                rect.top = safeClamp(rect.top + dy, 0f, maxTop)
            }
            HandleType.BOTTOM_LEFT -> {
                val maxLeft = (rect.right - safeMinW).coerceIn(0f, boundsWidth)
                val minBottom = (rect.top + safeMinH).coerceIn(0f, boundsHeight)
                rect.left = safeClamp(rect.left + dx, 0f, maxLeft)
                rect.bottom = safeClamp(rect.bottom + dy, minBottom, boundsHeight)
            }
            HandleType.BOTTOM_RIGHT -> {
                val minRight = (rect.left + safeMinW).coerceIn(0f, boundsWidth)
                val minBottom = (rect.top + safeMinH).coerceIn(0f, boundsHeight)
                rect.right = safeClamp(rect.right + dx, minRight, boundsWidth)
                rect.bottom = safeClamp(rect.bottom + dy, minBottom, boundsHeight)
            }
            HandleType.NONE -> Unit
        }
    }
}
