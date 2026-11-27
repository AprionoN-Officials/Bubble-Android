package com.bubble.screentools.ui.main.tabs

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.model.ActionMode
import com.bubble.screentools.model.ActionPanelButtonSize
import com.bubble.screentools.model.ActionPanelButtonType
import com.bubble.screentools.model.ActionPanelConfig
import com.bubble.screentools.model.ActionPanelLayoutMode
import com.bubble.screentools.model.ActionPanelPosition
import com.bubble.screentools.theme.ThemeManager
import com.bubble.screentools.theme.ThemePaletteGenerator
import com.bubble.screentools.ui.main.UiKit
import com.bubble.screentools.ui.overlay.ActionPanelView

/**
 * Tab 4 — Panel / Panel:
 * - Live Action Panel Preview using the EXACT shared ActionPanelView component
 * - Interactive Custom Position Editor (drag the panel directly inside the simulated screen!)
 * - Action Button Manager (6 working tools, Enable/Disable with >=1 guard, drag "=" handle reorder)
 * - Position (Bottom/Top/Left/Right/Custom), Layout (Horizontal Scroll / Two Rows / Icon Only),
 *   Button Size, Labels, Search Engine Selector, Scale, Opacity, Spacing, Padding, Button Radius, Margins, Reset
 *   (Panel colors automatically follow the active Theme / Material You accent from Appearance tab.)
 */
object PanelTabBuilder {

    @SuppressLint("ClickableViewAccessibility")
    fun render(
        context: Context,
        container: LinearLayout,
        preferences: BubblePreferences,
        palette: ThemePaletteGenerator.BubblePalette,
        onFullRebindRequested: () -> Unit
    ) {
        container.removeAllViews()
        val dp = { v: Float -> UiKit.dp(context, v) }
        var config = preferences.actionPanelConfig
        val themeAccent = ThemeManager.resolveOverlayAccentColor(context, preferences)

        // 1. Live Action Panel Preview & Interactive Custom Position Editor Card
        val (previewCard, previewBox) = UiKit.createExpressiveCard(context, palette)
        previewBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_panel_preview), palette))
        previewBox.addView(UiKit.createSubtitle(context, context.getString(R.string.panel_preview_hint), palette))

        val screenStage = FrameLayout(context).apply {
            tag = "skip_theme_children"
            clipToOutline = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18f).toFloat()
                setColor(if (palette.isDark) Color.parseColor("#13151B") else Color.parseColor("#E6ECF4"))
                setStroke(dp(1f), palette.outlineVariant)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(240f)
            )
        }

        // Mock selection box inside screen stage for visual context
        val mockSelection = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12f).toFloat()
                setStroke(dp(2f), themeAccent)
                setColor(ThemePaletteGenerator.withAlpha(themeAccent, 0.08f))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(110f),
                Gravity.CENTER
            ).apply {
                marginStart = dp(24f)
                marginEnd = dp(24f)
            }
        }
        screenStage.addView(mockSelection)

        val panelHost = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        val enginePillPreview = TextView(context).apply {
            text = "Engine: Google Lens ▾"
            textSize = 11f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16f).toFloat()
                setColor(Color.parseColor("#CC202124"))
            }
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(6f) }
        }
        panelHost.addView(enginePillPreview)

        val livePanel = ActionPanelView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        panelHost.addView(livePanel)
        screenStage.addView(panelHost)
        previewBox.addView(screenStage)

        val tvCustomCoords = TextView(context).apply {
            textSize = 12f
            setTextColor(palette.primary)
            setPadding(0, dp(8f), 0, 0)
            visibility = if (config.position == ActionPanelPosition.CUSTOM) View.VISIBLE else View.GONE
        }
        previewBox.addView(tvCustomCoords)

        fun updateLivePreview(updated: ActionPanelConfig) {
            config = updated.sanitized()
            preferences.actionPanelConfig = config
            enginePillPreview.visibility = if (config.showSearchEngineSelector) View.VISIBLE else View.GONE
            livePanel.render(config, ActionMode.TRANSLATE, themeAccent)
            ActionPanelView.applyContainerPosition(
                container = panelHost,
                config = config,
                insetLeft = dp(8f),
                insetTop = dp(8f),
                insetRight = dp(8f),
                insetBottom = dp(8f)
            )
            if (config.position == ActionPanelPosition.CUSTOM) {
                tvCustomCoords.visibility = View.VISIBLE
                tvCustomCoords.text = "${context.getString(R.string.panel_custom_drag_hint)}  (X: ${(config.customPosX * 100).toInt()}%, Y: ${(config.customPosY * 100).toInt()}%)"
            } else {
                tvCustomCoords.visibility = View.GONE
            }
        }
        updateLivePreview(config)

        // Interactive Drag-to-Position on screen stage when position == CUSTOM
        screenStage.setOnTouchListener { v, event ->
            if (config.position != ActionPanelPosition.CUSTOM) return@setOnTouchListener false
            v.parent?.requestDisallowInterceptTouchEvent(true)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val relX = (event.x / v.width.coerceAtLeast(1)).coerceIn(0.08f, 0.92f)
                    val relY = (event.y / v.height.coerceAtLeast(1)).coerceIn(0.14f, 0.92f)
                    updateLivePreview(config.copy(customPosX = relX, customPosY = relY))
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }

        if (config.position == ActionPanelPosition.CUSTOM) {
            previewBox.addView(
                UiKit.createPillButton(context, context.getString(R.string.btn_reset_position), UiKit.ButtonVariant.TEXT, palette) {
                    updateLivePreview(config.copy(customPosX = 0.5f, customPosY = 0.88f))
                }
            )
        }
        container.addView(previewCard)

        // 2. Action Buttons & Drag-Handle Reordering Card
        val (btnCard, btnBox) = UiKit.createExpressiveCard(context, palette)
        btnBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_panel_buttons), palette))
        btnBox.addView(UiKit.createSubtitle(context, context.getString(R.string.panel_buttons_subtitle), palette))

        // Enabled buttons rendered in a dedicated drag-reorderable container (exact priority order)
        val enabledOrdered = config.enabledButtons
        val disabledRest = ActionPanelButtonType.entries.filterNot { it in enabledOrdered }

        val enabledListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        enabledOrdered.forEachIndexed { enabledIdx, actionType ->
            val label = ActionPanelView.getLabelFor(context, actionType)
            val rowWrapper = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(palette.surfaceContainer)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rowWrapper.addView(UiKit.createRowDivider(context, palette, visible = enabledIdx > 0))

            val rowLine = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            if (enabledOrdered.size > 1) {
                val dragHandle = UiKit.createDragHandle(context, palette).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f)).apply {
                        marginEnd = dp(4f)
                    }
                }
                rowLine.addView(dragHandle)
                UiKit.attachDragReorder(
                    context = context,
                    listContainer = enabledListContainer,
                    rowView = rowWrapper,
                    dragHandle = dragHandle,
                    itemLabel = label
                ) { fromIdx, toIdx ->
                    val next = config.enabledButtons.toMutableList()
                    if (fromIdx in next.indices && toIdx in next.indices && fromIdx != toIdx) {
                        val moved = next.removeAt(fromIdx)
                        next.add(toIdx, moved)
                        updateLivePreview(config.copy(enabledButtons = next))
                        onFullRebindRequested()
                    }
                }
            }

            rowLine.addView(
                UiKit.createSwitchRow(
                    context = context,
                    label = "#${enabledIdx + 1}  $label",
                    checked = true,
                    palette = palette
                ) { checked ->
                    if (!checked) {
                        val next = config.enabledButtons.toMutableList()
                        if (next.size <= 1) {
                            Toast.makeText(context, context.getString(R.string.panel_buttons_subtitle), Toast.LENGTH_SHORT).show()
                            onFullRebindRequested()
                            return@createSwitchRow
                        }
                        next.remove(actionType)
                        updateLivePreview(config.copy(enabledButtons = next))
                        onFullRebindRequested()
                    }
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )

            rowWrapper.addView(rowLine)
            enabledListContainer.addView(rowWrapper)
        }
        btnBox.addView(enabledListContainer)

        // Disabled buttons listed below (toggling ON adds them to the active reorderable list)
        for (actionType in disabledRest) {
            btnBox.addView(UiKit.createDivider(context, palette))
            val label = ActionPanelView.getLabelFor(context, actionType)
            btnBox.addView(
                UiKit.createSwitchRow(
                    context = context,
                    label = label,
                    checked = false,
                    palette = palette
                ) { checked ->
                    if (checked) {
                        val next = config.enabledButtons.toMutableList()
                        if (actionType !in next) next.add(actionType)
                        updateLivePreview(config.copy(enabledButtons = next))
                        onFullRebindRequested()
                    }
                }
            )
        }
        container.addView(btnCard)

        // 3. Layout & Position Card
        val (layoutCard, layoutBox) = UiKit.createExpressiveCard(context, palette)
        layoutBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_panel_layout), palette))

        layoutBox.addView(UiKit.createLabel(context, context.getString(R.string.label_panel_position), palette))
        layoutBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    ActionPanelPosition.BOTTOM to context.getString(R.string.pos_bottom),
                    ActionPanelPosition.TOP to context.getString(R.string.pos_top),
                    ActionPanelPosition.LEFT to context.getString(R.string.pos_left),
                    ActionPanelPosition.RIGHT to context.getString(R.string.pos_right),
                    ActionPanelPosition.CUSTOM to context.getString(R.string.pos_custom)
                ),
                selected = config.position,
                palette = palette,
                scrollable = true
            ) { pos ->
                updateLivePreview(config.copy(position = pos))
                onFullRebindRequested()
            }
        )

        layoutBox.addView(UiKit.createLabel(context, context.getString(R.string.label_panel_layout_mode), palette))
        layoutBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    ActionPanelLayoutMode.HORIZONTAL_SCROLL to context.getString(R.string.layout_h_scroll),
                    ActionPanelLayoutMode.TWO_ROWS to context.getString(R.string.layout_two_rows),
                    ActionPanelLayoutMode.ICON_ONLY to context.getString(R.string.layout_icon_only)
                ),
                selected = config.layoutMode,
                palette = palette,
                scrollable = true
            ) { mode ->
                updateLivePreview(config.copy(layoutMode = mode))
            }
        )

        layoutBox.addView(UiKit.createLabel(context, context.getString(R.string.label_button_size), palette))
        layoutBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    ActionPanelButtonSize.COMPACT to context.getString(R.string.size_compact),
                    ActionPanelButtonSize.STANDARD to context.getString(R.string.size_standard),
                    ActionPanelButtonSize.LARGE to context.getString(R.string.size_large)
                ),
                selected = config.buttonSize,
                palette = palette
            ) { size ->
                updateLivePreview(config.copy(buttonSize = size))
            }
        )

        layoutBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_show_labels), config.showLabels, palette) {
            updateLivePreview(config.copy(showLabels = it))
        })
        layoutBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_show_engine_selector), config.showSearchEngineSelector, palette) {
            updateLivePreview(config.copy(showSearchEngineSelector = it))
        })
        container.addView(layoutCard)

        // 4. Panel Appearance Card
        val (appCard, appBox) = UiKit.createExpressiveCard(context, palette)
        appBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_panel_appearance), palette))

        appBox.addView(
            UiKit.createSliderRow(
                context, context.getString(R.string.slider_panel_scale_fmt, (config.overallScale * 100).toInt()),
                75f, 125f, 5f, config.overallScale * 100f, palette,
                { context.getString(R.string.slider_panel_scale_fmt, it.toInt()) }
            ) { updateLivePreview(config.copy(overallScale = it / 100f)) }
        )

        appBox.addView(
            UiKit.createSliderRow(
                context, context.getString(R.string.slider_panel_opacity_fmt, (config.panelOpacity * 100).toInt()),
                45f, 100f, 5f, config.panelOpacity * 100f, palette,
                { context.getString(R.string.slider_panel_opacity_fmt, it.toInt()) }
            ) { updateLivePreview(config.copy(panelOpacity = it / 100f)) }
        )

        appBox.addView(
            UiKit.createSliderRow(
                context, context.getString(R.string.slider_btn_spacing_fmt, config.buttonSpacingDp),
                2f, 20f, 2f, config.buttonSpacingDp, palette,
                { context.getString(R.string.slider_btn_spacing_fmt, it) }
            ) { updateLivePreview(config.copy(buttonSpacingDp = it)) }
        )

        appBox.addView(
            UiKit.createSliderRow(
                context, context.getString(R.string.slider_btn_padding_fmt, config.buttonPaddingHorizontalDp),
                6f, 24f, 2f, config.buttonPaddingHorizontalDp, palette,
                { context.getString(R.string.slider_btn_padding_fmt, it) }
            ) { updateLivePreview(config.copy(buttonPaddingHorizontalDp = it)) }
        )

        appBox.addView(
            UiKit.createSliderRow(
                context, context.getString(R.string.slider_btn_radius_fmt, config.buttonCornerRadiusDp),
                4f, 32f, 2f, config.buttonCornerRadiusDp, palette,
                { context.getString(R.string.slider_btn_radius_fmt, it) }
            ) { updateLivePreview(config.copy(buttonCornerRadiusDp = it)) }
        )

        appBox.addView(
            UiKit.createSliderRow(
                context, context.getString(R.string.slider_margin_h_fmt, config.horizontalMarginDp),
                4f, 40f, 2f, config.horizontalMarginDp, palette,
                { context.getString(R.string.slider_margin_h_fmt, it) }
            ) { updateLivePreview(config.copy(horizontalMarginDp = it)) }
        )

        appBox.addView(
            UiKit.createSliderRow(
                context, context.getString(R.string.slider_margin_v_fmt, config.verticalMarginDp),
                4f, 64f, 2f, config.verticalMarginDp, palette,
                { context.getString(R.string.slider_margin_v_fmt, it) }
            ) { updateLivePreview(config.copy(verticalMarginDp = it)) }
        )

        appBox.addView(
            UiKit.createPillButton(context, context.getString(R.string.btn_reset_default), UiKit.ButtonVariant.OUTLINED, palette) {
                preferences.resetActionPanelConfig()
                onFullRebindRequested()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.END
                    topMargin = dp(10f)
                }
            }
        )
        container.addView(appCard)
    }
}
