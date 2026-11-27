package com.bubble.screentools.ui.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import com.bubble.screentools.R
import com.bubble.screentools.model.ActionMode
import com.bubble.screentools.model.ActionPanelButtonType
import com.bubble.screentools.model.ActionPanelConfig
import com.bubble.screentools.model.ActionPanelLayoutMode
import com.bubble.screentools.model.ActionPanelPosition
import com.bubble.screentools.theme.ThemePaletteGenerator
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Shared Action Panel View used identically by BOTH the live overlay (BubbleAssistantActivity &
 * BubbleOverlayController) AND the Main App live previews (General & Panel tabs).
 *
 * Preserves Bubble's iconic minimalist two-capsule-button default (`Translate` + `Search Image`),
 * while supporting Horizontal Scroll, Two Rows, Icon-Only, vertical Left/Right docking,
 * Custom relative positioning, and full color/size/spacing/opacity customization.
 */
class ActionPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var onActionButtonClick: ((ActionPanelButtonType) -> Unit)? = null

    private var currentConfig: ActionPanelConfig = ActionPanelConfig()
    private var currentActiveMode: ActionMode = ActionMode.TRANSLATE
    private var currentAccentColor: Int = Color.parseColor("#2196F3")

    private val density: Float get() = resources.displayMetrics.density

    fun render(
        config: ActionPanelConfig,
        activeMode: ActionMode,
        themeAccentColor: Int
    ) {
        currentConfig = config.sanitized()
        currentActiveMode = activeMode
        currentAccentColor = themeAccentColor
        rebuildButtons()
    }

    fun updateActiveMode(activeMode: ActionMode) {
        if (currentActiveMode == activeMode) return
        currentActiveMode = activeMode
        rebuildButtons()
    }

    private fun rebuildButtons() {
        removeAllViews()
        val cfg = currentConfig
        val buttons = cfg.enabledButtons
        val isVertical = cfg.position == ActionPanelPosition.LEFT || cfg.position == ActionPanelPosition.RIGHT
        val effectiveIconOnly = cfg.layoutMode == ActionPanelLayoutMode.ICON_ONLY || !cfg.showLabels || isVertical

        val activeBgColor = currentAccentColor
        val inactiveBgColor = Color.parseColor("#CC202124")
        val outlineColor = Color.parseColor("#4DFFFFFF")
        val iconColor = Color.WHITE
        val textColor = Color.WHITE

        // Transparent floating container preserving Bubble's minimalist capsule pills
        background = null
        setPadding(0, 0, 0, 0)

        alpha = cfg.panelOpacity
        scaleX = cfg.overallScale
        scaleY = cfg.overallScale

        val spacingPx = dp(cfg.buttonSpacingDp)

        if (isVertical) {
            val scroll = ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            }
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            buttons.forEachIndexed { idx, type ->
                val pill = createActionButton(
                    type = type,
                    isActive = isButtonActive(type),
                    iconOnly = effectiveIconOnly,
                    activeBgColor = activeBgColor,
                    inactiveBgColor = inactiveBgColor,
                    outlineColor = outlineColor,
                    iconColor = iconColor,
                    textColor = textColor,
                    equalWeight = false
                )
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(cfg.buttonSize.heightDp.toFloat())
                ).apply {
                    if (idx > 0) topMargin = spacingPx
                }
                col.addView(pill, lp)
            }
            scroll.addView(col)
            addView(scroll)
            return
        }

        // Classic 2-button (or 1-button) balanced row matching original Bubble screenshots 1:1
        if (buttons.size <= 2 && cfg.layoutMode != ActionPanelLayoutMode.ICON_ONLY) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }
            buttons.forEachIndexed { idx, type ->
                val pill = createActionButton(
                    type = type,
                    isActive = isButtonActive(type),
                    iconOnly = !cfg.showLabels,
                    activeBgColor = activeBgColor,
                    inactiveBgColor = inactiveBgColor,
                    outlineColor = outlineColor,
                    iconColor = iconColor,
                    textColor = textColor,
                    equalWeight = true
                )
                val lp = LinearLayout.LayoutParams(0, dp(cfg.buttonSize.heightDp.toFloat()), 1f).apply {
                    if (idx > 0) marginStart = spacingPx
                }
                row.addView(pill, lp)
            }
            addView(row)
            return
        }

        when (cfg.layoutMode) {
            ActionPanelLayoutMode.TWO_ROWS -> {
                val outer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                }
                val firstRowCount = ceil(buttons.size / 2.0).toInt()
                val chunks = listOf(
                    buttons.take(firstRowCount),
                    buttons.drop(firstRowCount)
                ).filter { it.isNotEmpty() }

                chunks.forEachIndexed { rowIdx, rowButtons ->
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            if (rowIdx > 0) topMargin = spacingPx
                        }
                    }
                    rowButtons.forEachIndexed { idx, type ->
                        val pill = createActionButton(
                            type = type,
                            isActive = isButtonActive(type),
                            iconOnly = !cfg.showLabels,
                            activeBgColor = activeBgColor,
                            inactiveBgColor = inactiveBgColor,
                            outlineColor = outlineColor,
                            iconColor = iconColor,
                            textColor = textColor,
                            equalWeight = true
                        )
                        val lp = LinearLayout.LayoutParams(
                            0,
                            dp(cfg.buttonSize.heightDp.toFloat()),
                            1f
                        ).apply {
                            if (idx > 0) marginStart = spacingPx
                        }
                        row.addView(pill, lp)
                    }
                    outer.addView(row)
                }
                addView(outer)
            }

            ActionPanelLayoutMode.HORIZONTAL_SCROLL,
            ActionPanelLayoutMode.ICON_ONLY -> {
                val hScroll = HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    isFillViewport = false
                    layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL)
                }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                buttons.forEachIndexed { idx, type ->
                    val pill = createActionButton(
                        type = type,
                        isActive = isButtonActive(type),
                        iconOnly = effectiveIconOnly,
                        activeBgColor = activeBgColor,
                        inactiveBgColor = inactiveBgColor,
                        outlineColor = outlineColor,
                        iconColor = iconColor,
                        textColor = textColor,
                        equalWeight = false
                    )
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(cfg.buttonSize.heightDp.toFloat())
                    ).apply {
                        if (idx > 0) marginStart = spacingPx
                    }
                    row.addView(pill, lp)
                }
                hScroll.addView(row)
                addView(hScroll)
            }
        }
    }

    private fun isButtonActive(type: ActionPanelButtonType): Boolean {
        return (type == ActionPanelButtonType.TRANSLATE && currentActiveMode == ActionMode.TRANSLATE) ||
            (type == ActionPanelButtonType.SEARCH_IMAGE && currentActiveMode == ActionMode.VISUAL_SEARCH)
    }

    private fun createActionButton(
        type: ActionPanelButtonType,
        isActive: Boolean,
        iconOnly: Boolean,
        activeBgColor: Int,
        inactiveBgColor: Int,
        outlineColor: Int,
        iconColor: Int,
        textColor: Int,
        equalWeight: Boolean
    ): View {
        val cfg = currentConfig
        val label = getLabelFor(context, type)
        val effectiveBg = if (isActive) activeBgColor else inactiveBgColor
        val effectiveIconColor = if (isActive) {
            ThemePaletteGenerator.bestOnColor(activeBgColor)
        } else {
            iconColor
        }
        val effectiveTextColor = if (isActive) {
            ThemePaletteGenerator.bestOnColor(activeBgColor)
        } else {
            textColor
        }

        val pillBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cfg.buttonCornerRadiusDp).toFloat()
            setColor(effectiveBg)
            if (!isActive) {
                setStroke(dp(1f), outlineColor)
            }
        }

        val padH = if (iconOnly) dp(12f) else dp(cfg.buttonPaddingHorizontalDp)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = pillBg
            isClickable = true
            isFocusable = true
            contentDescription = label
            setPadding(padH, 0, padH, 0)
            if (!equalWeight) {
                minimumWidth = dp(if (iconOnly) 44f else 84f)
            }
            setOnClickListener { onActionButtonClick?.invoke(type) }
        }

        val iconSize = dp(cfg.buttonSize.iconDp.toFloat())
        val iconView = ImageView(context).apply {
            val drawable = context.getDrawable(getIconResFor(type))?.mutate()
            if (drawable != null) {
                DrawableCompat.setTint(drawable, effectiveIconColor)
                setImageDrawable(drawable)
            }
            contentDescription = null
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                if (!iconOnly) marginEnd = dp(6f)
            }
        }
        container.addView(iconView)

        if (!iconOnly) {
            val textView = TextView(context).apply {
                text = label
                isSingleLine = true
                setTextColor(effectiveTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, cfg.buttonSize.textSp)
                setTypeface(typeface, Typeface.BOLD)
            }
            container.addView(textView)
        }

        return container
    }

    private fun dp(value: Float): Int = (value * density).roundToInt()

    companion object {

        fun getIconResFor(type: ActionPanelButtonType): Int = when (type) {
            ActionPanelButtonType.TRANSLATE -> R.drawable.ic_translate
            ActionPanelButtonType.SEARCH_IMAGE -> R.drawable.ic_visual_search
            ActionPanelButtonType.FULL_SCREEN -> R.drawable.ic_fullscreen
            ActionPanelButtonType.SELECT_TEXT -> R.drawable.ic_select_text
            ActionPanelButtonType.COPY_IMAGE -> R.drawable.ic_copy_image
            ActionPanelButtonType.SAVE_IMAGE -> R.drawable.ic_save_image
            ActionPanelButtonType.SHARE_IMAGE -> R.drawable.ic_share_image
        }

        fun getLabelFor(context: Context, type: ActionPanelButtonType): String = when (type) {
            ActionPanelButtonType.TRANSLATE -> context.getString(R.string.action_translate)
            ActionPanelButtonType.SEARCH_IMAGE -> context.getString(R.string.action_visual_search)
            ActionPanelButtonType.FULL_SCREEN -> context.getString(R.string.panel_action_full_screen)
            ActionPanelButtonType.SELECT_TEXT -> context.getString(R.string.overlay_select_text)
            ActionPanelButtonType.COPY_IMAGE -> context.getString(R.string.panel_action_copy_image)
            ActionPanelButtonType.SAVE_IMAGE -> context.getString(R.string.panel_action_save_image)
            ActionPanelButtonType.SHARE_IMAGE -> context.getString(R.string.panel_action_share_image)
        }

        /**
         * Pure-Kotlin coordinate clamping for custom relative positioning that guarantees the
         * panel never clips off-screen or collides with system bars after rotation or resize.
         */
        fun computeClampedCustomOffsets(
            relX: Float,
            relY: Float,
            containerWidth: Int,
            containerHeight: Int,
            panelWidth: Int,
            panelHeight: Int,
            insetLeft: Int,
            insetTop: Int,
            insetRight: Int,
            insetBottom: Int
        ): Pair<Float, Float> {
            val safeRelX = if (relX.isFinite()) relX.coerceIn(0f, 1f) else 0.5f
            val safeRelY = if (relY.isFinite()) relY.coerceIn(0f, 1f) else 0.88f
            val minX = insetLeft.toFloat()
            val maxX = (containerWidth - insetRight - panelWidth).toFloat().coerceAtLeast(minX)
            val minY = insetTop.toFloat()
            val maxY = (containerHeight - insetBottom - panelHeight).toFloat().coerceAtLeast(minY)
            val rawX = safeRelX * containerWidth - panelWidth * 0.5f
            val rawY = safeRelY * containerHeight - panelHeight * 0.5f
            return rawX.coerceIn(minX, maxX) to rawY.coerceIn(minY, maxY)
        }

        /**
         * Applies saved ActionPanelPosition (BOTTOM / TOP / LEFT / RIGHT / CUSTOM) safely to
         * the overlay's bottomActionRow container respecting system insets.
         */
        fun applyContainerPosition(
            container: View,
            config: ActionPanelConfig,
            insetLeft: Int,
            insetTop: Int,
            insetRight: Int,
            insetBottom: Int
        ) {
            val density = container.resources.displayMetrics.density
            val marginH = (config.horizontalMarginDp * density).roundToInt()
            val marginV = (config.verticalMarginDp * density).roundToInt()
            val lp = (container.layoutParams as? FrameLayout.LayoutParams) ?: return

            when (config.position) {
                ActionPanelPosition.BOTTOM -> {
                    lp.width = FrameLayout.LayoutParams.MATCH_PARENT
                    lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
                    lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    container.translationX = 0f
                    container.translationY = 0f
                    container.setPadding(
                        marginH + insetLeft,
                        0,
                        marginH + insetRight,
                        marginV + insetBottom
                    )
                }
                ActionPanelPosition.TOP -> {
                    lp.width = FrameLayout.LayoutParams.MATCH_PARENT
                    lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
                    lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    container.translationX = 0f
                    container.translationY = 0f
                    container.setPadding(
                        marginH + insetLeft,
                        (54 * density).roundToInt() + marginV + insetTop,
                        marginH + insetRight,
                        0
                    )
                }
                ActionPanelPosition.LEFT -> {
                    lp.width = FrameLayout.LayoutParams.WRAP_CONTENT
                    lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
                    lp.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    container.translationX = 0f
                    container.translationY = 0f
                    container.setPadding(
                        marginH + insetLeft,
                        insetTop,
                        0,
                        insetBottom
                    )
                }
                ActionPanelPosition.RIGHT -> {
                    lp.width = FrameLayout.LayoutParams.WRAP_CONTENT
                    lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
                    lp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    container.translationX = 0f
                    container.translationY = 0f
                    container.setPadding(
                        0,
                        insetTop,
                        marginH + insetRight,
                        insetBottom
                    )
                }
                ActionPanelPosition.CUSTOM -> {
                    lp.width = FrameLayout.LayoutParams.WRAP_CONTENT
                    lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
                    lp.gravity = Gravity.TOP or Gravity.START
                    container.setPadding(marginH / 2, marginV / 2, marginH / 2, marginV / 2)
                    container.post {
                        val parent = container.parent as? View ?: return@post
                        val (tx, ty) = computeClampedCustomOffsets(
                            relX = config.customPosX,
                            relY = config.customPosY,
                            containerWidth = parent.width,
                            containerHeight = parent.height,
                            panelWidth = container.width,
                            panelHeight = container.height,
                            insetLeft = insetLeft + marginH / 2,
                            insetTop = insetTop + (48 * density).roundToInt(),
                            insetRight = insetRight + marginH / 2,
                            insetBottom = insetBottom + marginV / 2
                        )
                        container.translationX = tx
                        container.translationY = ty
                    }
                }
            }
            container.layoutParams = lp
        }
    }
}
