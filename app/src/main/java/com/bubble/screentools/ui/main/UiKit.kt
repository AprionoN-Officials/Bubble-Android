package com.bubble.screentools.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewParent
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.NestedScrollView
import com.bubble.screentools.R
import com.bubble.screentools.theme.ThemePaletteGenerator
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shared Material 3 Expressive View Builders for Bubble's 7 settings tabs.
 * Enforces generous spacing, rounded cards (20dp), pill buttons with dynamic wrap_content height
 * (minHeight=44dp — zero text clipping), and accessible contrast & touch targets.
 */
object UiKit {

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    fun createTabScrollPage(context: Context): Pair<NestedScrollView, LinearLayout> {
        val scroll = NestedScrollView(context).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dp(context, 16f)
            setPadding(padH, dp(context, 14f), padH, dp(context, 28f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        scroll.addView(content)
        return scroll to content
    }

    fun createExpressiveCard(
        context: Context,
        palette: ThemePaletteGenerator.BubblePalette,
        tonal: Boolean = false
    ): Pair<MaterialCardView, LinearLayout> {
        val card = MaterialCardView(context).apply {
            radius = dp(context, 20f).toFloat()
            cardElevation = 0f
            strokeWidth = if (tonal) 0 else dp(context, 1f)
            tag = if (tonal) "tonal_card" else "card"
            setCardBackgroundColor(if (tonal) palette.primaryContainer else palette.surfaceContainer)
            strokeColor = palette.outlineVariant
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, 14f)
            }
        }
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(context, 16f)
            setPadding(p, p, p, p)
        }
        card.addView(inner)
        return card to inner
    }

    fun createSectionTitle(
        context: Context,
        text: String,
        palette: ThemePaletteGenerator.BubblePalette
    ): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 16.5f
            setTypeface(typeface, Typeface.BOLD)
            tag = "section_title"
            setTextColor(palette.primary)
            setPadding(0, 0, 0, dp(context, 6f))
        }
    }

    fun createSubtitle(
        context: Context,
        text: String,
        palette: ThemePaletteGenerator.BubblePalette
    ): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 12.5f
            tag = "text_secondary"
            setTextColor(palette.onSurfaceVariant)
            setPadding(0, 0, 0, dp(context, 10f))
        }
    }

    fun createLabel(
        context: Context,
        text: String,
        palette: ThemePaletteGenerator.BubblePalette
    ): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            tag = "text_primary"
            setTextColor(palette.onSurface)
            setPadding(0, dp(context, 8f), 0, dp(context, 6f))
        }
    }

    enum class ButtonVariant { PRIMARY, TONAL, OUTLINED, TEXT }

    fun createPillButton(
        context: Context,
        label: String,
        variant: ButtonVariant,
        palette: ThemePaletteGenerator.BubblePalette,
        onClick: () -> Unit
    ): MaterialButton {
        val styleAttr = when (variant) {
            ButtonVariant.PRIMARY -> com.google.android.material.R.attr.materialButtonStyle
            ButtonVariant.TONAL -> com.google.android.material.R.attr.materialButtonStyle
            ButtonVariant.OUTLINED -> com.google.android.material.R.attr.materialButtonOutlinedStyle
            ButtonVariant.TEXT -> com.google.android.material.R.attr.borderlessButtonStyle
        }
        return MaterialButton(context, null, styleAttr).apply {
            text = label
            isAllCaps = false
            cornerRadius = dp(context, 24f)
            minHeight = dp(context, 48f)
            insetTop = 0
            insetBottom = 0
            setPadding(dp(context, 14f), dp(context, 10f), dp(context, 14f), dp(context, 10f))
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            when (variant) {
                ButtonVariant.PRIMARY -> {
                    tag = "btn_primary"
                    backgroundTintList = ColorStateList.valueOf(palette.primary)
                    setTextColor(palette.onPrimary)
                }
                ButtonVariant.TONAL -> {
                    tag = "btn_tonal"
                    backgroundTintList = ColorStateList.valueOf(palette.primaryContainer)
                    setTextColor(palette.onPrimaryContainer)
                }
                ButtonVariant.OUTLINED -> {
                    tag = "btn_outlined"
                    strokeColor = ColorStateList.valueOf(palette.outline)
                    setTextColor(palette.primary)
                }
                ButtonVariant.TEXT -> {
                    tag = "btn_text"
                    setTextColor(palette.primary)
                }
            }
            setOnClickListener { onClick() }
        }
    }

    fun createSwitchRow(
        context: Context,
        label: String,
        checked: Boolean,
        palette: ThemePaletteGenerator.BubblePalette,
        subtitle: String? = null,
        onChanged: (Boolean) -> Unit
    ): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 4f), 0, dp(context, 4f))
        }
        val sw = MaterialSwitch(context).apply {
            text = label
            isChecked = checked
            textSize = 14f
            setTextColor(palette.onSurface)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked))
            trackTintList = ColorStateList(states, intArrayOf(palette.primary, palette.surfaceContainerHigh))
            thumbTintList = ColorStateList(states, intArrayOf(palette.onPrimary, palette.outline))
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
        }
        box.addView(sw)
        if (!subtitle.isNullOrBlank()) {
            box.addView(TextView(context).apply {
                text = subtitle
                textSize = 12f
                tag = "text_secondary"
                setTextColor(palette.onSurfaceVariant)
                setPadding(0, 0, dp(context, 48f), dp(context, 4f))
            })
        }
        return box
    }

    fun createSliderRow(
        context: Context,
        initialLabel: String,
        valueFrom: Float,
        valueTo: Float,
        stepSize: Float,
        currentValue: Float,
        palette: ThemePaletteGenerator.BubblePalette,
        formatLabel: (Float) -> String,
        onValueChanged: (Float) -> Unit
    ): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(context, 6f), 0, dp(context, 2f))
        }
        val tv = TextView(context).apply {
            text = initialLabel
            textSize = 13f
            tag = "text_primary"
            setTextColor(palette.onSurface)
        }
        val clamped = currentValue.coerceIn(valueFrom, valueTo)
        val snapped = if (stepSize > 0f) {
            val steps = ((clamped - valueFrom) / stepSize).roundToInt()
            (valueFrom + steps * stepSize).coerceIn(valueFrom, valueTo)
        } else {
            clamped
        }
        val slider = Slider(context).apply {
            this.valueFrom = valueFrom
            this.valueTo = valueTo
            if (stepSize > 0f) this.stepSize = stepSize
            this.value = snapped
            trackActiveTintList = ColorStateList.valueOf(palette.primary)
            trackInactiveTintList = ColorStateList.valueOf(palette.outlineVariant)
            thumbTintList = ColorStateList.valueOf(palette.primary)
            contentDescription = initialLabel
            addOnChangeListener { _, value, fromUser ->
                tv.text = formatLabel(value)
                if (fromUser) onValueChanged(value)
            }
        }
        box.addView(tv)
        box.addView(slider)
        return box
    }

    fun <T> createChoiceChips(
        context: Context,
        items: List<Pair<T, String>>,
        selected: T,
        palette: ThemePaletteGenerator.BubblePalette,
        scrollable: Boolean = false,
        onSelected: (T) -> Unit
    ): View {
        val group = ChipGroup(context).apply {
            isSingleSelection = true
            isSelectionRequired = true
            isSingleLine = scrollable
        }
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked))
        val bgList = ColorStateList(states, intArrayOf(palette.primaryContainer, palette.surface))
        val txtList = ColorStateList(states, intArrayOf(palette.onPrimaryContainer, palette.onSurface))
        val strokeList = ColorStateList(states, intArrayOf(palette.primary, palette.outlineVariant))

        for ((value, label) in items) {
            val chip = Chip(context).apply {
                text = label
                isCheckable = true
                isChecked = (value == selected)
                chipBackgroundColor = bgList
                setTextColor(txtList)
                chipStrokeColor = strokeList
                chipStrokeWidth = dp(context, 1f).toFloat()
                chipCornerRadius = dp(context, 18f).toFloat()
                setOnClickListener { onSelected(value) }
            }
            group.addView(chip)
        }

        return if (scrollable) {
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(group)
            }
        } else {
            group
        }
    }

    fun createColorSwatchesRow(
        context: Context,
        colors: List<Int>,
        selectedColor: Int,
        palette: ThemePaletteGenerator.BubblePalette,
        onPicked: (Int) -> Unit
    ): HorizontalScrollView {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 4f), 0, dp(context, 6f))
        }
        val size = dp(context, 34f)
        val swatchViews = ArrayList<Pair<Int, View>>(colors.size)
        fun applyRingStates(activeColor: Int) {
            for ((col, v) in swatchViews) {
                val selected = (col == activeColor)
                (v.background as? GradientDrawable)?.setStroke(
                    dp(context, if (selected) 3f else 1.5f),
                    if (selected) palette.onSurface else palette.outline
                )
            }
        }
        for (c in colors) {
            val isSelected = (c == selectedColor)
            val swatch = View(context).apply {
                contentDescription = ThemePaletteGenerator.formatHex(c)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (c == Color.TRANSPARENT) palette.surfaceContainerHigh else c)
                    setStroke(
                        dp(context, if (isSelected) 3f else 1.5f),
                        if (isSelected) palette.onSurface else palette.outline
                    )
                }
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dp(context, 10f)
                }
                setOnClickListener {
                    applyRingStates(c)
                    onPicked(c)
                }
            }
            swatchViews.add(c to swatch)
            row.addView(swatch)
        }
        scroll.addView(row)
        return scroll
    }

    fun showHexRgbColorPickerDialog(
        context: Context,
        title: String,
        initialColor: Int,
        palette: ThemePaletteGenerator.BubblePalette,
        onColorChosen: (Int) -> Unit
    ) {
        var r = Color.red(initialColor)
        var g = Color.green(initialColor)
        var b = Color.blue(initialColor)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(context, 20f)
            setPadding(p, dp(context, 14f), p, dp(context, 8f))
        }

        val preview = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(context, 14f).toFloat()
                setColor(Color.rgb(r, g, b))
                setStroke(dp(context, 1f), palette.outline)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 48f)
            ).apply { bottomMargin = dp(context, 12f) }
        }
        container.addView(preview)

        val hexInput = EditText(context).apply {
            hint = "#2196F3"
            setText(ThemePaletteGenerator.formatHex(Color.rgb(r, g, b)))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        container.addView(hexInput)

        fun refreshPreview() {
            val col = Color.rgb(r, g, b)
            (preview.background as? GradientDrawable)?.setColor(col)
            hexInput.setText(ThemePaletteGenerator.formatHex(col))
        }

        container.addView(createSliderRow(context, "R: $r", 0f, 255f, 1f, r.toFloat(), palette, { "R: ${it.toInt()}" }) {
            r = it.toInt(); refreshPreview()
        })
        container.addView(createSliderRow(context, "G: $g", 0f, 255f, 1f, g.toFloat(), palette, { "G: ${it.toInt()}" }) {
            g = it.toInt(); refreshPreview()
        })
        container.addView(createSliderRow(context, "B: $b", 0f, 255f, 1f, b.toFloat(), palette, { "B: ${it.toInt()}" }) {
            b = it.toInt(); refreshPreview()
        })

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(context.getString(R.string.btn_save)) { _, _ ->
                val parsed = ThemePaletteGenerator.parseHexColor(hexInput.text.toString())
                onColorChosen(parsed ?: Color.rgb(r, g, b))
            }
            .setNegativeButton(context.getString(R.string.btn_cancel), null)
            .show()
    }

    fun createDivider(context: Context, palette: ThemePaletteGenerator.BubblePalette): View {
        return View(context).apply {
            setBackgroundColor(palette.outlineVariant)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 1f)
            ).apply {
                topMargin = dp(context, 10f)
                bottomMargin = dp(context, 10f)
            }
        }
    }

    /**
     * Vector-icon pill button (immune to device/ROM font glyph corruption).
     */
    fun createIconPillButton(
        context: Context,
        @DrawableRes iconRes: Int,
        contentDescriptionText: String,
        variant: ButtonVariant,
        palette: ThemePaletteGenerator.BubblePalette,
        onClick: () -> Unit
    ): MaterialButton {
        val styleAttr = when (variant) {
            ButtonVariant.PRIMARY -> com.google.android.material.R.attr.materialButtonStyle
            ButtonVariant.TONAL -> com.google.android.material.R.attr.materialButtonStyle
            ButtonVariant.OUTLINED -> com.google.android.material.R.attr.materialButtonOutlinedStyle
            ButtonVariant.TEXT -> com.google.android.material.R.attr.borderlessButtonStyle
        }
        val tintColor = when (variant) {
            ButtonVariant.PRIMARY -> palette.onPrimary
            ButtonVariant.TONAL -> palette.onPrimaryContainer
            ButtonVariant.OUTLINED, ButtonVariant.TEXT -> palette.primary
        }
        return MaterialButton(context, null, styleAttr).apply {
            text = ""
            contentDescription = contentDescriptionText
            icon = ContextCompat.getDrawable(context, iconRes)
            iconTint = ColorStateList.valueOf(tintColor)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 0
            iconSize = dp(context, 20f)
            cornerRadius = dp(context, 24f)
            minWidth = dp(context, 48f)
            minHeight = dp(context, 44f)
            insetTop = dp(context, 4f)
            insetBottom = dp(context, 4f)
            setPadding(dp(context, 12f), 0, dp(context, 12f), 0)
            when (variant) {
                ButtonVariant.PRIMARY -> {
                    tag = "btn_primary"
                    backgroundTintList = ColorStateList.valueOf(palette.primary)
                }
                ButtonVariant.TONAL -> {
                    tag = "btn_tonal"
                    backgroundTintList = ColorStateList.valueOf(palette.primaryContainer)
                }
                ButtonVariant.OUTLINED -> {
                    tag = "btn_outlined"
                    strokeColor = ColorStateList.valueOf(palette.outline)
                }
                ButtonVariant.TEXT -> {
                    tag = "btn_text"
                }
            }
            setOnClickListener { onClick() }
        }
    }

    /**
     * Vector "=" drag-to-reorder handle with 44dp accessible touch target.
     */
    fun createDragHandle(
        context: Context,
        palette: ThemePaletteGenerator.BubblePalette
    ): ImageView {
        val pad = dp(context, 10f)
        val size = dp(context, 44f)
        return ImageView(context).apply {
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_drag_handle)?.mutate()
            if (drawable != null) {
                DrawableCompat.setTint(drawable, palette.onSurfaceVariant)
                setImageDrawable(drawable)
            }
            contentDescription = context.getString(R.string.cd_drag_to_reorder)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    private const val TAG_ROW_DIVIDER = "row_divider"

    /**
     * Divider placed at the top of reorderable rows so drag swaps can toggle index-0
     * divider visibility cleanly without visual jumps or orphaned borders.
     */
    fun createRowDivider(
        context: Context,
        palette: ThemePaletteGenerator.BubblePalette,
        visible: Boolean
    ): View {
        return createDivider(context, palette).apply {
            tag = TAG_ROW_DIVIDER
            visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    fun syncRowDividers(listContainer: LinearLayout) {
        for (i in 0 until listContainer.childCount) {
            val child = listContainer.getChildAt(i)
            child.findViewWithTag<View>(TAG_ROW_DIVIDER)?.visibility =
                if (i == 0) View.GONE else View.VISIBLE
        }
    }

    /**
     * Enables smooth vertical drag-to-reorder via the "=" handle inside a vertical LinearLayout
     * (safely disallowing parent NestedScrollView interception while dragging), plus a single-tap
     * "Move to Position" accessible fallback dialog.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun attachDragReorder(
        context: Context,
        listContainer: LinearLayout,
        rowView: View,
        dragHandle: View,
        itemLabel: String,
        onReordered: (fromIndex: Int, toIndex: Int) -> Unit
    ) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val dragElevation = dp(context, 6f).toFloat()
        val containerLoc = IntArray(2)

        fun setParentScrollLocked(locked: Boolean) {
            var p: ViewParent? = listContainer.parent
            while (p != null) {
                p.requestDisallowInterceptTouchEvent(locked)
                p = p.parent
            }
        }

        var initialIndex = -1
        var currentIndex = -1
        var downRawY = 0f
        var pointerOffsetInRow = 0f
        var lastFingerLocalY = 0f
        var didDrag = false

        fun updateRowTranslation(fingerLocalY: Float) {
            lastFingerLocalY = fingerLocalY
            val targetTop = fingerLocalY - pointerOffsetInRow
            rowView.translationY = targetTop - rowView.top
        }

        val layoutSyncListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (didDrag) {
                updateRowTranslation(lastFingerLocalY)
            }
        }

        dragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialIndex = listContainer.indexOfChild(rowView)
                    currentIndex = initialIndex
                    if (initialIndex < 0 || listContainer.childCount <= 1) return@setOnTouchListener false
                    listContainer.getLocationOnScreen(containerLoc)
                    downRawY = event.rawY
                    val fingerLocalY = event.rawY - containerLoc[1]
                    lastFingerLocalY = fingerLocalY
                    pointerOffsetInRow = (fingerLocalY - rowView.top).coerceIn(0f, rowView.height.toFloat().coerceAtLeast(1f))
                    didDrag = false
                    setParentScrollLocked(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (initialIndex < 0) return@setOnTouchListener false
                    if (!didDrag && abs(event.rawY - downRawY) > touchSlop) {
                        didDrag = true
                        rowView.elevation = dragElevation
                        rowView.alpha = 0.94f
                        rowView.scaleX = 1.015f
                        rowView.scaleY = 1.015f
                        rowView.addOnLayoutChangeListener(layoutSyncListener)
                        dragHandle.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                    if (!didDrag) return@setOnTouchListener true

                    listContainer.getLocationOnScreen(containerLoc)
                    val fingerLocalY = event.rawY - containerLoc[1]
                    updateRowTranslation(fingerLocalY)
                    val draggedCenter = rowView.top + rowView.height * 0.5f + rowView.translationY

                    // Check moving down
                    if (currentIndex < listContainer.childCount - 1) {
                        val next = listContainer.getChildAt(currentIndex + 1)
                        val nextMid = next.top + next.height * 0.5f
                        if (draggedCenter > nextMid) {
                            listContainer.removeViewAt(currentIndex)
                            currentIndex++
                            listContainer.addView(rowView, currentIndex)
                            syncRowDividers(listContainer)
                            dragHandle.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }

                    // Check moving up
                    if (currentIndex > 0) {
                        val prev = listContainer.getChildAt(currentIndex - 1)
                        val prevMid = prev.top + prev.height * 0.5f
                        if (draggedCenter < prevMid) {
                            listContainer.removeViewAt(currentIndex)
                            currentIndex--
                            listContainer.addView(rowView, currentIndex)
                            syncRowDividers(listContainer)
                            dragHandle.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    setParentScrollLocked(false)
                    rowView.removeOnLayoutChangeListener(layoutSyncListener)
                    rowView.translationY = 0f
                    rowView.elevation = 0f
                    rowView.alpha = 1f
                    rowView.scaleX = 1f
                    rowView.scaleY = 1f
                    syncRowDividers(listContainer)

                    val from = initialIndex
                    val to = currentIndex
                    val wasDrag = didDrag
                    initialIndex = -1
                    currentIndex = -1
                    didDrag = false

                    if (wasDrag) {
                        if (from >= 0 && to >= 0 && from != to) {
                            onReordered(from, to)
                        }
                    } else if (event.actionMasked == MotionEvent.ACTION_UP && from >= 0 && listContainer.childCount > 1) {
                        // Accessible tap fallback: pick exact target position
                        val total = listContainer.childCount
                        val choices = (1..total).map { pos ->
                            val base = context.getString(R.string.reorder_position_fmt, pos)
                            if (pos - 1 == from) "$base ✓" else base
                        }.toTypedArray()
                        MaterialAlertDialogBuilder(context)
                            .setTitle(context.getString(R.string.dialog_reorder_title, itemLabel))
                            .setItems(choices) { _, picked ->
                                if (picked != from) {
                                    onReordered(from, picked)
                                }
                            }
                            .setNegativeButton(context.getString(R.string.btn_cancel), null)
                            .show()
                    }
                    true
                }

                else -> false
            }
        }
    }
}
