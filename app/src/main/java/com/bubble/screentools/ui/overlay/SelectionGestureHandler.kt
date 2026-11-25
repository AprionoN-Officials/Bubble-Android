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
        when (handle) {
            HandleType.INSIDE_MOVE -> {
                val w = rect.width()
                val h = rect.height()
                val newLeft = (rect.left + dx).coerceIn(0f, (boundsWidth - w).coerceAtLeast(0f))
                val newTop = (rect.top + dy).coerceIn(0f, (boundsHeight - h).coerceAtLeast(0f))
                rect.set(newLeft, newTop, newLeft + w, newTop + h)
            }
            HandleType.LEFT -> rect.left = (rect.left + dx).coerceIn(0f, rect.right - minSizePx)
            HandleType.RIGHT -> rect.right = (rect.right + dx).coerceIn(rect.left + minSizePx, boundsWidth)
            HandleType.TOP -> rect.top = (rect.top + dy).coerceIn(0f, rect.bottom - minSizePx)
            HandleType.BOTTOM -> rect.bottom = (rect.bottom + dy).coerceIn(rect.top + minSizePx, boundsHeight)
            HandleType.TOP_LEFT -> {
                rect.left = (rect.left + dx).coerceIn(0f, rect.right - minSizePx)
                rect.top = (rect.top + dy).coerceIn(0f, rect.bottom - minSizePx)
            }
            HandleType.TOP_RIGHT -> {
                rect.right = (rect.right + dx).coerceIn(rect.left + minSizePx, boundsWidth)
                rect.top = (rect.top + dy).coerceIn(0f, rect.bottom - minSizePx)
            }
            HandleType.BOTTOM_LEFT -> {
                rect.left = (rect.left + dx).coerceIn(0f, rect.right - minSizePx)
                rect.bottom = (rect.bottom + dy).coerceIn(rect.top + minSizePx, boundsHeight)
            }
            HandleType.BOTTOM_RIGHT -> {
                rect.right = (rect.right + dx).coerceIn(rect.left + minSizePx, boundsWidth)
                rect.bottom = (rect.bottom + dy).coerceIn(rect.top + minSizePx, boundsHeight)
            }
            HandleType.NONE -> Unit
        }
    }
}
