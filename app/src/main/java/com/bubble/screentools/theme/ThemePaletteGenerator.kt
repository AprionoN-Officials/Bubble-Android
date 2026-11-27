package com.bubble.screentools.theme

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure-Kotlin Material 3 tonal palette generator.
 * Builds a complete, accessible color scheme (primary, onPrimary, containers, surfaces, outlines)
 * from any ARGB seed color for Light, Dark, and AMOLED Black modes without external dependencies.
 */
object ThemePaletteGenerator {

    data class BubblePalette(
        val primary: Int,
        val onPrimary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val secondary: Int,
        val secondaryContainer: Int,
        val onSecondaryContainer: Int,
        val surface: Int,
        val surfaceContainer: Int,
        val surfaceContainerHigh: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val outline: Int,
        val outlineVariant: Int,
        val isDark: Boolean
    )

    fun generate(seedArgb: Int, isDark: Boolean, amoledBlack: Boolean = false): BubblePalette {
        val hsl = rgbToHsl(seedArgb)
        val hue = hsl[0]
        val isMonochrome = hsl[1] < 0.08f
        val sat = if (isMonochrome) 0.02f else hsl[1].coerceIn(0.45f, 0.88f)
        val secHue = (hue + 24f) % 360f
        val secSat = if (isMonochrome) 0.02f else (sat * 0.55f).coerceIn(0.18f, 0.45f)

        return if (!isDark) {
            val primary = ensureAccessiblePrimary(
                hue = hue,
                sat = sat,
                initialLightness = if (isMonochrome) 0.24f else 0.46f,
                isDark = false
            )
            val onPrimary = bestOnColor(primary)
            val primaryContainer = hslToRgb(hue, if (isMonochrome) 0.04f else (sat * 0.72f), 0.90f)
            val onPrimaryContainer = hslToRgb(hue, if (isMonochrome) 0.05f else sat, 0.16f)
            val secondary = hslToRgb(secHue, secSat, 0.42f)
            val secondaryContainer = hslToRgb(secHue, secSat, 0.91f)
            val onSecondaryContainer = hslToRgb(secHue, secSat, 0.16f)
            val surface = hslToRgb(hue, if (isMonochrome) 0f else 0.08f, 0.985f)
            val surfaceContainer = hslToRgb(hue, if (isMonochrome) 0f else 0.12f, 0.955f)
            val surfaceContainerHigh = hslToRgb(hue, if (isMonochrome) 0f else 0.14f, 0.92f)
            val onSurface = 0xFF1A1C1E.toInt()
            val onSurfaceVariant = 0xFF525860.toInt()
            val outline = hslToRgb(hue, if (isMonochrome) 0f else 0.10f, 0.58f)
            val outlineVariant = hslToRgb(hue, if (isMonochrome) 0f else 0.10f, 0.82f)

            BubblePalette(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = onSecondaryContainer,
                surface = surface,
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceContainerHigh,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                outlineVariant = outlineVariant,
                isDark = false
            )
        } else {
            val primary = ensureAccessiblePrimary(
                hue = hue,
                sat = if (isMonochrome) 0.02f else (sat * 0.88f),
                initialLightness = if (isMonochrome) 0.84f else 0.68f,
                isDark = true
            )
            val onPrimary = bestOnColor(primary)
            val primaryContainer = hslToRgb(hue, if (isMonochrome) 0.05f else (sat * 0.65f), 0.24f)
            val onPrimaryContainer = hslToRgb(hue, if (isMonochrome) 0.04f else (sat * 0.78f), 0.92f)
            val secondary = hslToRgb(secHue, secSat, 0.72f)
            val secondaryContainer = hslToRgb(secHue, secSat, 0.24f)
            val onSecondaryContainer = hslToRgb(secHue, secSat, 0.90f)
            val surface = if (amoledBlack) 0xFF000000.toInt() else hslToRgb(hue, if (isMonochrome) 0f else 0.10f, 0.08f)
            val surfaceContainer = if (amoledBlack) 0xFF0B0C0E.toInt() else hslToRgb(hue, if (isMonochrome) 0f else 0.12f, 0.12f)
            val surfaceContainerHigh = if (amoledBlack) 0xFF16181C.toInt() else hslToRgb(hue, if (isMonochrome) 0f else 0.14f, 0.17f)
            val onSurface = 0xFFE6E8EC.toInt()
            val onSurfaceVariant = 0xFFA6ADB8.toInt()
            val outline = hslToRgb(hue, if (isMonochrome) 0f else 0.10f, 0.44f)
            val outlineVariant = if (amoledBlack) 0xFF262930.toInt() else hslToRgb(hue, if (isMonochrome) 0f else 0.10f, 0.26f)

            BubblePalette(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondary,
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = onSecondaryContainer,
                surface = surface,
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceContainerHigh,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant,
                outline = outline,
                outlineVariant = outlineVariant,
                isDark = true
            )
        }
    }

    fun parseHexColor(hex: String): Int? {
        val cleaned = hex.trim().removePrefix("#")
        return when (cleaned.length) {
            6 -> cleaned.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
            8 -> cleaned.toLongOrNull(16)?.toInt()
            else -> null
        }
    }

    fun formatHex(argb: Int, includeAlpha: Boolean = false): String {
        return if (includeAlpha) {
            String.format(Locale.US, "#%08X", argb)
        } else {
            String.format(Locale.US, "#%06X", argb and 0xFFFFFF)
        }
    }

    fun contrastRatio(foregroundArgb: Int, backgroundArgb: Int): Double {
        val l1 = relativeLuminance(foregroundArgb)
        val l2 = relativeLuminance(backgroundArgb)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun bestOnColor(bgArgb: Int): Int {
        val whiteContrast = contrastRatio(0xFFFFFFFF.toInt(), bgArgb)
        val darkContrast = contrastRatio(0xFF101216.toInt(), bgArgb)
        return if (whiteContrast >= darkContrast) 0xFFFFFFFF.toInt() else 0xFF101216.toInt()
    }

    /**
     * Nudges primary lightness slightly when a custom hue (e.g. vivid orange/vermilion or cyan)
     * lands in the mid-luminance WCAG dead zone so primary button labels always achieve >= 4.5:1.
     */
    private fun ensureAccessiblePrimary(
        hue: Float,
        sat: Float,
        initialLightness: Float,
        isDark: Boolean
    ): Int {
        var l = initialLightness.coerceIn(0.15f, 0.90f)
        var candidate = hslToRgb(hue, sat, l)
        var attempts = 0
        while (contrastRatio(bestOnColor(candidate), candidate) < 4.55 && attempts < 14) {
            l = if (isDark) (l + 0.02f).coerceAtMost(0.90f) else (l - 0.02f).coerceAtLeast(0.20f)
            candidate = hslToRgb(hue, sat, l)
            attempts++
        }
        return candidate
    }

    fun withAlpha(argb: Int, alphaFraction: Float): Int {
        val a = (alphaFraction.coerceIn(0f, 1f) * 255f).roundToInt()
        return (a shl 24) or (argb and 0x00FFFFFF)
    }

    private fun relativeLuminance(argb: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = channel((argb ushr 16) and 0xFF)
        val g = channel((argb ushr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun rgbToHsl(argb: Int): FloatArray {
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val maxVal = max(r, max(g, b))
        val minVal = min(r, min(g, b))
        val delta = maxVal - minVal
        val l = (maxVal + minVal) / 2f
        val denom = (1f - abs(2f * l - 1f)).coerceAtLeast(1e-6f)
        val s = if (delta == 0f) 0f else (delta / denom).coerceIn(0f, 1f)
        val h = when {
            delta == 0f -> 0f
            maxVal == r -> 60f * (((g - b) / delta) % 6f)
            maxVal == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        return floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    private fun hslToRgb(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
