package com.bubble.screentools.ui.main.tabs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.model.ActionMode
import com.bubble.screentools.model.ThemeMode
import com.bubble.screentools.model.ThemePreset
import com.bubble.screentools.theme.ThemeManager
import com.bubble.screentools.theme.ThemePaletteGenerator
import com.bubble.screentools.ui.main.UiKit
import com.bubble.screentools.ui.overlay.ActionPanelView

/**
 * Tab 5 — Appearance / Tampilan:
 * - Live Theme Preview Card (Primary, Tonal, Outlined buttons, Switch, Selection Border, Card, Action Panel, Text, Surface)
 * - Theme Modes (Follow System / Light / Dark / AMOLED Black)
 * - Material You Dynamic Color toggle (Android 12+ with graceful API 24–30 fallback explanation)
 * - Manual Color Themes (Bubble, Rose, Lavender, Blue, Green, Amber, Monochrome, Custom HEX/RGB Picker)
 * - Theme Scope toggles (Main UI / Bubble Overlay / Selection Border without clobbering manual selection colors)
 */
object AppearanceTabBuilder {

    interface Callbacks {
        fun onThemeChanged(recreateRequired: Boolean)
    }

    fun render(
        context: Context,
        container: LinearLayout,
        preferences: BubblePreferences,
        palette: ThemePaletteGenerator.BubblePalette,
        callbacks: Callbacks
    ) {
        container.removeAllViews()
        val dp = { v: Float -> UiKit.dp(context, v) }
        val config = preferences.themeConfig

        // 1. Live Theme Preview Card
        val (prevCard, prevBox) = UiKit.createExpressiveCard(context, palette)
        prevBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_theme_preview), palette))

        val surfacePreview = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = "skip_theme_children"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16f).toFloat()
                setColor(palette.surface)
                setStroke(dp(1f), palette.outlineVariant)
            }
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
        }

        // Primary / Tonal / Outlined buttons row
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(10f) }
        }
        btnRow.addView(
            UiKit.createPillButton(context, context.getString(R.string.preview_primary_btn), UiKit.ButtonVariant.PRIMARY, palette) {}
                .apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4f) } }
        )
        btnRow.addView(
            UiKit.createPillButton(context, context.getString(R.string.preview_tonal_btn), UiKit.ButtonVariant.TONAL, palette) {}
                .apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4f); marginEnd = dp(4f) } }
        )
        btnRow.addView(
            UiKit.createPillButton(context, context.getString(R.string.preview_outlined_btn), UiKit.ButtonVariant.OUTLINED, palette) {}
                .apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4f) } }
        )
        surfacePreview.addView(btnRow)

        // Material 3 Switch preview
        surfacePreview.addView(
            UiKit.createSwitchRow(context, context.getString(R.string.preview_switch_label), true, palette) {}
        )

        // Selection Border + Surface Card preview
        val borderPreview = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12f).toFloat()
                setColor(palette.surfaceContainer)
                setStroke(dp(2.5f), ThemeManager.resolveSelectionBorderColor(context, preferences.selectionConfig, config))
            }
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6f); bottomMargin = dp(10f) }
        }
        borderPreview.addView(TextView(context).apply {
            text = context.getString(R.string.app_name)
            textSize = 14f
            setTextColor(palette.onSurface)
        })
        borderPreview.addView(TextView(context).apply {
            text = context.getString(R.string.app_tagline)
            textSize = 11.5f
            setTextColor(palette.onSurfaceVariant)
        })
        surfacePreview.addView(borderPreview)

        // Action Panel mini preview
        val miniPanel = ActionPanelView(context).apply {
            val accent = ThemeManager.resolveOverlayAccentColor(context, preferences)
            render(
                config = preferences.actionPanelConfig.copy(position = com.bubble.screentools.model.ActionPanelPosition.BOTTOM),
                activeMode = ActionMode.TRANSLATE,
                themeAccentColor = accent
            )
        }
        surfacePreview.addView(miniPanel)
        prevBox.addView(surfacePreview)
        container.addView(prevCard)

        // 2. Theme Mode & Dynamic Color Card
        val (modeCard, modeBox) = UiKit.createExpressiveCard(context, palette)
        modeBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_theme_mode), palette))

        modeBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    ThemeMode.FOLLOW_SYSTEM to context.getString(R.string.theme_follow_system),
                    ThemeMode.LIGHT to context.getString(R.string.theme_light),
                    ThemeMode.DARK to context.getString(R.string.theme_dark),
                    ThemeMode.AMOLED_BLACK to context.getString(R.string.theme_amoled)
                ),
                selected = config.themeMode,
                palette = palette,
                scrollable = true
            ) { mode ->
                preferences.themeConfig = config.copy(themeMode = mode)
                ThemeManager.applyNightMode(mode)
                callbacks.onThemeChanged(recreateRequired = true)
            }
        )

        val dynamicSupported = ThemeManager.isDynamicColorSupported()
        modeBox.addView(
            UiKit.createSwitchRow(
                context = context,
                label = context.getString(R.string.switch_dynamic_color),
                checked = config.useDynamicColor && dynamicSupported,
                palette = palette,
                subtitle = if (!dynamicSupported) context.getString(R.string.dynamic_color_unsupported) else null
            ) { checked ->
                preferences.themeConfig = config.copy(useDynamicColor = checked)
                callbacks.onThemeChanged(recreateRequired = dynamicSupported)
            }
        )
        container.addView(modeCard)

        // 3. Manual Color Themes Card
        val (palCard, palBox) = UiKit.createExpressiveCard(context, palette)
        palBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_manual_palette), palette))

        palBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    ThemePreset.BUBBLE_BLUE to context.getString(R.string.palette_bubble_blue),
                    ThemePreset.ROSE to context.getString(R.string.palette_rose),
                    ThemePreset.LAVENDER to context.getString(R.string.palette_lavender),
                    ThemePreset.BLUE to context.getString(R.string.palette_blue),
                    ThemePreset.GREEN to context.getString(R.string.palette_green),
                    ThemePreset.AMBER to context.getString(R.string.palette_amber),
                    ThemePreset.MONOCHROME to context.getString(R.string.palette_mono),
                    ThemePreset.CUSTOM to context.getString(R.string.palette_custom)
                ),
                selected = config.preset,
                palette = palette
            ) { preset ->
                if (preset == ThemePreset.CUSTOM) {
                    UiKit.showHexRgbColorPickerDialog(
                        context,
                        context.getString(R.string.section_manual_palette),
                        config.customSeedColor,
                        palette
                    ) { picked ->
                        preferences.themeConfig = config.copy(
                            useDynamicColor = false,
                            preset = ThemePreset.CUSTOM,
                            customSeedColor = picked
                        )
                        callbacks.onThemeChanged(recreateRequired = false)
                    }
                } else {
                    preferences.themeConfig = config.copy(
                        useDynamicColor = false,
                        preset = preset
                    )
                    callbacks.onThemeChanged(recreateRequired = false)
                }
            }
        )

        palBox.addView(
            UiKit.createPillButton(
                context,
                "${context.getString(R.string.btn_pick_custom_hex)} (${ThemePaletteGenerator.formatHex(config.customSeedColor)})",
                UiKit.ButtonVariant.OUTLINED,
                palette
            ) {
                UiKit.showHexRgbColorPickerDialog(
                    context,
                    context.getString(R.string.section_manual_palette),
                    config.customSeedColor,
                    palette
                ) { picked ->
                    preferences.themeConfig = config.copy(
                        useDynamicColor = false,
                        preset = ThemePreset.CUSTOM,
                        customSeedColor = picked
                    )
                    callbacks.onThemeChanged(recreateRequired = false)
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(8f) }
            }
        )
        container.addView(palCard)

        // 4. Theme Scope Card
        val (scopeCard, scopeBox) = UiKit.createExpressiveCard(context, palette)
        scopeBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_theme_scope), palette))
        scopeBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.scope_main_ui), config.applyToMainUi, palette) {
            preferences.themeConfig = config.copy(applyToMainUi = it)
            callbacks.onThemeChanged(recreateRequired = false)
        })
        scopeBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.scope_overlay), config.applyToOverlay, palette) {
            preferences.themeConfig = config.copy(applyToOverlay = it)
            callbacks.onThemeChanged(recreateRequired = false)
        })
        scopeBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.scope_selection), config.applyToSelection, palette) {
            preferences.themeConfig = config.copy(applyToSelection = it)
            callbacks.onThemeChanged(recreateRequired = false)
        })
        scopeBox.addView(
            UiKit.createPillButton(context, context.getString(R.string.btn_reset_default), UiKit.ButtonVariant.TEXT, palette) {
                preferences.resetThemeConfig()
                ThemeManager.applyNightMode(ThemeMode.FOLLOW_SYSTEM)
                callbacks.onThemeChanged(recreateRequired = true)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.END
                    topMargin = dp(8f)
                }
            }
        )
        container.addView(scopeCard)
    }
}
