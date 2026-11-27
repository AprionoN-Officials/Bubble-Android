package com.bubble.screentools.ui.main.tabs

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.lens.SearchEngineRegistry
import com.bubble.screentools.model.SearchEngineItem
import com.bubble.screentools.model.SearchEngineType
import com.bubble.screentools.model.SearchImageFormat
import com.bubble.screentools.model.SearchImageMethod
import com.bubble.screentools.model.SearchImageQuality
import com.bubble.screentools.theme.ThemePaletteGenerator
import com.bubble.screentools.ui.main.UiKit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Tab 2 — Search / Pencarian:
 * - Visual Search Engine Manager (Enable/Disable, Default, Drag "=" handle reorder, Add/Edit/Delete Custom Visual Engine)
 * - Selected-Text Search Engine Manager (Default, Enable/Disable, Drag "=" handle reorder, Custom Text URL Template)
 * - Image Pipeline Optimization (Original/Balanced/Data Saver, Max Resolution, Compression, Format, Confirm Before Upload, External Browser)
 */
object SearchTabBuilder {

    interface Callbacks {
        fun onChanged()
        fun onRequestCustomEngineGate(action: () -> Unit)
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
        val registry = SearchEngineRegistry(preferences)

        // 1. Visual Search Engines Card
        val (visualCard, visualBox) = UiKit.createExpressiveCard(context, palette)
        visualBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_visual_search), palette))
        visualBox.addView(UiKit.createSubtitle(context, context.getString(R.string.visual_search_subtitle), palette))

        val visualEngines = registry.getVisualEngines(enabledOnly = false)
        val defaultVisualId = preferences.defaultVisualEngineId
        val visualListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        visualEngines.forEachIndexed { idx, engine ->
            val isDefault = engine.id == defaultVisualId
            val subtitle = when (engine.imageMethod) {
                SearchImageMethod.GOOGLE_LENS_NATIVE -> "Native App Intent (Fallback: Open With)"
                SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD -> engine.baseUrl
                SearchImageMethod.ANDROID_OPEN_WITH -> "Android System Chooser"
                SearchImageMethod.CUSTOM_INTENT_PACKAGE -> "App: ${engine.targetPackage}"
                SearchImageMethod.BROWSER_URL_ONLY -> "Browser: ${engine.baseUrl}"
            }

            val rowWrapper = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(palette.surfaceContainer)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rowWrapper.addView(UiKit.createRowDivider(context, palette, visible = idx > 0))

            val contentRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val dragHandle = UiKit.createDragHandle(context, palette).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f)).apply {
                    marginEnd = dp(4f)
                }
            }
            contentRow.addView(dragHandle)

            val rightColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            rightColumn.addView(
                UiKit.createSwitchRow(
                    context = context,
                    label = if (isDefault) "${engine.name}  ★" else engine.name,
                    checked = engine.enabled,
                    palette = palette,
                    subtitle = subtitle
                ) { enabled ->
                    if (!registry.setEngineEnabled(engine.id, enabled)) {
                        Toast.makeText(context, "At least one visual search engine must stay enabled.", Toast.LENGTH_SHORT).show()
                    }
                    callbacks.onChanged()
                }
            )
            val actionRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            actionRow.addView(
                UiKit.createPillButton(
                    context,
                    if (isDefault) context.getString(R.string.label_default_badge) else context.getString(R.string.btn_set_default),
                    if (isDefault) UiKit.ButtonVariant.TONAL else UiKit.ButtonVariant.OUTLINED,
                    palette
                ) {
                    preferences.defaultVisualEngineId = engine.id
                    preferences.lastUsedVisualEngineId = engine.id
                    callbacks.onChanged()
                }
            )
            if (!engine.isBuiltIn) {
                actionRow.addView(UiKit.createPillButton(context, context.getString(R.string.btn_edit), UiKit.ButtonVariant.TEXT, palette) {
                    callbacks.onRequestCustomEngineGate {
                        showCustomEngineDialog(context, engine, SearchEngineType.VISUAL, registry) { callbacks.onChanged() }
                    }
                })
                actionRow.addView(UiKit.createPillButton(context, context.getString(R.string.btn_delete), UiKit.ButtonVariant.TEXT, palette) {
                    registry.deleteCustomEngine(engine.id)
                    callbacks.onChanged()
                })
            }
            rightColumn.addView(actionRow)
            contentRow.addView(rightColumn)
            rowWrapper.addView(contentRow)

            UiKit.attachDragReorder(
                context = context,
                listContainer = visualListContainer,
                rowView = rowWrapper,
                dragHandle = dragHandle,
                itemLabel = engine.name
            ) { _, toIdx ->
                if (registry.reorderEngine(engine.id, toIdx)) {
                    callbacks.onChanged()
                }
            }
            visualListContainer.addView(rowWrapper)
        }
        visualBox.addView(visualListContainer)

        visualBox.addView(
            UiKit.createPillButton(context, "+ ${context.getString(R.string.btn_add_custom_engine)}", UiKit.ButtonVariant.TONAL, palette) {
                callbacks.onRequestCustomEngineGate {
                    showCustomEngineDialog(context, null, SearchEngineType.VISUAL, registry) { callbacks.onChanged() }
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(12f) }
            }
        )
        container.addView(visualCard)

        // 2. Selected-Text Search Engines Card
        val (textCard, textBox) = UiKit.createExpressiveCard(context, palette)
        textBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_text_search), palette))
        textBox.addView(UiKit.createSubtitle(context, context.getString(R.string.text_search_subtitle), palette))

        val textEngines = registry.getTextEngines(enabledOnly = false)
        val defaultTextId = preferences.defaultTextEngineId
        val textListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        textEngines.forEachIndexed { idx, engine ->
            val isDefault = engine.id == defaultTextId
            val rowWrapper = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(palette.surfaceContainer)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            rowWrapper.addView(UiKit.createRowDivider(context, palette, visible = idx > 0))

            val contentRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val dragHandle = UiKit.createDragHandle(context, palette).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f)).apply {
                    marginEnd = dp(4f)
                }
            }
            contentRow.addView(dragHandle)

            val rightColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            rightColumn.addView(
                UiKit.createSwitchRow(
                    context = context,
                    label = if (isDefault) "${engine.name}  ★" else engine.name,
                    checked = engine.enabled,
                    palette = palette,
                    subtitle = engine.textQueryTemplate
                ) { enabled ->
                    registry.setEngineEnabled(engine.id, enabled)
                    callbacks.onChanged()
                }
            )
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(
                UiKit.createPillButton(
                    context,
                    if (isDefault) context.getString(R.string.label_default_badge) else context.getString(R.string.btn_set_default),
                    if (isDefault) UiKit.ButtonVariant.TONAL else UiKit.ButtonVariant.OUTLINED,
                    palette
                ) {
                    preferences.defaultTextEngineId = engine.id
                    callbacks.onChanged()
                }
            )
            if (!engine.isBuiltIn) {
                row.addView(UiKit.createPillButton(context, context.getString(R.string.btn_edit), UiKit.ButtonVariant.TEXT, palette) {
                    callbacks.onRequestCustomEngineGate {
                        showCustomEngineDialog(context, engine, SearchEngineType.TEXT, registry) { callbacks.onChanged() }
                    }
                })
                row.addView(UiKit.createPillButton(context, context.getString(R.string.btn_delete), UiKit.ButtonVariant.TEXT, palette) {
                    registry.deleteCustomEngine(engine.id)
                    callbacks.onChanged()
                })
            }
            rightColumn.addView(row)
            contentRow.addView(rightColumn)
            rowWrapper.addView(contentRow)

            UiKit.attachDragReorder(
                context = context,
                listContainer = textListContainer,
                rowView = rowWrapper,
                dragHandle = dragHandle,
                itemLabel = engine.name
            ) { _, toIdx ->
                if (registry.reorderEngine(engine.id, toIdx)) {
                    callbacks.onChanged()
                }
            }
            textListContainer.addView(rowWrapper)
        }
        textBox.addView(textListContainer)

        textBox.addView(
            UiKit.createPillButton(context, "+ ${context.getString(R.string.btn_add_custom_engine)}", UiKit.ButtonVariant.OUTLINED, palette) {
                callbacks.onRequestCustomEngineGate {
                    showCustomEngineDialog(context, null, SearchEngineType.TEXT, registry) { callbacks.onChanged() }
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(10f) }
            }
        )
        container.addView(textCard)

        // 3. Search Pipeline Optimization Card
        val (optCard, optBox) = UiKit.createExpressiveCard(context, palette)
        optBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_search_optimization), palette))

        optBox.addView(UiKit.createLabel(context, context.getString(R.string.label_image_quality), palette))
        optBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    SearchImageQuality.ORIGINAL to context.getString(R.string.quality_original),
                    SearchImageQuality.BALANCED to context.getString(R.string.quality_balanced),
                    SearchImageQuality.DATA_SAVER to context.getString(R.string.quality_data_saver)
                ),
                selected = preferences.searchImageQuality,
                palette = palette
            ) { quality ->
                preferences.searchImageQuality = quality
                preferences.searchMaxResolutionPx = quality.maxDimensionPx
                preferences.searchCompressionQuality = quality.jpegQuality
                callbacks.onChanged()
            }
        )

        optBox.addView(UiKit.createLabel(context, context.getString(R.string.label_image_format), palette))
        optBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    SearchImageFormat.AUTO to "Auto",
                    SearchImageFormat.PNG to "PNG",
                    SearchImageFormat.JPEG to "JPEG",
                    SearchImageFormat.WEBP to "WebP"
                ),
                selected = preferences.searchImageFormat,
                palette = palette
            ) {
                preferences.searchImageFormat = it
            }
        )

        optBox.addView(
            UiKit.createSliderRow(
                context = context,
                initialLabel = context.getString(R.string.label_max_resolution_fmt, preferences.searchMaxResolutionPx),
                valueFrom = 480f,
                valueTo = 2400f,
                stepSize = 120f,
                currentValue = preferences.searchMaxResolutionPx.toFloat(),
                palette = palette,
                formatLabel = { context.getString(R.string.label_max_resolution_fmt, it.toInt()) }
            ) { preferences.searchMaxResolutionPx = it.toInt() }
        )

        optBox.addView(
            UiKit.createSliderRow(
                context = context,
                initialLabel = context.getString(R.string.label_compression_fmt, preferences.searchCompressionQuality),
                valueFrom = 50f,
                valueTo = 100f,
                stepSize = 2f,
                currentValue = preferences.searchCompressionQuality.toFloat(),
                palette = palette,
                formatLabel = { context.getString(R.string.label_compression_fmt, it.toInt()) }
            ) { preferences.searchCompressionQuality = it.toInt() }
        )

        optBox.addView(UiKit.createDivider(context, palette))
        optBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_auto_visual_search), preferences.autoSearchAfterSelection, palette) {
            preferences.autoSearchAfterSelection = it
            callbacks.onChanged()
        })

        // Visual Search Auto-Delay: built-in presets (1, 2, 5, 7, 10s) + Custom input
        val delayPresetsMs = listOf(1_000L, 2_000L, 5_000L, 7_000L, 10_000L)
        val currentDelayMs = preferences.visualSearchDelayMs
        val isCustomDelay = currentDelayMs !in delayPresetsMs
        val customChipLabel = if (isCustomDelay) {
            context.getString(R.string.delay_custom_value_fmt, formatSecondsLabel(currentDelayMs))
        } else {
            context.getString(R.string.delay_custom)
        }
        val delayItems = listOf(
            1_000L to context.getString(R.string.delay_seconds_fmt, "1"),
            2_000L to context.getString(R.string.delay_seconds_fmt, "2"),
            5_000L to context.getString(R.string.delay_seconds_fmt, "5"),
            7_000L to context.getString(R.string.delay_seconds_fmt, "7"),
            10_000L to context.getString(R.string.delay_seconds_fmt, "10"),
            -1L to customChipLabel
        )
        optBox.addView(UiKit.createLabel(context, context.getString(R.string.label_visual_search_delay), palette))
        optBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = delayItems,
                selected = if (isCustomDelay) -1L else currentDelayMs,
                palette = palette,
                scrollable = true
            ) { pickedMs ->
                if (pickedMs == -1L) {
                    showCustomDelayDialog(context, preferences.visualSearchDelayMs) { savedMs ->
                        preferences.visualSearchDelayMs = savedMs
                        callbacks.onChanged()
                    }
                } else {
                    preferences.visualSearchDelayMs = pickedMs
                    callbacks.onChanged()
                }
            }
        )

        optBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_remember_last_engine), preferences.rememberLastSearchEngine, palette) {
            preferences.rememberLastSearchEngine = it
        })
        optBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_confirm_upload), preferences.confirmBeforeImageUpload, palette) {
            preferences.confirmBeforeImageUpload = it
        })
        optBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_open_external_browser), preferences.openSearchInExternalBrowser, palette) {
            preferences.openSearchInExternalBrowser = it
        })
        optBox.addView(
            UiKit.createSwitchRow(
                context = context,
                label = context.getString(R.string.switch_web_adblocker),
                checked = preferences.webAdBlockerEnabled,
                palette = palette,
                subtitle = context.getString(R.string.web_adblocker_subtitle)
            ) {
                preferences.webAdBlockerEnabled = it
            }
        )

        optBox.addView(
            UiKit.createPillButton(context, context.getString(R.string.btn_reset_default), UiKit.ButtonVariant.TEXT, palette) {
                registry.resetToDefaults()
                preferences.searchImageQuality = SearchImageQuality.BALANCED
                preferences.searchImageFormat = SearchImageFormat.AUTO
                preferences.searchMaxResolutionPx = SearchImageQuality.BALANCED.maxDimensionPx
                preferences.searchCompressionQuality = SearchImageQuality.BALANCED.jpegQuality
                preferences.visualSearchDelayMs = BubblePreferences.DEFAULT_VISUAL_SEARCH_DELAY_MS
                preferences.confirmBeforeImageUpload = false
                preferences.openSearchInExternalBrowser = false
                preferences.webAdBlockerEnabled = true
                callbacks.onChanged()
            }
        )
        container.addView(optCard)
    }

    private fun formatSecondsLabel(ms: Long): String {
        val sec = ms / 1000.0
        return if (ms % 1000L == 0L) {
            (ms / 1000L).toString()
        } else {
            String.format(Locale.US, "%.1f", sec).trimEnd('0').trimEnd('.')
        }
    }

    private fun showCustomDelayDialog(
        context: Context,
        currentMs: Long,
        onSaved: (Long) -> Unit
    ) {
        val dp = { v: Float -> UiKit.dp(context, v) }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(14f), dp(20f), dp(8f))
        }
        val etSeconds = EditText(context).apply {
            hint = context.getString(R.string.dialog_custom_delay_hint)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(formatSecondsLabel(currentMs))
            setSelection(text.length)
        }
        box.addView(etSeconds)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.dialog_custom_delay_title))
            .setView(box)
            .setPositiveButton(context.getString(R.string.btn_save)) { _, _ ->
                val raw = etSeconds.text.toString().trim().replace(',', '.')
                val parsedSec = raw.toDoubleOrNull()
                if (parsedSec == null || !parsedSec.isFinite() || parsedSec < 0.0 || parsedSec > 60.0) {
                    Toast.makeText(context, context.getString(R.string.toast_invalid_delay), Toast.LENGTH_SHORT).show()
                } else {
                    onSaved((parsedSec * 1000.0).roundToLong().coerceIn(0L, BubblePreferences.MAX_VISUAL_SEARCH_DELAY_MS))
                }
            }
            .setNegativeButton(context.getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showCustomEngineDialog(
        context: Context,
        existing: SearchEngineItem?,
        type: SearchEngineType,
        registry: SearchEngineRegistry,
        onSaved: () -> Unit
    ) {
        val dp = { v: Float -> UiKit.dp(context, v) }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(14f), dp(20f), dp(8f))
        }
        val etName = EditText(context).apply {
            hint = "Engine Name"
            setText(existing?.name.orEmpty())
        }
        val etUrl = EditText(context).apply {
            hint = if (type == SearchEngineType.TEXT) {
                "Query URL Template (must contain {query})"
            } else {
                "Base URL (https://...) for web upload"
            }
            setText(if (type == SearchEngineType.TEXT) existing?.textQueryTemplate.orEmpty() else existing?.baseUrl.orEmpty())
        }
        val etPackage = EditText(context).apply {
            hint = "Optional Android Package for direct image intent (leave blank for web)"
            setText(existing?.targetPackage.orEmpty())
        }
        box.addView(etName)
        box.addView(etUrl)
        if (type == SearchEngineType.VISUAL) {
            box.addView(TextView(context).apply {
                text = "Note: Web visual engines open in WebView for standard file upload (no CAPTCHA bypass). Or enter a compatible Android app package name."
                textSize = 11.5f
                alpha = 0.75f
                setPadding(0, dp(6f), 0, dp(6f))
            })
            box.addView(etPackage)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(existing?.name ?: context.getString(R.string.btn_add_custom_engine))
            .setView(box)
            .setPositiveButton(context.getString(R.string.btn_save)) { _, _ ->
                val pkg = etPackage.text.toString().trim()
                val url = etUrl.text.toString().trim()
                val method = when {
                    type == SearchEngineType.TEXT -> SearchImageMethod.BROWSER_URL_ONLY
                    pkg.isNotEmpty() -> SearchImageMethod.CUSTOM_INTENT_PACKAGE
                    else -> SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD
                }
                val item = SearchEngineItem(
                    id = existing?.id.orEmpty(),
                    name = etName.text.toString().trim(),
                    type = type,
                    baseUrl = if (type == SearchEngineType.VISUAL) url else url.replace("{query}", ""),
                    textQueryTemplate = if (type == SearchEngineType.TEXT) url else "",
                    imageMethod = method,
                    targetPackage = pkg,
                    enabled = true,
                    isBuiltIn = false
                )
                val res = registry.addOrUpdateCustomEngine(item)
                res.onSuccess {
                    onSaved()
                }.onFailure { err ->
                    Toast.makeText(context, err.message ?: "Invalid engine configuration.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(context.getString(R.string.btn_cancel), null)
            .show()
    }
}
