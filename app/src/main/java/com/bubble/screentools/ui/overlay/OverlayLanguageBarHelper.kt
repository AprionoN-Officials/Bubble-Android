package com.bubble.screentools.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
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
import com.bubble.screentools.qr.DetectedQrResult
import com.bubble.screentools.qr.QrActionHelper
import com.bubble.screentools.qr.QrCodeParser
import com.bubble.screentools.translation.SupportedLanguages
import com.bubble.screentools.util.ScreenLinkDetector

/**
 * Bilingual (EN/ID) shared controller for Language Bar, Visual Search Engine Pill,
 * Auto QR Card + Payment Open-With Picker, Auto Link Detection Card (non-blocking, yields to QR),
 * and Full-Screen Live Text & Smart Link Select mode.
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
    private val btnFullScreenSelect: View = rootView.findViewById(R.id.btnFullScreenSelect)
    private val btnSelectTextMode: Button = rootView.findViewById(R.id.btnSelectTextMode)
    private val primaryActionButtonsRow: View = rootView.findViewById(R.id.primaryActionButtonsRow)

    private val selectedTextCard: View = rootView.findViewById(R.id.selectedTextCard)
    private val tvSelectedTextHeader: TextView = rootView.findViewById(R.id.tvSelectedTextHeader)
    private val tvSelectedTextPreview: TextView = rootView.findViewById(R.id.tvSelectedTextPreview)
    private val btnSelectAllText: View = rootView.findViewById(R.id.btnSelectAllText)
    private val btnTextCopy: TextView = rootView.findViewById(R.id.btnTextCopy)
    private val btnTextTranslate: TextView = rootView.findViewById(R.id.btnTextTranslate)
    private val btnTextWebSearch: TextView = rootView.findViewById(R.id.btnTextWebSearch)

    private val languageBarContainer: View = rootView.findViewById(R.id.languageBarContainer)
    private val btnSourceLang: TextView = rootView.findViewById(R.id.btnSourceLang)
    private val btnSwapLang: View = rootView.findViewById(R.id.btnSwapLang)
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

    private val linkResultCard: View = rootView.findViewById(R.id.linkResultCard)
    private val tvLinkTitle: TextView = rootView.findViewById(R.id.tvLinkTitle)
    private val tvLinkSubtitle: TextView = rootView.findViewById(R.id.tvLinkSubtitle)
    private val btnLinkPrimary: TextView = rootView.findViewById(R.id.btnLinkPrimaryAction)
    private val btnLinkSecondary: TextView = rootView.findViewById(R.id.btnLinkSecondaryAction)
    private val btnDismissLink: View = rootView.findViewById(R.id.btnDismissLink)

    private val pickerOverlay: View = rootView.findViewById(R.id.languagePickerOverlay)
    private val pickerTitle: TextView = rootView.findViewById(R.id.tvLanguagePickerTitle)
    private val pickerList: LinearLayout = rootView.findViewById(R.id.languagePickerList)

    var lastDetectedSourceLang: String? = null
    private var activeQr: DetectedQrResult? = null
    private var snapshotProvider: (() -> Bitmap?)? = null
    private var detectedLinks: List<ScreenLinkDetector.DetectedLink> = emptyList()
    private var userDismissedLinkCard: Boolean = false
    private var selectedLinks: List<ScreenLinkDetector.DetectedLink> = emptyList()
    private var fullScreenTextBlocks: List<TextBlockResult> = emptyList()
    private var currentMode: ActionMode = preferences.lastActionMode
    private var hasTranslationResult: Boolean = false
    private var userManuallyExpandedControls: Boolean = false

    var isTextSelectMode: Boolean = false
        private set

    init {
        refreshLabels()
        tvSelectedTextPreview.movementMethod = LinkMovementMethod.getInstance()

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
                selectedLinks = ScreenLinkDetector.extractLinks(combinedText)
                if (selectedBlocks.isEmpty()) {
                    tvSelectedTextHeader.text = context.getString(
                        R.string.overlay_tap_text_header_fmt,
                        fullScreenTextBlocks.size
                    )
                    tvSelectedTextPreview.text = context.getString(R.string.overlay_tap_text_hint)
                    btnTextCopy.text = context.getString(R.string.overlay_copy)
                    btnTextWebSearch.text = context.getString(R.string.overlay_web_search)
                } else if (selectedLinks.isNotEmpty()) {
                    tvSelectedTextHeader.text = context.getString(
                        R.string.overlay_selected_links_fmt,
                        selectedBlocks.size,
                        selectedLinks.size
                    )
                    val allOccurrences = ScreenLinkDetector.extractAllLinkOccurrences(combinedText)
                    tvSelectedTextPreview.text = buildClickableLinkPreview(combinedText, allOccurrences)
                    btnTextCopy.text = if (ScreenLinkDetector.isPrimarilyLink(combinedText)) {
                        context.getString(R.string.overlay_copy_link)
                    } else {
                        context.getString(R.string.overlay_copy)
                    }
                    btnTextWebSearch.text = context.getString(R.string.overlay_open_link)
                } else {
                    tvSelectedTextHeader.text = context.getString(
                        R.string.overlay_selected_blocks_fmt,
                        selectedBlocks.size
                    )
                    tvSelectedTextPreview.text = combinedText
                    btnTextCopy.text = context.getString(R.string.overlay_copy)
                    btnTextWebSearch.text = context.getString(R.string.overlay_web_search)
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
                return@setOnClickListener
            }
            if (selectedLinks.isNotEmpty() && ScreenLinkDetector.isPrimarilyLink(text)) {
                val linksPayload = selectedLinks.joinToString("\n") { it.normalizedUrl }
                QrActionHelper.copyToClipboard(context, context.getString(R.string.overlay_label_link), linksPayload)
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
            // When links are detected in the selection, this button acts as "Open Link" instead of Web Search
            if (selectedLinks.isNotEmpty()) {
                handleOpenDetectedLinks(selectedLinks)
                return@setOnClickListener
            }
            val registry = com.bubble.screentools.lens.SearchEngineRegistry(preferences)
            val activeTextEngine = registry.getActiveTextEngine()
            val url = com.bubble.screentools.lens.SearchEngineRegistry.buildTextSearchUrl(activeTextEngine, text)
                ?: "https://www.google.com/search?q=${Uri.encode(text)}"
            val searchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

        btnDismissLink.setOnClickListener {
            userDismissedLinkCard = true
            updateFloatingCardsVisibility()
        }

        tvLinkSubtitle.setOnClickListener {
            if (detectedLinks.size > 1) {
                openLinkPicker(detectedLinks)
            }
        }

        btnLinkPrimary.setOnClickListener {
            if (detectedLinks.isNotEmpty()) {
                handleOpenDetectedLinks(detectedLinks)
            }
        }

        btnLinkSecondary.setOnClickListener {
            val primary = detectedLinks.firstOrNull() ?: return@setOnClickListener
            QrActionHelper.copyToClipboard(
                context,
                context.getString(R.string.overlay_label_link),
                primary.normalizedUrl
            )
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
            if (preferences.autoLinkDetectEnabled) {
                val links = ScreenLinkDetector.extractLinksFromBlocks(blocks)
                if (links.isNotEmpty()) {
                    showDetectedLinks(links, resetDismiss = false)
                }
            }
        } else if (!isTextSelectMode) {
            btnSelectTextMode.visibility = View.GONE
        }
    }

    /**
     * Checks whether the user's selected region intersects detected links and surfaces
     * them cleanly on the non-blocking link card (yielding to QR if present).
     */
    fun updateLinksForRegion(regionBounds: RectF) {
        if (!preferences.autoLinkDetectEnabled || fullScreenTextBlocks.isEmpty()) return
        val intersecting = fullScreenTextBlocks.filter { RectF.intersects(it.boundingBox, regionBounds) }
        val regionLinks = ScreenLinkDetector.extractLinksFromBlocks(intersecting)
        if (regionLinks.isNotEmpty()) {
            showDetectedLinks(regionLinks, resetDismiss = true)
        }
    }

    fun showDetectedLinks(links: List<ScreenLinkDetector.DetectedLink>, resetDismiss: Boolean = false) {
        if (!preferences.autoLinkDetectEnabled || links.isEmpty()) return
        detectedLinks = links
        if (resetDismiss) {
            userDismissedLinkCard = false
        }
        val first = links.first()
        tvLinkTitle.text = context.getString(R.string.overlay_link_found_title)
        tvLinkSubtitle.text = if (links.size == 1) {
            first.displayUrl
        } else {
            context.getString(R.string.overlay_link_more_fmt, first.displayUrl, links.size - 1)
        }
        updateFloatingCardsVisibility()
    }

    /**
     * Merges extra links (e.g. from AssistStructure / Accessibility tree on social apps like Facebook)
     * into the non-blocking link card without duplicating already-detected OCR links.
     */
    fun mergeExtraDetectedLinks(extraLinks: List<ScreenLinkDetector.DetectedLink>) {
        if (!preferences.autoLinkDetectEnabled || extraLinks.isEmpty()) return
        val seen = HashSet<String>(detectedLinks.size + extraLinks.size)
        val merged = ArrayList<ScreenLinkDetector.DetectedLink>(detectedLinks.size + extraLinks.size)
        for (link in detectedLinks) {
            if (seen.add(link.normalizedUrl.lowercase().trimEnd('/'))) {
                merged.add(link)
            }
        }
        for (link in extraLinks) {
            if (seen.add(link.normalizedUrl.lowercase().trimEnd('/'))) {
                merged.add(link)
            }
        }
        if (merged.size > detectedLinks.size) {
            showDetectedLinks(merged, resetDismiss = false)
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
     * Automatically collapses the bottom Translate/Scan panel (and floating cards) when translation
     * results are displayed so bottom text is completely unobstructed.
     */
    fun setTranslationResultVisible(visible: Boolean) {
        hasTranslationResult = visible
        if (visible) {
            userManuallyExpandedControls = false
        }
        applyControlsVisibility()
    }

    private fun updateFloatingCardsVisibility() {
        if (isTextSelectMode || hasTranslationResult) {
            qrResultCard.visibility = View.GONE
            linkResultCard.visibility = View.GONE
            return
        }
        if (activeQr != null) {
            qrResultCard.visibility = View.VISIBLE
            linkResultCard.visibility = View.GONE
        } else if (detectedLinks.isNotEmpty() && !userDismissedLinkCard && preferences.autoLinkDetectEnabled) {
            qrResultCard.visibility = View.GONE
            linkResultCard.visibility = View.VISIBLE
        } else {
            qrResultCard.visibility = View.GONE
            linkResultCard.visibility = View.GONE
        }
    }

    private fun applyControlsVisibility() {
        if (isTextSelectMode) {
            bottomControlsPanel.visibility = View.GONE
            btnToggleBottomControls.visibility = View.GONE
            updateFloatingCardsVisibility()
            return
        }

        val showBottomPanel = !hasTranslationResult || userManuallyExpandedControls
        bottomControlsPanel.visibility = if (showBottomPanel) View.VISIBLE else View.GONE
        languageBarContainer.visibility =
            if (currentMode == ActionMode.TRANSLATE) View.VISIBLE else View.GONE
        val showEnginePill = currentMode == ActionMode.VISUAL_SEARCH &&
            preferences.actionPanelConfig.showSearchEngineSelector
        searchEngineBarContainer.visibility = if (showEnginePill) View.VISIBLE else View.GONE
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
        updateFloatingCardsVisibility()
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
        btnFullScreenSelect.visibility = View.GONE
        selectableTextOverlayView.visibility = View.VISIBLE
        selectableTextOverlayView.setDetectedBlocks(fullScreenTextBlocks)
        selectedTextCard.visibility = View.VISIBLE
        applyControlsVisibility()
        btnSelectTextMode.text = context.getString(R.string.overlay_done)
        btnSelectTextMode.setBackgroundResource(R.drawable.bg_action_button_active)
    }

    fun exitTextSelectMode(): Boolean {
        if (!isTextSelectMode) return false
        isTextSelectMode = false
        selectedLinks = emptyList()
        selectableTextOverlayView.clearSelection()
        selectableTextOverlayView.visibility = View.GONE
        selectionOverlayView.visibility = View.VISIBLE
        btnFullScreenSelect.visibility = View.VISIBLE
        selectedTextCard.visibility = View.GONE
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
        val activeVisual = com.bubble.screentools.lens.SearchEngineRegistry(preferences).getActiveVisualEngine()
        tvActiveSearchEngine.text = context.getString(
            R.string.overlay_engine_pill_fmt,
            activeVisual.name
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
        updateFloatingCardsVisibility()
    }

    fun hideQrCard() {
        activeQr = null
        snapshotProvider = null
        updateFloatingCardsVisibility()
    }

    fun closePicker(): Boolean {
        if (pickerOverlay.visibility != View.VISIBLE) return false
        pickerOverlay.visibility = View.GONE
        return true
    }

    private fun buildClickableLinkPreview(
        fullText: String,
        links: List<ScreenLinkDetector.DetectedLink>
    ): CharSequence {
        val spannable = SpannableString(fullText)
        for (link in links) {
            val start = link.startIndex.coerceIn(0, fullText.length)
            val end = link.endIndex.coerceIn(start, fullText.length)
            if (end <= start) continue
            val span = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    if (ScreenLinkDetector.openLink(context, link.normalizedUrl)) {
                        onDismissOverlayRequested()
                    }
                }

                override fun updateDrawState(ds: TextPaint) {
                    ds.color = Color.parseColor("#4DD0E1")
                    ds.isUnderlineText = true
                }
            }
            spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spannable
    }

    private fun handleOpenDetectedLinks(links: List<ScreenLinkDetector.DetectedLink>) {
        if (links.size == 1) {
            if (ScreenLinkDetector.openLink(context, links.first().normalizedUrl)) {
                onDismissOverlayRequested()
            }
        } else if (links.size > 1) {
            openLinkPicker(links)
        }
    }

    private fun openLinkPicker(links: List<ScreenLinkDetector.DetectedLink>) {
        pickerTitle.text = context.getString(R.string.overlay_pick_link_title)
        pickerList.removeAllViews()
        val density = context.resources.displayMetrics.density
        val padH = (12 * density).toInt()
        val padV = (12 * density).toInt()

        for (link in links) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padH, padV, padH, padV)
                setOnClickListener {
                    closePicker()
                    if (ScreenLinkDetector.openLink(context, link.normalizedUrl)) {
                        onDismissOverlayRequested()
                    }
                }
            }
            row.addView(TextView(context).apply {
                text = link.host
                textSize = 14f
                setTextColor(Color.parseColor("#4DD0E1"))
                setTypeface(typeface, Typeface.BOLD)
            })
            row.addView(TextView(context).apply {
                text = link.displayUrl
                textSize = 12f
                maxLines = 2
                setTextColor(Color.WHITE)
            })
            pickerList.addView(row)
        }
        pickerOverlay.visibility = View.VISIBLE
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

        val registry = com.bubble.screentools.lens.SearchEngineRegistry(preferences)
        val engines = registry.getVisualEngines(enabledOnly = true)
        val active = registry.getActiveVisualEngine()

        for (engine in engines) {
            val isSelected = (engine.id == active.id)
            val row = TextView(context).apply {
                text = if (isSelected) "✓  ${engine.name}" else engine.name
                textSize = 14f
                setTextColor(if (isSelected) Color.parseColor("#64B5F6") else Color.WHITE)
                setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                setPadding(padH, padV, padH, padV)
                setOnClickListener {
                    preferences.lastUsedVisualEngineId = engine.id
                    if (!preferences.rememberLastSearchEngine) {
                        preferences.defaultVisualEngineId = engine.id
                    }
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
