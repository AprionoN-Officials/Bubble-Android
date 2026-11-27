package com.bubble.screentools.theme

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.model.SelectionColorMode
import com.bubble.screentools.model.SelectionConfig
import com.bubble.screentools.model.ThemeConfig
import com.bubble.screentools.model.ThemeMode
import com.bubble.screentools.model.ThemePreset
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout

/**
 * Central Theme Manager supporting Follow System, Light, Dark, AMOLED Black,
 * Android 12+ Material You Dynamic Color, Manual Seed Palettes, Theme Scope isolation,
 * and system reduced-motion respect.
 */
object ThemeManager {

    fun isDynamicColorSupported(): Boolean = DynamicColors.isDynamicColorAvailable()

    fun applyNightMode(themeMode: ThemeMode) {
        val nightMode = when (themeMode) {
            ThemeMode.FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK, ThemeMode.AMOLED_BLACK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    fun applyToActivityBeforeContent(activity: Activity, prefs: BubblePreferences) {
        val config = prefs.themeConfig
        applyNightMode(config.themeMode)
        if (config.applyToMainUi && config.useDynamicColor && isDynamicColorSupported()) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }

    fun wrapOverlayContext(base: Context, prefs: BubblePreferences): Context {
        val config = prefs.themeConfig
        return if (config.applyToOverlay && config.useDynamicColor && isDynamicColorSupported()) {
            DynamicColors.wrapContextIfAvailable(base)
        } else {
            base
        }
    }

    fun isCurrentlyDark(context: Context, config: ThemeConfig): Boolean {
        return when (config.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK, ThemeMode.AMOLED_BLACK -> true
            ThemeMode.FOLLOW_SYSTEM -> {
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    fun resolveDynamicPrimary(context: Context): Int? {
        if (!isDynamicColorSupported()) return null
        val wrapped = DynamicColors.wrapContextIfAvailable(context)
        val tv = TypedValue()
        val ok = wrapped.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tv, true)
        return if (ok && tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
            tv.data
        } else {
            null
        }
    }

    fun resolveSeedColor(context: Context, config: ThemeConfig): Int {
        if (config.useDynamicColor && isDynamicColorSupported()) {
            resolveDynamicPrimary(context)?.let { return it }
        }
        return when (config.preset) {
            ThemePreset.CUSTOM -> config.customSeedColor
            else -> ThemePaletteGenerator.parseHexColor(config.preset.seedHex)
                ?: Color.parseColor("#2196F3")
        }
    }

    fun resolvePalette(
        context: Context,
        config: ThemeConfig,
        respectMainScope: Boolean = true
    ): ThemePaletteGenerator.BubblePalette {
        val dark = isCurrentlyDark(context, config)
        val amoled = config.themeMode == ThemeMode.AMOLED_BLACK
        val seed = if (!respectMainScope || config.applyToMainUi) {
            resolveSeedColor(context, config)
        } else {
            Color.parseColor(ThemePreset.BUBBLE_BLUE.seedHex)
        }
        return ThemePaletteGenerator.generate(seed, isDark = dark, amoledBlack = amoled)
    }

    fun resolveOverlayAccentColor(context: Context, prefs: BubblePreferences): Int {
        val config = prefs.themeConfig
        if (!config.applyToOverlay) {
            return Color.parseColor(ThemePreset.BUBBLE_BLUE.seedHex)
        }
        return resolvePalette(context, config, respectMainScope = false).primary
    }

    /**
     * Resolves selection border color without ever overwriting a user's explicit manual selection color.
     */
    fun resolveSelectionBorderColor(
        context: Context,
        selectionConfig: SelectionConfig,
        themeConfig: ThemeConfig
    ): Int {
        if (selectionConfig.colorMode == SelectionColorMode.CUSTOM) {
            return selectionConfig.borderColor
        }
        return if (themeConfig.applyToSelection) {
            resolvePalette(context, themeConfig, respectMainScope = false).primary
        } else {
            selectionConfig.borderColor
        }
    }

    fun areAnimationsAllowed(context: Context, userEnabled: Boolean = true): Boolean {
        if (!userEnabled) return false
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        return scale > 0f
    }

    /**
     * Applies the resolved Material 3 Expressive palette cleanly across Material views
     * (surface, cards, tabs, switches, sliders, buttons) while preserving plain minimal surfaces.
     */
    fun applyPaletteToHierarchy(
        root: View,
        palette: ThemePaletteGenerator.BubblePalette
    ) {
        root.setBackgroundColor(palette.surface)
        styleViewTree(root, palette)
    }

    private fun styleViewTree(view: View, palette: ThemePaletteGenerator.BubblePalette) {
        when (view) {
            is MaterialCardView -> {
                if (view.tag == "tonal_card") {
                    view.setCardBackgroundColor(palette.primaryContainer)
                    view.strokeWidth = 0
                } else if (view.tag != "preview_canvas") {
                    view.setCardBackgroundColor(palette.surfaceContainer)
                    view.strokeColor = palette.outlineVariant
                }
            }
            is TabLayout -> {
                view.setBackgroundColor(palette.surface)
                view.setSelectedTabIndicatorColor(palette.primary)
                view.setTabTextColors(palette.onSurfaceVariant, palette.primary)
            }
            is MaterialSwitch -> {
                val trackStates = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                )
                view.trackTintList = ColorStateList(
                    trackStates,
                    intArrayOf(palette.primary, palette.surfaceContainerHigh)
                )
                view.thumbTintList = ColorStateList(
                    trackStates,
                    intArrayOf(palette.onPrimary, palette.outline)
                )
                view.setTextColor(palette.onSurface)
            }
            is Slider -> {
                view.trackActiveTintList = ColorStateList.valueOf(palette.primary)
                view.trackInactiveTintList = ColorStateList.valueOf(palette.outlineVariant)
                view.thumbTintList = ColorStateList.valueOf(palette.primary)
            }
            is MaterialButton -> {
                when (view.tag) {
                    "btn_primary" -> {
                        view.backgroundTintList = ColorStateList.valueOf(palette.primary)
                        view.setTextColor(palette.onPrimary)
                        view.iconTint = ColorStateList.valueOf(palette.onPrimary)
                    }
                    "btn_tonal" -> {
                        view.backgroundTintList = ColorStateList.valueOf(palette.primaryContainer)
                        view.setTextColor(palette.onPrimaryContainer)
                        view.iconTint = ColorStateList.valueOf(palette.onPrimaryContainer)
                    }
                    "btn_outlined" -> {
                        view.strokeColor = ColorStateList.valueOf(palette.outline)
                        view.setTextColor(palette.primary)
                        view.iconTint = ColorStateList.valueOf(palette.primary)
                    }
                    "btn_text" -> {
                        view.setTextColor(palette.primary)
                        view.iconTint = ColorStateList.valueOf(palette.primary)
                    }
                }
            }
            is TextView -> {
                when (view.tag) {
                    "section_title" -> view.setTextColor(palette.primary)
                    "text_primary" -> view.setTextColor(palette.onSurface)
                    "text_secondary" -> view.setTextColor(palette.onSurfaceVariant)
                }
            }
        }

        if (view is ViewGroup && view.tag != "skip_theme_children") {
            for (i in 0 until view.childCount) {
                styleViewTree(view.getChildAt(i), palette)
            }
        }
    }

    fun describeActiveVariant(context: Context, config: ThemeConfig, isDebug: Boolean): String {
        val parts = mutableListOf<String>()
        if (config.useDynamicColor && isDynamicColorSupported()) {
            parts.add("Material You")
        } else {
            parts.add(config.preset.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })
        }
        if (config.themeMode == ThemeMode.AMOLED_BLACK) {
            parts.add("AMOLED")
        }
        if (isDebug) {
            parts.add("Debug")
        }
        return parts.joinToString(" • ")
    }
}
