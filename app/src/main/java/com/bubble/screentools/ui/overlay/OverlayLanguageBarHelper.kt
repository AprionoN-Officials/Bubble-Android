package com.bubble.screentools.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.model.ActionMode
import com.bubble.screentools.model.TextBlockResult
import com.bubble.screentools.model.VisualSearchEngine
import com.bubble.screentools.qr.DetectedQrResult
import com.bubble.screentools.qr.QrActionHelper
import com.bubble.screentools.qr.QrCodeParser
import com.bubble.screentools.translation.SupportedLanguages

/**
 * Bilingual (EN/ID) shared controller for Language Bar, Visual Search Engine Pill,
 * Auto QR Card + Payment Open-With Picker, and Full-Screen Live Text Select & Copy mode.
 */
class OverlayLanguageBarHelper(
    private val context: Context,
    rootView: View,
    private val preferences: BubblePreferences,
    private val onLanguagePairChanged: () -> Unit,
    private val onSearchEngineChanged: () -> Unit = {},
    private val onTranslateSelectedBlocksRequested: (List<TextBlockResult>) -> Unit = {},
    private val onDismissOverlayRequested: () -> Unit = {}
) {
    private val topControlsBar: View = rootView.findViewById(R.id.topControlsBar)
    private val bottomActionRow: View = rootView.findViewById(R.id.bottomActionRow)
    private val bottomControlsPanel: View = rootView.findViewById(R.id.bottomControlsPanel)
    private val btnToggleBottomControls: Button = rootView.findViewById(R.id.btnToggleBottomControls)

    private val selectionOverlayView: View = rootView.findViewById(R.id.selectionOverlayView)
    private val selectableTextOverlayView: SelectableTextOverlayView =
        rootView.findViewById(R.id.selectableTextOverlayView)
    private val btnSelectTextMode: Button = rootView.findViewById(R.id.btnSelectTextMode)
    private val primaryActionButtonsRow: View = rootView.findViewById(R.id.primaryActionButtonsRow)

    private val selectedTextCard: View = rootView.findViewById(R.id.selectedTextCard)
    private val tvSelectedTextHeader: TextView = rootView.findViewById(R.id.tvSelectedTextHeader)
    private val tvSelectedTextPreview: TextView = rootView.findViewById(R.id.tvSelectedTextPreview)
    private val btnSelectAllText: View = rootView.findViewById(R.id.btnSelectAllText)
    private val btnTextCopy: View = rootView.findViewById(R.id.btnTextCopy)
    private val btnTextTranslate: View = rootView.findViewById(R.id.btnTextTranslate)
    private val btnTextWebSearch: View = rootView.findViewById(R.id.btnTextWebSearch)

    private val languageBarContainer: View = rootView.findViewById(R.id.languageBarContainer)
    private val btnSourceLang: TextView = rootView.findViewById(R.id.btnSourceLang)
    private val btnSwapLang: TextView = rootView.findViewById(R.id.btnSwapLang)
    private val btnTargetLang: TextView = rootView.findViewById(R.id.btnTargetLang)

    private val searchEngineBarContainer: View = rootView.findViewById(R.id.searchEngineBarContainer)
    private val tvActiveSearchEngine: TextView = rootView.findViewById(R.id.tvActiveSearchEngine)

    private val qrResultCard: View = rootView.findViewById(R.id.qrResultCard)
    private val tvQrBadge: TextView = rootView.findViewById(R.id.tvQrBadge)
    private val tvQrTitle: TextView = rootView.findViewById(R.id.tvQrTitle)
    private val tvQrSubtitle: TextView = rootView.findViewById(R.id.tvQrSubtitle)
    private val btnQrPrimary: TextView = rootView.findViewById(R.id.btnQrPrimaryAction)
    private val btnQrSecondary: TextView = rootView.findViewById(R.id.btnQrSecondaryAction)
    private val btnDismissQr: View = rootView.findViewById(R.id.btnDismissQr)

    private val pickerOverlay: View = rootView.findViewById(R.id.languagePickerOverlay)
    private val pickerTitle: TextView = rootView.findViewById(R.id.tvLanguagePickerTitle)
    private val pickerList: LinearLayout = rootView.findViewById(R.id.languagePickerList)

    var lastDetectedSourceLang: String? = null
    private var activeQr: DetectedQrResult? = null
    private var snapshotProvider: (() -> Bitmap?)? = null
    private var fullScreenTextBlocks: List<TextBlockResult> = emptyList()
    private var currentMode: ActionMode = preferences.lastActionMode
    private var hasTranslationResult: Boolean = false
    private var userManuallyExpandedControls: Boolean = false

    var isTextSelectMode: Boolean = false
        private set

    init {
        refreshLabels()

        btnToggleBottomControls.setOnClickListener {
            userManuallyExpandedControls = !userManuallyExpandedControls
            applyControlsVisibility()
        }

        btnSelectTextMode.setOnClickListener {
            if (isTextSelectMode) {
                exitTextSelectMode()
            } else {
                enterTextSelectMode()
            }
        }

        selectableTextOverlayView.listener = object : SelectableTextOverlayView.OnTextSelectionChangedListener {
            override fun onTextSelectionChanged(selectedBlocks: List<TextBlockResult>, combinedText: String) {
                if (selectedBlocks.isEmpty()) {
                    tvSelectedTextHeader.text = context.getString(
                        R.string.overlay_tap_text_header_fmt,
                        fullScreenTextBlocks.size
                    )
                    tvSelectedTextPreview.text = context.getString(R.string.overlay_tap_text_hint)
                } else {
                    tvSelectedTextHeader.text = context.getString(
                        R.string.overlay_selected_blocks_fmt,
                        selectedBlocks.size
                    )
                    tvSelectedTextPreview.text = combinedText
                }
            }
        }

        btnSelectAllText.setOnClickListener {
            selectableTextOverlayView.selectAll()
        }

        btnTextCopy.setOnClickListener {
            val text = selectableTextOverlayView.getCombinedSelectedText()
            if (text.isBlank()) {
                Toast.makeText(context, context.getString(R.string.overlay_select_text_first), Toast.LENGTH_SHORT).show()
            } else {
                QrActionHelper.copyToClipboard(context, context.getString(R.string.overlay_label_screen_text), text)
            }
        }

        btnTextTranslate.setOnClickListener {
            val selected = selectableTextOverlayView.getSelectedBlocks().ifEmpty { fullScreenTextBlocks }
            if (selected.isEmpty()) {
                Toast.makeText(context, context.getString(R.string.overlay_select_text_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            exitTextSelectMode()
            onTranslateSelectedBlocksRequested(selected)
        }

        btnTextWebSearch.setOnClickListener {
            val text = selectableTextOverlayView.getCombinedSelectedText()
            if (text.isBlank()) {
                Toast.makeText(context, context.getString(R.string.overlay_select_text_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val searchIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${Uri.encode(text)}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val chooser = Intent.createChooser(searchIntent, context.getString(R.string.overlay_pick_app_open_title))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(chooser) }
            onDismissOverlayRequested()
        }

        btnSourceLang.setOnClickListener {
            openLanguagePicker(pickingSource = true)
        }

        btnTargetLang.setOnClickListener {
            openLanguagePicker(pickingSource = false)
        }

        btnSwapLang.setOnClickListener {
            val effectiveSource = if (preferences.autoDetectLanguage) {
                SupportedLanguages.AUTO_CODE
            } else {
                preferences.sourceLanguageCode
            }
            val (newSource, newTarget) = SupportedLanguages.swap(
                currentSourceCode = effectiveSource,
                currentTargetCode = preferences.targetLanguageCode,
                lastDetectedSourceCode = lastDetectedSourceLang
            )
            preferences.sourceLanguageCode = newSource
            preferences.targetLanguageCode = newTarget
            preferences.autoDetectLanguage = (newSource == SupportedLanguages.AUTO_CODE)
            refreshLabels()
            onLanguagePairChanged()
        }

        searchEngineBarContainer.setOnClickListener {
            openSearchEnginePicker()
        }

        btnDismissQr.setOnClickListener {
            hideQrCard()
        }

        btnQrPrimary.setOnClickListener {
            val detected = activeQr ?: return@setOnClickListener
            val parsed = detected.parsed
            if (parsed is QrCodeParser.ParsedQr.Payment && !parsed.isDirectUri) {
                openPaymentAppPicker(detected)
            } else {
                val shouldDismiss = QrActionHelper.executePrimaryAction(
                    context = context,
                    detected = detected,
                    sourceSnapshot = snapshotProvider?.invoke()
                )
                if (shouldDismiss) {
                    onDismissOverlayRequested()
                }
            }
        }

        btnQrSecondary.setOnClickListener {
            val detected = activeQr ?: return@setOnClickListener
            val shouldDismiss = QrActionHelper.executeSecondaryAction(
                context = context,
                detected = detected,
                sourceSnapshot = snapshotProvider?.invoke()
            )
            if (shouldDismiss) {
                onDismissOverlayRequested()
            }
        }

        pickerOverlay.setOnClickListener {
            closePicker()
        }
    }

    fun setDetectedFullScreenTextBlocks(blocks: List<TextBlockResult>) {
        fullScreenTextBlocks = blocks
        if (blocks.isNotEmpty()) {
            btnSelectTextMode.visibility = View.VISIBLE
            btnSelectTextMode.text = if (isTextSelectMode) {
                context.getString(R.string.overlay_done)
            } else {
                context.getString(R.string.overlay_select_text_count, blocks.size)
            }
            if (isTextSelectMode) {
                selectableTextOverlayView.setDetectedBlocks(blocks)
            }
        } else if (!isTextSelectMode) {
            btnSelectTextMode.visibility = View.GONE
        }
    }

    /**
     * Hides overlay top & bottom controls completely while the user is actively dragging
     * or resizing the selection box so no button ever blocks touch or vision!
     */
    fun setDraggingMode(isDragging: Boolean) {
        if (isTextSelectMode) return
        if (isDragging) {
            closePicker()
            topControlsBar.visibility = View.GONE
            bottomActionRow.visibility = View.GONE
        } else {
            topControlsBar.visibility = View.VISIBLE
            bottomActionRow.visibility = View.VISIBLE
            applyControlsVisibility()
        }
    }

    /**
     * Automatically collapses the bottom Translate/Scan panel when translation results
     * are displayed so bottom text is completely unobstructed; user can tap "Menu" in the
     * top bar anytime to bring the bottom controls back.
     */
    fun setTranslationResultVisible(visible: Boolean) {
        hasTranslationResult = visible
        if (visible) {
            userManuallyExpandedControls = false
        }
        applyControlsVisibility()
    }

    private fun applyControlsVisibility() {
        if (isTextSelectMode) {
            bottomControlsPanel.visibility = View.GONE
            btnToggleBottomControls.visibility = View.GONE
            return
        }

        val showBottomPanel = !hasTranslationResult || userManuallyExpandedControls
        bottomControlsPanel.visibility = if (showBottomPanel) View.VISIBLE else View.GONE
        languageBarContainer.visibility =
            if (currentMode == ActionMode.TRANSLATE) View.VISIBLE else View.GONE
        searchEngineBarContainer.visibility =
            if (currentMode == ActionMode.VISUAL_SEARCH) View.VISIBLE else View.GONE
        primaryActionButtonsRow.visibility = View.VISIBLE

        if (hasTranslationResult) {
            btnToggleBottomControls.visibility = View.VISIBLE
            btnToggleBottomControls.setBackgroundResource(
                if (userManuallyExpandedControls) R.drawable.bg_action_button_active
                else R.drawable.bg_action_button_inactive
            )
        } else {
            btnToggleBottomControls.visibility = View.GONE
        }
    }

    fun enterTextSelectMode() {
        if (fullScreenTextBlocks.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.overlay_no_text_to_select), Toast.LENGTH_SHORT).show()
            return
        }
        isTextSelectMode = true
        closePicker()
        topControlsBar.visibility = View.VISIBLE
        bottomActionRow.visibility = View.VISIBLE
        selectionOverlayView.visibility = View.INVISIBLE
        selectableTextOverlayView.visibility = View.VISIBLE
        selectableTextOverlayView.setDetectedBlocks(fullScreenTextBlocks)
        selectedTextCard.visibility = View.VISIBLE
        qrResultCard.visibility = View.GONE
        applyControlsVisibility()
        btnSelectTextMode.text = context.getString(R.string.overlay_done)
        btnSelectTextMode.setBackgroundResource(R.drawable.bg_action_button_active)
    }

    fun exitTextSelectMode(): Boolean {
        if (!isTextSelectMode) return false
        isTextSelectMode = false
        selectableTextOverlayView.clearSelection()
        selectableTextOverlayView.visibility = View.GONE
        selectionOverlayView.visibility = View.VISIBLE
        selectedTextCard.visibility = View.GONE
        if (activeQr != null) {
            qrResultCard.visibility = View.VISIBLE
        }
        applyControlsVisibility()
        btnSelectTextMode.text = if (fullScreenTextBlocks.isNotEmpty()) {
            context.getString(R.string.overlay_select_text_count, fullScreenTextBlocks.size)
        } else {
            context.getString(R.string.overlay_select_text)
        }
        btnSelectTextMode.setBackgroundResource(R.drawable.bg_action_button_inactive)
        return true
    }

    fun setVisibleForMode(mode: ActionMode) {
        currentMode = mode
        applyControlsVisibility()
        closePicker()
    }

    fun refreshLabels() {
        val effectiveSource = if (preferences.autoDetectLanguage) {
            SupportedLanguages.AUTO_CODE
        } else {
            preferences.sourceLanguageCode
        }
        btnSourceLang.text = SupportedLanguages.shortLabelFor(effectiveSource)
        btnTargetLang.text = SupportedLanguages.shortLabelFor(preferences.targetLanguageCode)
        tvActiveSearchEngine.text = context.getString(
            R.string.overlay_engine_pill_fmt,
            preferences.visualSearchEngine.shortLabel
        )
    }

    fun showDetectedQr(detected: DetectedQrResult, getSnapshot: () -> Bitmap?) {
        activeQr = detected
        snapshotProvider = getSnapshot
        val parsed = detected.parsed
        val labels = QrActionHelper.getActionLabels(parsed)

        tvQrBadge.text = parsed.badgeLabel
        tvQrTitle.text = parsed.title
        tvQrSubtitle.text = parsed.subtitle
        btnQrPrimary.text = labels.primaryLabel
        btnQrSecondary.text = labels.secondaryLabel
        if (!isTextSelectMode) {
            qrResultCard.visibility = View.VISIBLE
        }
    }

    fun hideQrCard() {
        activeQr = null
        snapshotProvider = null
        qrResultCard.visibility = View.GONE
    }

    fun closePicker(): Boolean {
        if (pickerOverlay.visibility != View.VISIBLE) return false
        pickerOverlay.visibility = View.GONE
        return true
    }

    private fun openPaymentAppPicker(detected: DetectedQrResult) {
        val installedApps = QrActionHelper.getInstalledPaymentApps(context)
        if (installedApps.isEmpty()) {
            if (QrActionHelper.openQrWithAndroidOpenWith(context, detected, snapshotProvider?.invoke())) {
                onDismissOverlayRequested()
            }
            return
        }

        pickerTitle.text = context.getString(R.string.overlay_pick_app_open_title)
        pickerList.removeAllViews()
        val density = context.resources.displayMetrics.density
        val padH = (10 * density).toInt()
        val padV = (10 * density).toInt()
        val iconSize = (32 * density).toInt()

        for (app in installedApps) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padH, padV, padH, padV)
                setOnClickListener {
                    closePicker()
                    val launched = QrActionHelper.openQrisInSpecificApp(
                        context = context,
                        app = app,
                        detected = detected,
                        sourceSnapshot = snapshotProvider?.invoke()
                    )
                    if (launched) {
                        onDismissOverlayRequested()
                    }
                }
            }
            if (app.icon != null) {
                row.addView(ImageView(context).apply {
                    setImageDrawable(app.icon)
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        marginEnd = (12 * density).toInt()
                    }
                })
            }
            row.addView(TextView(context).apply {
                text = app.appName
                textSize = 14.5f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            })
            pickerList.addView(row)
        }

        val otherRow = TextView(context).apply {
            text = context.getString(R.string.overlay_other_android_apps)
            textSize = 13.5f
            setTextColor(Color.parseColor("#64B5F6"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(padH, (14 * density).toInt(), padH, padV)
            setOnClickListener {
                closePicker()
                if (QrActionHelper.openQrWithAndroidOpenWith(context, detected, snapshotProvider?.invoke())) {
                    onDismissOverlayRequested()
                }
            }
        }
        pickerList.addView(otherRow)

        pickerOverlay.visibility = View.VISIBLE
    }

    private fun openLanguagePicker(pickingSource: Boolean) {
        pickerTitle.text = if (pickingSource) {
            context.getString(R.string.dialog_pick_source_lang)
        } else {
            context.getString(R.string.dialog_pick_target_lang)
        }

        val options = if (pickingSource) {
            SupportedLanguages.SOURCE_LANGUAGES
        } else {
            SupportedLanguages.TARGET_LANGUAGES
        }
        val selectedCode = if (pickingSource) {
            if (preferences.autoDetectLanguage) SupportedLanguages.AUTO_CODE else preferences.sourceLanguageCode
        } else {
            preferences.targetLanguageCode
        }

        pickerList.removeAllViews()
        val density = context.resources.displayMetrics.density
        val padH = (12 * density).toInt()
        val padV = (11 * density).toInt()

        for (option in options) {
            val isSelected = option.code.equals(selectedCode, ignoreCase = true)
            val row = TextView(context).apply {
                text = if (isSelected) "✓  ${option.displayName}" else option.displayName
                textSize = 14f
                setTextColor(if (isSelected) Color.parseColor("#64B5F6") else Color.WHITE)
                setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                setPadding(padH, padV, padH, padV)
                setOnClickListener {
                    if (pickingSource) {
                        preferences.sourceLanguageCode = option.code
                        preferences.autoDetectLanguage = (option.code == SupportedLanguages.AUTO_CODE)
                    } else {
                        preferences.targetLanguageCode = option.code
                    }
                    refreshLabels()
                    closePicker()
                    onLanguagePairChanged()
                }
            }
            pickerList.addView(row)
        }

        pickerOverlay.visibility = View.VISIBLE
    }

    private fun openSearchEnginePicker() {
        pickerTitle.text = context.getString(R.string.overlay_pick_engine_title)
        pickerList.removeAllViews()
        val density = context.resources.displayMetrics.density
        val padH = (12 * density).toInt()
        val padV = (12 * density).toInt()

        for (engine in VisualSearchEngine.entries) {
            val isSelected = (engine == preferences.visualSearchEngine)
            val row = TextView(context).apply {
                text = if (isSelected) "✓  ${engine.displayName}" else engine.displayName
                textSize = 14f
                setTextColor(if (isSelected) Color.parseColor("#64B5F6") else Color.WHITE)
                setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                setPadding(padH, padV, padH, padV)
                setOnClickListener {
                    preferences.visualSearchEngine = engine
                    refreshLabels()
                    closePicker()
                    onSearchEngineChanged()
                }
            }
            pickerList.addView(row)
        }
        pickerOverlay.visibility = View.VISIBLE
    }
}
