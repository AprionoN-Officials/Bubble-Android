package com.bubble.screentools.ui.overlay

import android.graphics.Path
import android.graphics.RectF
import com.bubble.screentools.model.SelectionShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Unified geometry & Path builder shared by SelectionOverlayView, Live Selection Preview,
 * and BitmapCropHelper so visual selection boundaries always match actual crop/mask regions.
 */
object SelectionPathBuilder {

    fun buildShapePath(
        bounds: RectF,
        shape: SelectionShape,
        cornerRadiusPx: Float,
        curvatureLevel: Float,
        freeformPath: Path? = null
    ): Path {
        val path = Path()
        if (bounds.width() <= 0f || bounds.height() <= 0f) return path

        when (shape) {
            SelectionShape.RECTANGLE -> {
                path.addRect(bounds, Path.Direction.CW)
            }
            SelectionShape.ROUNDED_RECTANGLE -> {
                val maxR = min(bounds.width(), bounds.height()) * 0.5f
                val r = cornerRadiusPx.coerceIn(0f, maxR)
                path.addRoundRect(bounds, r, r, Path.Direction.CW)
            }
            SelectionShape.ELLIPSE -> {
                path.addOval(bounds, Path.Direction.CW)
            }
            SelectionShape.FREEFORM -> {
                if (freeformPath != null && !freeformPath.isEmpty) {
                    path.set(freeformPath)
                } else {
                    val maxR = min(bounds.width(), bounds.height()) * 0.5f
                    val r = cornerRadiusPx.coerceIn(0f, maxR)
                    path.addRoundRect(bounds, r, r, Path.Direction.CW)
                }
            }
            SelectionShape.CUSTOM_CURVED -> {
                buildSuperellipsePath(path, bounds, curvatureLevel)
            }
        }
        return path
    }

    /**
     * Generates a smooth superellipse (squircle / custom curved frame) where:
     * - curvature = 0.0 -> sharp rectangle (exponent ~12)
     * - curvature = 0.55 -> expressive organic squircle (exponent ~3.6)
     * - curvature = 1.0 -> smooth oval/curved loop (exponent = 2.0)
     */
    fun buildSuperellipsePath(outPath: Path, bounds: RectF, curvatureLevel: Float, steps: Int = 72) {
        outPath.reset()
        val pts = computeSuperellipsePoints(
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
            curvatureLevel = curvatureLevel,
            steps = steps
        )
        if (pts.isEmpty()) return
        outPath.moveTo(pts[0].first, pts[0].second)
        for (i in 1 until pts.size) {
            outPath.lineTo(pts[i].first, pts[i].second)
        }
        outPath.close()
    }

    /**
     * Pure-Kotlin point generator for superellipse curves (directly unit-testable on JVM).
     */
    fun computeSuperellipsePoints(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        curvatureLevel: Float,
        steps: Int = 72
    ): List<Pair<Float, Float>> {
        if (!left.isFinite() || !top.isFinite() || !right.isFinite() ||
            !bottom.isFinite() || !curvatureLevel.isFinite()
        ) {
            return emptyList()
        }
        val w = right - left
        val h = bottom - top
        if (w <= 0f || h <= 0f || steps < 8) return emptyList()

        val cx = (left + right) * 0.5f
        val cy = (top + bottom) * 0.5f
        val a = w * 0.5f
        val b = h * 0.5f
        val c = curvatureLevel.coerceIn(0f, 1f)
        // Map curvature [0..1] -> superellipse exponent [12.0..2.0]
        val n = (2.0 + (1.0 - c) * (1.0 - c) * 10.0)
        val exp = 2.0 / n

        val points = ArrayList<Pair<Float, Float>>(steps)
        for (i in 0 until steps) {
            val theta = (2.0 * PI * i) / steps
            val cosT = cos(theta)
            val sinT = sin(theta)
            val x = cx + (a * sign(cosT) * abs(cosT).pow(exp)).toFloat()
            val y = cy + (b * sign(sinT) * abs(sinT).pow(exp)).toFloat()
            points.add(x to y)
        }
        return points
    }

    /**
     * Builds a smooth quadratic Bezier path from raw freeform touch points when [smooth] is true,
     * or a direct polyline when [smooth] is false.
     */
    fun buildFreeformPath(
        points: List<Pair<Float, Float>>,
        smooth: Boolean,
        close: Boolean
    ): Path {
        val path = Path()
        if (points.isEmpty()) return path
        path.moveTo(points[0].first, points[0].second)
        if (points.size == 1) return path

        if (!smooth || points.size < 3) {
            for (i in 1 until points.size) {
                path.lineTo(points[i].first, points[i].second)
            }
        } else {
            var prevX = points[0].first
            var prevY = points[0].second
            for (i in 1 until points.size) {
                val currX = points[i].first
                val currY = points[i].second
                val midX = (prevX + currX) * 0.5f
                val midY = (prevY + currY) * 0.5f
                path.quadTo(prevX, prevY, midX, midY)
                prevX = currX
                prevY = currY
            }
            path.lineTo(prevX, prevY)
        }

        if (close && points.size >= 3) {
            path.close()
        }
        return path
    }

    /**
     * Pure-Kotlin bounding box computation from freeform touch points.
     */
    fun computePointsBounds(points: List<Pair<Float, Float>>): SelectionGestureHandler.BoundsF {
        if (points.isEmpty()) return SelectionGestureHandler.BoundsF(0f, 0f, 0f, 0f)
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var hasValid = false
        for ((x, y) in points) {
            if (!x.isFinite() || !y.isFinite()) continue
            hasValid = true
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
        return if (hasValid) {
            SelectionGestureHandler.BoundsF(minX, minY, maxX, maxY)
        } else {
            SelectionGestureHandler.BoundsF(0f, 0f, 0f, 0f)
        }
    }
}
