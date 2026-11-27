package com.bubble.screentools.ui.main.tabs

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.model.SelectionBorderStyle
import com.bubble.screentools.model.SelectionColorMode
import com.bubble.screentools.model.SelectionConfig
import com.bubble.screentools.model.SelectionShape
import com.bubble.screentools.theme.ThemeManager
import com.bubble.screentools.theme.ThemePaletteGenerator
import com.bubble.screentools.ui.main.UiKit
import com.bubble.screentools.ui.overlay.SelectionOverlayView

/**
 * Tab 3 — Selection / Seleksi:
 * - Interactive Live Selection Preview running the ACTUAL SelectionOverlayView over sample screen content
 *   (supports live drag, resize, AND Freeform path drawing right inside the preview!)
 * - All 5 shapes: Rectangle, Rounded Rectangle, Ellipse, Freeform, Custom Curved
 * - All 4 border styles: Solid, Dashed, Dotted, Dash-Dot
 * - Material You Accent vs Custom HEX/RGB border color, thickness, corner radius, curvature, fill opacity, outer dim
 * - Handle visibility/size/color, smooth freeform path, edge smoothing, animation, min selection size, Reset to Default
 */
object SelectionTabBuilder {

    private val PRESET_BORDER_COLORS = listOf(
        Color.parseColor("#2196F3"),
        Color.parseColor("#E11D48"),
        Color.parseColor("#7C3AED"),
        Color.parseColor("#16A34A"),
        Color.parseColor("#F59E0B"),
        Color.parseColor("#06B6D4"),
        Color.parseColor("#FFFFFF")
    )

    private val PRESET_HANDLE_COLORS = listOf(
        Color.WHITE,
        Color.parseColor("#2196F3"),
        Color.parseColor("#F59E0B"),
        Color.parseColor("#10B981"),
        Color.parseColor("#18181B")
    )

    fun render(
        context: Context,
        container: LinearLayout,
        preferences: BubblePreferences,
        palette: ThemePaletteGenerator.BubblePalette,
        onFullRebindRequested: () -> Unit
    ) {
        container.removeAllViews()
        val dp = { v: Float -> UiKit.dp(context, v) }
        var config = preferences.selectionConfig

        // 1. Live Interactive Selection Preview Card
        val (previewCard, previewBox) = UiKit.createExpressiveCard(context, palette)
        previewBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_selection_preview), palette))
        previewBox.addView(UiKit.createSubtitle(context, context.getString(R.string.selection_preview_hint), palette))

        val stage = FrameLayout(context).apply {
            tag = "skip_theme_children"
            clipToOutline = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16f).toFloat()
                setColor(if (palette.isDark) Color.parseColor("#161922") else Color.parseColor("#F4F7FB"))
                setStroke(dp(1f), palette.outlineVariant)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(230f)
            )
        }

        // Simulated screen content beneath the live SelectionOverlayView
        val sampleContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(18f), dp(18f), dp(18f))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        sampleContent.addView(TextView(context).apply {
            text = "KYOTO TRAVEL GUIDE"
            textSize = 10.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.primary)
        })
        sampleContent.addView(TextView(context).apply {
            text = "Exploring Autumn Temples"
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.onSurface)
            setPadding(0, dp(4f), 0, dp(6f))
        })
        sampleContent.addView(TextView(context).apply {
            text = "Morning walks through ancient cedar forests offer quiet paths and vibrant red maple leaves across the hills.\n\n京都の秋は美しい紅葉と静かな寺院が魅力です。"
            textSize = 12.5f
            setLineSpacing(dp(3f).toFloat(), 1f)
            setTextColor(palette.onSurfaceVariant)
        })
        stage.addView(sampleContent)

        val liveSelectionView = SelectionOverlayView(context).apply {
            isPreviewMode = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        stage.addView(liveSelectionView)
        previewBox.addView(stage)
        container.addView(previewCard)

        fun applyLivePreview(updated: SelectionConfig) {
            config = updated
            preferences.selectionConfig = updated
            val resolvedBorder = ThemeManager.resolveSelectionBorderColor(context, updated, preferences.themeConfig)
            val allowAnim = ThemeManager.areAnimationsAllowed(context, updated.animationEnabled)
            liveSelectionView.applyConfig(updated, resolvedBorder, allowAnim)
        }
        applyLivePreview(config)

        // 2. Shape & Border Style Card
        val (shapeCard, shapeBox) = UiKit.createExpressiveCard(context, palette)
        shapeBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_selection_shape), palette))

        shapeBox.addView(UiKit.createLabel(context, context.getString(R.string.label_default_shape), palette))
        shapeBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    SelectionShape.RECTANGLE to context.getString(R.string.shape_rectangle),
                    SelectionShape.ROUNDED_RECTANGLE to context.getString(R.string.shape_rounded_rect),
                    SelectionShape.ELLIPSE to context.getString(R.string.shape_ellipse),
                    SelectionShape.FREEFORM to context.getString(R.string.shape_freeform),
                    SelectionShape.CUSTOM_CURVED to context.getString(R.string.shape_custom_curved)
                ),
                selected = config.shape,
                palette = palette,
                scrollable = true
            ) { shape ->
                applyLivePreview(config.copy(shape = shape))
            }
        )

        shapeBox.addView(UiKit.createLabel(context, context.getString(R.string.label_border_style), palette))
        shapeBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    SelectionBorderStyle.SOLID to context.getString(R.string.border_solid),
                    SelectionBorderStyle.DASHED to context.getString(R.string.border_dashed),
                    SelectionBorderStyle.DOTTED to context.getString(R.string.border_dotted),
                    SelectionBorderStyle.DASH_DOT to context.getString(R.string.border_dash_dot)
                ),
                selected = config.borderStyle,
                palette = palette,
                scrollable = true
            ) { style ->
                applyLivePreview(config.copy(borderStyle = style))
            }
        )
        container.addView(shapeCard)

        // 3. Appearance Card (Colors, Thickness, Corner Radius, Curvature, Fill & Outer Dim)
        val (appCard, appBox) = UiKit.createExpressiveCard(context, palette)
        appBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_selection_appearance), palette))

        appBox.addView(UiKit.createLabel(context, context.getString(R.string.label_border_color_mode), palette))
        appBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    SelectionColorMode.MATERIAL_YOU to context.getString(R.string.color_mode_material_you),
                    SelectionColorMode.CUSTOM to context.getString(R.string.color_mode_custom)
                ),
                selected = config.colorMode,
                palette = palette
            ) { mode ->
                applyLivePreview(config.copy(colorMode = mode))
                onFullRebindRequested()
            }
        )

        if (config.colorMode == SelectionColorMode.CUSTOM) {
            appBox.addView(
                UiKit.createColorSwatchesRow(context, PRESET_BORDER_COLORS, config.borderColor, palette) { chosen ->
                    applyLivePreview(config.copy(colorMode = SelectionColorMode.CUSTOM, borderColor = chosen))
                    onFullRebindRequested()
                }
            )
            appBox.addView(
                UiKit.createPillButton(
                    context,
                    "${context.getString(R.string.btn_pick_custom_hex)} (${ThemePaletteGenerator.formatHex(config.borderColor)})",
                    UiKit.ButtonVariant.OUTLINED,
                    palette
                ) {
                    UiKit.showHexRgbColorPickerDialog(
                        context,
                        context.getString(R.string.label_border_color_mode),
                        config.borderColor,
                        palette
                    ) { picked ->
                        applyLivePreview(config.copy(colorMode = SelectionColorMode.CUSTOM, borderColor = picked))
                        onFullRebindRequested()
                    }
                }
            )
        }

        appBox.addView(
            UiKit.createSliderRow(
                context,
                context.getString(R.string.slider_border_thickness_fmt, config.borderThicknessDp),
                1.5f, 8.0f, 0.5f, config.borderThicknessDp, palette,
                { context.getString(R.string.slider_border_thickness_fmt, it) }
            ) { applyLivePreview(config.copy(borderThicknessDp = it)) }
        )

        appBox.addView(
            UiKit.createSliderRow(
                context,
                context.getString(R.string.slider_corner_radius_fmt, config.cornerRadiusDp),
                0f, 36f, 2f, config.cornerRadiusDp, palette,
                { context.getString(R.string.slider_corner_radius_fmt, it) }
            ) { applyLivePreview(config.copy(cornerRadiusDp = it)) }
        )

        appBox.addView(
            UiKit.createSliderRow(
                context,
                context.getString(R.string.slider_curvature_fmt, (config.curvatureLevel * 100).toInt()),
                0f, 100f, 5f, config.curvatureLevel * 100f, palette,
                { context.getString(R.string.slider_curvature_fmt, it.toInt()) }
            ) { applyLivePreview(config.copy(curvatureLevel = it / 100f)) }
        )

        appBox.addView(
            UiKit.createSliderRow(
                context,
                context.getString(R.string.slider_fill_opacity_fmt, (config.fillOpacity * 100).toInt()),
                0f, 45f, 5f, config.fillOpacity * 100f, palette,
                { context.getString(R.string.slider_fill_opacity_fmt, it.toInt()) }
            ) { applyLivePreview(config.copy(fillOpacity = it / 100f)) }
        )

        appBox.addView(
            UiKit.createSliderRow(
                context,
                context.getString(R.string.slider_outer_dim_fmt, (config.outerDimOpacity * 100).toInt()),
                10f, 75f, 5f, config.outerDimOpacity * 100f, palette,
                { context.getString(R.string.slider_outer_dim_fmt, it.toInt()) }
            ) { applyLivePreview(config.copy(outerDimOpacity = it / 100f)) }
        )
        container.addView(appCard)

        // 4. Handles & Advanced Controls Card
        val (advCard, advBox) = UiKit.createExpressiveCard(context, palette)
        advBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_selection_advanced), palette))

        advBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_show_handles), config.showResizeHandles, palette) {
            applyLivePreview(config.copy(showResizeHandles = it))
        })

        advBox.addView(
            UiKit.createSliderRow(
                context,
                context.getString(R.string.slider_handle_size_fmt, config.handleSizeDp),
                4.0f, 13.0f, 0.5f, config.handleSizeDp, palette,
                { context.getString(R.string.slider_handle_size_fmt, it) }
            ) { applyLivePreview(config.copy(handleSizeDp = it)) }
        )

        advBox.addView(
            UiKit.createColorSwatchesRow(context, PRESET_HANDLE_COLORS, config.handleColor, palette) { c ->
                applyLivePreview(config.copy(handleColor = c))
                onFullRebindRequested()
            }
        )

        advBox.addView(
            UiKit.createSliderRow(
                context,
                context.getString(R.string.slider_min_selection_fmt, config.minSelectionSizeDp),
                24f, 96f, 6f, config.minSelectionSizeDp, palette,
                { context.getString(R.string.slider_min_selection_fmt, it) }
            ) { applyLivePreview(config.copy(minSelectionSizeDp = it)) }
        )

        advBox.addView(UiKit.createDivider(context, palette))
        advBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_smooth_freeform), config.smoothFreeformPath, palette) {
            applyLivePreview(config.copy(smoothFreeformPath = it))
        })
        advBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_edge_smoothing), config.edgeSmoothing, palette) {
            applyLivePreview(config.copy(edgeSmoothing = it))
        })
        advBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_selection_animation), config.animationEnabled, palette) {
            applyLivePreview(config.copy(animationEnabled = it))
        })

        advBox.addView(
            UiKit.createPillButton(context, context.getString(R.string.btn_reset_default), UiKit.ButtonVariant.OUTLINED, palette) {
                preferences.resetSelectionConfig()
                onFullRebindRequested()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.END
                    topMargin = dp(8f)
                }
            }
        )
        container.addView(advCard)
    }
}
