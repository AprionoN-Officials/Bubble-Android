package com.bubble.screentools.ui.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bubble.screentools.R
import com.bubble.screentools.capture.BitmapCropHelper
import com.bubble.screentools.capture.CroppedBitmapResult
import com.bubble.screentools.capture.MediaProjectionConsentActivity
import com.bubble.screentools.capture.ScreenCaptureManager
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.lens.LensIntegrationManager
import com.bubble.screentools.lens.SearchEngineRegistry
import com.bubble.screentools.lens.TemporaryImageProvider
import com.bubble.screentools.model.ActionMode
import com.bubble.screentools.model.ActionPanelButtonType
import com.bubble.screentools.model.BubbleOverlayState
import com.bubble.screentools.model.SelectionRegion
import com.bubble.screentools.model.SelectionShape
import com.bubble.screentools.model.TextBlockResult
import com.bubble.screentools.ocr.OCRManager
import com.bubble.screentools.ocr.ScreenTextExtractor
import com.bubble.screentools.qr.QrActionHelper
import com.bubble.screentools.qr.QrCodeScanner
import com.bubble.screentools.security.SafeModeManager
import com.bubble.screentools.service.MediaProjectionService
import com.bubble.screentools.theme.ThemeManager
import com.bubble.screentools.translation.LanguageDetectionManager
import com.bubble.screentools.translation.TranslationManager
import com.bubble.screentools.util.CompatibilityHelper
import com.bubble.screentools.util.LocaleHelper
import com.bubble.screentools.util.PerformanceManager
import com.bubble.screentools.util.ScreenLinkDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/**
 * Orchestrates Mode B (Accessibility / WindowManager Overlay) lifecycle with:
 * - Safe Mode protection before showing and throughout live sessions
 * - Custom Selection appearance, custom Action Panel (classic 2-button default),
 *   and 6 working overlay actions (Translate, Search, Select Text, Copy/Save/Share Image)
 * - Quality/Context translation pipeline, Auto QR & Auto Link Detection, & SearchEngineRegistry integration
 */
class BubbleOverlayController(
    private val context: Context,
    private val preferences: BubblePreferences,
    private val safeModeManager: SafeModeManager,
    private val screenCaptureManager: ScreenCaptureManager
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val textExtractor = ScreenTextExtractor()
    private val ocrManager = OCRManager(context, preferences)
    private val qrScanner = QrCodeScanner()
    private val languageDetector = LanguageDetectionManager()
    private val translationManager = TranslationManager(languageDetector)
    private val lensManager = LensIntegrationManager(context)
    private val searchRegistry = SearchEngineRegistry(preferences)
    private val performanceManager = PerformanceManager(preferences)

    private var rootView: OverlayRootFrameLayout? = null
    private var selectionView: SelectionOverlayView? = null
    private var translationRenderer: TranslationOverlayRenderer? = null
    private var actionPanelView: ActionPanelView? = null
    private var bottomActionRow: View? = null
    private var btnToggleOriginal: Button? = null
    private var btnCopyResultText: Button? = null
    private var tvStatusHint: TextView? = null
    private var topBarSpacer: View? = null
    private var uploadConfirmCard: View? = null
    private var tvUploadConfirmMsg: TextView? = null
    private var languageBarHelper: OverlayLanguageBarHelper? = null

    private var sessionSnapshot: Bitmap? = null
    private var activeServiceRef: AccessibilityService? = null

    private var activeMode: ActionMode = preferences.lastActionMode
    private var currentState: BubbleOverlayState = BubbleOverlayState.Idle
    private var debounceJob: Job? = null
    private var activeTaskJob: Job? = null
    private var qrScanJob: Job? = null
    private var fullScreenTextJob: Job? = null
    private var pendingRegionAfterConsent: SelectionRegion? = null
    private var pendingConfirmedSearchRegion: SelectionRegion? = null
    private var showOriginalText = false
    private var lastInsetLeft = 0
    private var lastInsetTop = 0
    private var lastInsetRight = 0
    private var lastInsetBottom = 0

    val isShowing: Boolean
        get() = rootView != null

    fun show(
        service: AccessibilityService?,
        initialPackage: String?,
        initialSnapshot: Bitmap? = null
    ) {
        safeModeManager.updateForegroundPackage(initialPackage)
        val decision = safeModeManager.evaluateActiveApp(initialPackage)
        if (decision is SafeModeManager.SafeModeDecision.Blocked) {
            initialSnapshot?.let { if (!it.isRecycled) it.recycle() }
            haltAllAndDismiss("Safe Mode: ${decision.reason}")
            return
        }

        if (isShowing) {
            initialSnapshot?.let { if (!it.isRecycled) it.recycle() }
            return
        }

        val isFromAccessibilityService = context is AccessibilityService
        if (!isFromAccessibilityService && !CompatibilityHelper.isOverlayPermissionGranted(context)) {
            initialSnapshot?.let { if (!it.isRecycled) it.recycle() }
            Toast.makeText(
                context,
                locCtx().getString(R.string.overlay_permission_required_toast),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        activeServiceRef = service
        replaceSessionSnapshot(initialSnapshot)

        val localizedContext = LocaleHelper.wrapContext(context, preferences.appLanguageCode)
        val themedBase = ContextThemeWrapper(localizedContext, R.style.Theme_Bubble)
        val themedContext = ThemeManager.wrapOverlayContext(themedBase, preferences)
        val view = LayoutInflater.from(themedContext)
            .inflate(R.layout.overlay_bubble_container, null) as OverlayRootFrameLayout
        rootView = view

        val topBar = view.findViewById<View>(R.id.topControlsBar)
        val bottomBar = view.findViewById<View>(R.id.bottomActionRow)
        bottomActionRow = bottomBar
        selectionView = view.findViewById(R.id.selectionOverlayView)
        translationRenderer = view.findViewById(R.id.translationOverlayRenderer)
        actionPanelView = view.findViewById(R.id.primaryActionButtonsRow)
        btnToggleOriginal = view.findViewById(R.id.btnToggleOriginal)
        btnCopyResultText = view.findViewById(R.id.btnCopyResultText)
        tvStatusHint = view.findViewById(R.id.tvStatusHint)
        topBarSpacer = view.findViewById(R.id.topBarSpacer)
        uploadConfirmCard = view.findViewById(R.id.uploadConfirmCard)
        tvUploadConfirmMsg = view.findViewById(R.id.tvUploadConfirmMsg)

        val panelConfig = preferences.actionPanelConfig
        val topBasePadding = topBar.paddingTop
        val fallbackTopInset = getStatusBarHeightFallback()
        val fallbackBottomInset = getNavigationBarHeightFallback()
        lastInsetLeft = 0
        lastInsetTop = fallbackTopInset
        lastInsetRight = 0
        lastInsetBottom = fallbackBottomInset
        topBar.setPadding(
            topBar.paddingLeft,
            topBasePadding + fallbackTopInset,
            topBar.paddingRight,
            topBar.paddingBottom
        )
        ActionPanelView.applyContainerPosition(
            container = bottomBar,
            config = panelConfig,
            insetLeft = 0,
            insetTop = fallbackTopInset,
            insetRight = 0,
            insetBottom = fallbackBottomInset
        )
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val sysBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            lastInsetLeft = maxOf(sysBars.left, 0)
            lastInsetTop = maxOf(sysBars.top, 0)
            lastInsetRight = maxOf(sysBars.right, 0)
            lastInsetBottom = maxOf(sysBars.bottom, 0)
            topBar.setPadding(
                topBar.paddingLeft,
                topBasePadding + lastInsetTop,
                topBar.paddingRight,
                topBar.paddingBottom
            )
            ActionPanelView.applyContainerPosition(
                container = bottomBar,
                config = panelConfig,
                insetLeft = lastInsetLeft,
                insetTop = lastInsetTop,
                insetRight = lastInsetRight,
                insetBottom = lastInsetBottom
            )
            insets
        }

        view.onBackPressedListener = {
            when {
                uploadConfirmCard?.visibility == View.VISIBLE -> {
                    uploadConfirmCard?.visibility = View.GONE
                    pendingConfirmedSearchRegion = null
                }
                languageBarHelper?.closePicker() == true -> Unit
                languageBarHelper?.exitTextSelectMode() == true -> Unit
                else -> dismiss()
            }
        }

        view.findViewById<View>(R.id.btnFullScreenSelect).setOnClickListener {
            expandSelectionToFullScreen()
        }

        view.findViewById<ImageButton>(R.id.btnCloseOverlay).setOnClickListener {
            dismiss()
        }

        view.findViewById<View>(R.id.btnCancelUpload).setOnClickListener {
            uploadConfirmCard?.visibility = View.GONE
            pendingConfirmedSearchRegion = null
            updateStatusHint(null)
        }

        view.findViewById<View>(R.id.btnProceedUpload).setOnClickListener {
            uploadConfirmCard?.visibility = View.GONE
            val region = pendingConfirmedSearchRegion ?: return@setOnClickListener
            pendingConfirmedSearchRegion = null
            dispatchVisualSearchNow(service, region)
        }

        languageBarHelper = OverlayLanguageBarHelper(
            context = themedContext,
            rootView = view,
            preferences = preferences,
            onLanguagePairChanged = {
                val region = selectionView?.getCurrentRegion()
                if (activeMode == ActionMode.TRANSLATE && region != null && region.isValid()) {
                    executeTranslate(service, region)
                }
            },
            onSearchEngineChanged = {
                val region = selectionView?.getCurrentRegion()
                if (activeMode == ActionMode.VISUAL_SEARCH && region != null && region.isValid()) {
                    requestVisualSearch(service, region)
                }
            },
            onTranslateSelectedBlocksRequested = { selectedBlocks ->
                translateExplicitBlocks(selectedBlocks)
            },
            onDismissOverlayRequested = { dismiss() }
        )

        btnToggleOriginal?.setOnClickListener {
            val resultState = currentState as? BubbleOverlayState.Result ?: return@setOnClickListener
            showOriginalText = !showOriginalText
            btnToggleOriginal?.text = localizedContext.getString(
                if (showOriginalText) R.string.overlay_translated else R.string.overlay_original
            )
            translationRenderer?.setTranslations(
                resultState.blocks,
                showOriginalText,
                preferences.translationOverlayStyle
            )
        }

        btnCopyResultText?.setOnClickListener {
            val resultState = currentState as? BubbleOverlayState.Result ?: return@setOnClickListener
            val text = resultState.blocks.joinToString("\n") {
                if (showOriginalText) it.originalText else it.translatedText
            }
            if (text.isNotBlank()) {
                QrActionHelper.copyToClipboard(
                    localizedContext,
                    localizedContext.getString(
                        if (showOriginalText) R.string.overlay_label_original_text else R.string.overlay_label_translated_text
                    ),
                    text
                )
            }
        }

        applyOverlayPreferences(themedContext)
        activeMode = preferences.lastActionMode
        val accent = ThemeManager.resolveOverlayAccentColor(themedContext, preferences)
        actionPanelView?.render(panelConfig, activeMode, accent)
        actionPanelView?.onActionButtonClick = { actionType ->
            handlePanelAction(service, actionType)
        }
        languageBarHelper?.setVisibleForMode(activeMode)

        selectionView?.listener = object : SelectionOverlayView.SelectionListener {
            override fun onSelectionInteractionStarted() {
                debounceJob?.cancel()
                uploadConfirmCard?.visibility = View.GONE
                updateStatusHint(null)
                languageBarHelper?.setDraggingMode(true)
            }

            override fun onSelectionInteractionEnded() {
                languageBarHelper?.setDraggingMode(false)
            }

            override fun onSelectionChanged(region: SelectionRegion, wasMoveOnly: Boolean) {
                translationRenderer?.clear()
                btnToggleOriginal?.visibility = View.GONE
                btnCopyResultText?.visibility = View.GONE
                languageBarHelper?.setTranslationResultVisible(false)
                if (preferences.autoQrScanEnabled) {
                    runAutoQrScanForRegion(region)
                }
                if (preferences.autoLinkDetectEnabled) {
                    languageBarHelper?.updateLinksForRegion(region.bounds)
                }
                val shouldAutoRun = when (activeMode) {
                    ActionMode.TRANSLATE -> preferences.autoTranslateSelection
                    ActionMode.VISUAL_SEARCH -> preferences.autoSearchAfterSelection && !wasMoveOnly
                }
                if (shouldAutoRun) {
                    scheduleDebouncedAction(service, region)
                }
            }

            override fun onSelectionCleared() {
                debounceJob?.cancel()
                translationRenderer?.clear()
                btnToggleOriginal?.visibility = View.GONE
                btnCopyResultText?.visibility = View.GONE
                languageBarHelper?.setTranslationResultVisible(false)
                updateStatusHint(null)
            }
        }

        val layoutType = CompatibilityHelper.getOverlayWindowType(
            fromAccessibilityService = isFromAccessibilityService
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val allowAnim = ThemeManager.areAnimationsAllowed(context, preferences.animationsEnabled)
        if (allowAnim) {
            view.alpha = 0f
            windowManager.addView(view, params)
            view.requestFocus()
            ViewCompat.requestApplyInsets(view)
            view.animate().alpha(1f).setDuration(140L).start()
        } else {
            windowManager.addView(view, params)
            view.requestFocus()
            ViewCompat.requestApplyInsets(view)
        }
        currentState = BubbleOverlayState.Selecting(activeMode)

        view.post {
            if (!isShowing) return@post
            if (preferences.autoQrScanEnabled && sessionSnapshot != null) {
                runAutoQrScanFullScreen()
            }
            runAutoFullScreenTextScan(service)
        }
    }

    private fun handlePanelAction(service: AccessibilityService?, actionType: ActionPanelButtonType) {
        debounceJob?.cancel()
        uploadConfirmCard?.visibility = View.GONE
        val region = selectionView?.getCurrentRegion() ?: return
        val lCtx = locCtx()

        when (actionType) {
            ActionPanelButtonType.TRANSLATE -> {
                activeMode = ActionMode.TRANSLATE
                preferences.lastActionMode = activeMode
                actionPanelView?.updateActiveMode(activeMode)
                languageBarHelper?.setVisibleForMode(activeMode)
                if (region.isValid()) executeTranslate(service, region)
            }
            ActionPanelButtonType.SEARCH_IMAGE -> {
                activeMode = ActionMode.VISUAL_SEARCH
                preferences.lastActionMode = activeMode
                actionPanelView?.updateActiveMode(activeMode)
                languageBarHelper?.setVisibleForMode(activeMode)
                if (region.isValid()) requestVisualSearch(service, region)
            }
            ActionPanelButtonType.FULL_SCREEN -> {
                expandSelectionToFullScreen()
            }
            ActionPanelButtonType.SELECT_TEXT -> {
                languageBarHelper?.enterTextSelectMode()
            }
            ActionPanelButtonType.COPY_IMAGE -> executeImageTool(service, region) { bmp ->
                val ok = OverlayExtraActionsHelper.copyImageToClipboard(context, bmp)
                Toast.makeText(
                    context,
                    lCtx.getString(if (ok) R.string.panel_toast_image_copied else R.string.panel_toast_action_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
            ActionPanelButtonType.SAVE_IMAGE -> executeImageTool(service, region) { bmp ->
                val ok = OverlayExtraActionsHelper.saveImageToGallery(context, bmp)
                Toast.makeText(
                    context,
                    lCtx.getString(if (ok) R.string.panel_toast_image_saved else R.string.panel_toast_action_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
            ActionPanelButtonType.SHARE_IMAGE -> executeImageTool(service, region) { bmp ->
                val ok = OverlayExtraActionsHelper.shareImage(context, bmp, lCtx.getString(R.string.panel_action_share_image))
                if (ok) dismiss()
            }
        }
    }

    private fun executeImageTool(
        service: AccessibilityService?,
        region: SelectionRegion,
        onBitmapReady: (Bitmap) -> Unit
    ) {
        if (!region.isValid()) return
        activeTaskJob?.cancel()
        activeTaskJob = scope.launch {
            val cropped = obtainCroppedBitmap(service, region, entireScreen = false, forOcr = false) ?: return@launch
            try {
                onBitmapReady(cropped.bitmap)
            } finally {
                if (!cropped.bitmap.isRecycled) cropped.bitmap.recycle()
            }
        }
    }

    private fun expandSelectionToFullScreen() {
        debounceJob?.cancel()
        uploadConfirmCard?.visibility = View.GONE
        pendingConfirmedSearchRegion = null
        translationRenderer?.clear()
        btnToggleOriginal?.visibility = View.GONE
        btnCopyResultText?.visibility = View.GONE
        languageBarHelper?.setTranslationResultVisible(false)
        selectionView?.selectFullScreen(
            insetLeft = lastInsetLeft,
            insetTop = lastInsetTop,
            insetRight = lastInsetRight,
            insetBottom = lastInsetBottom
        )
        updateStatusHint(locCtx().getString(R.string.overlay_fullscreen_ready_hint))
    }

    private fun runAutoFullScreenTextScan(service: AccessibilityService?) {
        fullScreenTextJob?.cancel()
        fullScreenTextJob = scope.launch {
            val snapshot = sessionSnapshot
            val vw = selectionView?.width ?: 0
            val vh = selectionView?.height ?: 0

            val ocrBlocks = if (snapshot != null && !snapshot.isRecycled) {
                ocrManager.recognizeFullScreen(
                    source = snapshot,
                    viewWidth = vw,
                    viewHeight = vh,
                    maxDimensionPx = performanceManager.getMaxOcrDimensionPx()
                )
            } else {
                emptyList()
            }

            val a11yBlocks = if (service != null && (ocrBlocks.isEmpty() || preferences.autoLinkDetectEnabled)) {
                withContext(Dispatchers.Default) {
                    val fullRect = RectF(0f, 0f, vw.toFloat().coerceAtLeast(1080f), vh.toFloat().coerceAtLeast(1920f))
                    textExtractor.extractFromAccessibilityTree(
                        service = service,
                        region = SelectionRegion(fullRect, SelectionShape.RECTANGLE),
                        entireScreen = true
                    )
                }
            } else {
                emptyList()
            }

            val finalBlocks = if (ocrBlocks.isNotEmpty()) ocrBlocks else a11yBlocks
            if (isShowing && finalBlocks.isNotEmpty()) {
                languageBarHelper?.setDetectedFullScreenTextBlocks(finalBlocks)
            }
            if (isShowing && preferences.autoLinkDetectEnabled && a11yBlocks.isNotEmpty() && ocrBlocks.isNotEmpty()) {
                val extraLinks = ScreenLinkDetector.extractLinksFromBlocks(a11yBlocks)
                if (extraLinks.isNotEmpty()) {
                    languageBarHelper?.mergeExtraDetectedLinks(extraLinks)
                }
            }
        }
    }

    private fun runAutoQrScanFullScreen() {
        val snapshot = sessionSnapshot ?: return
        if (snapshot.isRecycled) return
        qrScanJob?.cancel()
        qrScanJob = scope.launch {
            val found = withContext(Dispatchers.Default) {
                qrScanner.scanBitmap(snapshot)
            }
            val first = found.firstOrNull()
            if (first != null && isShowing) {
                languageBarHelper?.showDetectedQr(first) { sessionSnapshot }
            }
        }
    }

    private fun runAutoQrScanForRegion(region: SelectionRegion) {
        val snapshot = sessionSnapshot ?: return
        if (snapshot.isRecycled) return
        qrScanJob?.cancel()
        qrScanJob = scope.launch {
            val vw = selectionView?.width ?: 0
            val vh = selectionView?.height ?: 0
            val cropped = withContext(Dispatchers.Default) {
                BitmapCropHelper.cropAndMask(
                    source = snapshot,
                    region = region,
                    maxDimensionPx = 1400,
                    viewWidth = vw,
                    viewHeight = vh,
                    forOcr = false
                )
            }
            val found = try {
                qrScanner.scanBitmap(
                    bitmap = cropped.bitmap,
                    offsetLeft = cropped.cropLeft,
                    offsetTop = cropped.cropTop,
                    scaleFactor = cropped.scaleX
                )
            } finally {
                if (!cropped.bitmap.isRecycled) cropped.bitmap.recycle()
            }
            val first = found.firstOrNull()
            if (first != null && isShowing) {
                languageBarHelper?.showDetectedQr(first) { sessionSnapshot }
            }
        }
    }

    private fun applyOverlayPreferences(themedContext: Context) {
        val selConfig = preferences.selectionConfig
        val themeConfig = preferences.themeConfig
        val resolvedBorder = ThemeManager.resolveSelectionBorderColor(themedContext, selConfig, themeConfig)
        val allowAnim = ThemeManager.areAnimationsAllowed(context, selConfig.animationEnabled)
        selectionView?.applyConfig(selConfig, resolvedBorder, allowAnim)
        selectionView?.hapticEnabled = preferences.hapticFeedbackEnabled
    }

    private fun locCtx(): Context = LocaleHelper.wrapContext(context, preferences.appLanguageCode)

    private fun scheduleDebouncedAction(service: AccessibilityService?, region: SelectionRegion) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            when (activeMode) {
                ActionMode.TRANSLATE -> {
                    delay(performanceManager.getTranslateDebounceMs())
                    executeTranslate(service, region)
                }
                ActionMode.VISUAL_SEARCH -> {
                    val totalMs = performanceManager.getVisualSearchDebounceMs()
                    val engineName = searchRegistry.getActiveVisualEngine().name
                    if (totalMs <= 0L) {
                        requestVisualSearch(service, region)
                    } else {
                        var remainingMs = totalMs
                        while (remainingMs > 0L) {
                            val secLabel = ceil(remainingMs / 1000.0).toInt().coerceAtLeast(1)
                            updateStatusHint(locCtx().getString(R.string.overlay_countdown_fmt, engineName, secLabel))
                            val stepMs = minOf(1000L, remainingMs)
                            delay(stepMs)
                            remainingMs -= stepMs
                        }
                        requestVisualSearch(service, region)
                    }
                }
            }
        }
    }

    private fun translateExplicitBlocks(blocks: List<TextBlockResult>) {
        if (blocks.isEmpty()) return
        activeTaskJob?.cancel()
        activeTaskJob = scope.launch {
            updateStatusHint(locCtx().getString(R.string.overlay_translating_fmt, blocks.size))
            val translated = translationManager.translateBlocks(
                blocks = blocks,
                sourceLangPref = preferences.sourceLanguageCode,
                targetLangPref = preferences.targetLanguageCode,
                autoDetect = preferences.autoDetectLanguage,
                useCache = preferences.translationCacheEnabled,
                qualityMode = preferences.translationQualityMode,
                contextProfile = preferences.translationContextProfile,
                preferredSources = preferences.preferredSourceLanguages,
                fallbackSourceLang = preferences.fallbackLanguageCode
            )
            languageBarHelper?.lastDetectedSourceLang = translated.firstOrNull()?.detectedLangCode
            showOriginalText = false
            currentState = BubbleOverlayState.Result(ActionMode.TRANSLATE, translated, showOriginal = false)
            translationRenderer?.setTranslations(
                translated,
                showOriginalText = false,
                style = preferences.translationOverlayStyle
            )
            btnToggleOriginal?.text = locCtx().getString(R.string.overlay_original)
            btnToggleOriginal?.visibility = View.VISIBLE
            btnCopyResultText?.visibility = View.VISIBLE
            languageBarHelper?.setTranslationResultVisible(true)
            updateStatusHint(null)
        }
    }

    private fun executeTranslate(service: AccessibilityService?, region: SelectionRegion) {
        val decision = safeModeManager.evaluateActiveApp()
        if (decision is SafeModeManager.SafeModeDecision.Blocked) {
            haltAllAndDismiss(decision.reason)
            return
        }

        activeTaskJob?.cancel()
        activeTaskJob = scope.launch {
            currentState = BubbleOverlayState.Scanning(ActionMode.TRANSLATE, region)
            updateStatusHint(locCtx().getString(R.string.overlay_reading_text))

            val cropped = obtainCroppedBitmap(
                service = service,
                region = region,
                entireScreen = preferences.translateEntireScreen,
                forOcr = true
            )
            val ocrBlocks = if (cropped != null) {
                try {
                    ocrManager.recognizeBitmap(
                        bitmap = cropped.bitmap,
                        cropOffsetLeft = cropped.cropLeft,
                        cropOffsetTop = cropped.cropTop,
                        scaleFactor = cropped.scaleX,
                        scaleX = cropped.scaleX,
                        scaleY = cropped.scaleY,
                        padPx = cropped.padPx
                    )
                } finally {
                    if (!cropped.bitmap.isRecycled) {
                        cropped.bitmap.recycle()
                    }
                }
            } else {
                emptyList()
            }

            val blocks = if (ocrBlocks.isNotEmpty()) {
                ocrBlocks
            } else {
                withContext(Dispatchers.Default) {
                    textExtractor.extractFromAccessibilityTree(
                        service = service,
                        region = region,
                        entireScreen = preferences.translateEntireScreen
                    )
                }
            }

            if (blocks.isEmpty()) {
                updateStatusHint(locCtx().getString(R.string.overlay_no_text_found))
                return@launch
            }

            currentState = BubbleOverlayState.Translating(region, blocks.size)
            updateStatusHint(locCtx().getString(R.string.overlay_translating_fmt, blocks.size))

            val translated = translationManager.translateBlocks(
                blocks = blocks,
                sourceLangPref = preferences.sourceLanguageCode,
                targetLangPref = preferences.targetLanguageCode,
                autoDetect = preferences.autoDetectLanguage,
                useCache = preferences.translationCacheEnabled,
                qualityMode = preferences.translationQualityMode,
                contextProfile = preferences.translationContextProfile,
                preferredSources = preferences.preferredSourceLanguages,
                fallbackSourceLang = preferences.fallbackLanguageCode
            )

            languageBarHelper?.lastDetectedSourceLang = translated.firstOrNull()?.detectedLangCode
            showOriginalText = false
            currentState = BubbleOverlayState.Result(ActionMode.TRANSLATE, translated, showOriginal = false)
            translationRenderer?.setTranslations(
                translated,
                showOriginalText = false,
                style = preferences.translationOverlayStyle
            )
            btnToggleOriginal?.text = locCtx().getString(R.string.overlay_original)
            btnToggleOriginal?.visibility = View.VISIBLE
            btnCopyResultText?.visibility = View.VISIBLE
            languageBarHelper?.setTranslationResultVisible(true)
            updateStatusHint(null)
        }
    }

    private fun requestVisualSearch(service: AccessibilityService?, region: SelectionRegion) {
        if (preferences.confirmBeforeImageUpload) {
            pendingConfirmedSearchRegion = region
            val engineName = searchRegistry.getActiveVisualEngine().name
            tvUploadConfirmMsg?.text = locCtx().getString(R.string.search_confirm_upload_msg, engineName)
            uploadConfirmCard?.visibility = View.VISIBLE
            return
        }
        dispatchVisualSearchNow(service, region)
    }

    private fun dispatchVisualSearchNow(service: AccessibilityService?, region: SelectionRegion) {
        val decision = safeModeManager.evaluateActiveApp()
        if (decision is SafeModeManager.SafeModeDecision.Blocked) {
            haltAllAndDismiss(decision.reason)
            return
        }

        activeTaskJob?.cancel()
        activeTaskJob = scope.launch {
            currentState = BubbleOverlayState.Searching(region)
            val engineItem = searchRegistry.getActiveVisualEngine()
            updateStatusHint(locCtx().getString(R.string.overlay_preparing_search_fmt, engineItem.name))

            val cropped = obtainCroppedBitmap(
                service = service,
                region = region,
                entireScreen = false,
                forOcr = false,
                maxDimOverride = preferences.searchMaxResolutionPx
            ) ?: return@launch

            val dispatch = try {
                lensManager.launchVisualSearch(cropped.bitmap, engineItem)
            } finally {
                if (!cropped.bitmap.isRecycled) {
                    cropped.bitmap.recycle()
                }
            }

            when (dispatch) {
                is LensIntegrationManager.VisualSearchDispatchResult.OpenedSharesheet -> {
                    Toast.makeText(context, dispatch.fallbackNotice, Toast.LENGTH_LONG).show()
                    dismiss()
                }
                is LensIntegrationManager.VisualSearchDispatchResult.Error -> {
                    updateStatusHint(dispatch.message)
                }
                else -> dismiss()
            }
        }
    }

    private suspend fun obtainCroppedBitmap(
        service: AccessibilityService?,
        region: SelectionRegion,
        entireScreen: Boolean,
        forOcr: Boolean,
        maxDimOverride: Int? = null
    ): CroppedBitmapResult? {
        val maxDim = maxDimOverride ?: performanceManager.getMaxOcrDimensionPx()
        val vw = selectionView?.width ?: 0
        val vh = selectionView?.height ?: 0
        val qualityMode = preferences.translationQualityMode

        val existing = sessionSnapshot
        if (existing != null && !existing.isRecycled) {
            val effectiveRegion = if (entireScreen) {
                val targetW = if (vw > 0) vw.toFloat() else existing.width.toFloat()
                val targetH = if (vh > 0) vh.toFloat() else existing.height.toFloat()
                SelectionRegion(
                    bounds = RectF(0f, 0f, targetW, targetH),
                    shape = SelectionShape.RECTANGLE
                )
            } else {
                region
            }
            return withContext(Dispatchers.Default) {
                BitmapCropHelper.cropAndMask(
                    source = existing,
                    region = effectiveRegion,
                    maxDimensionPx = maxDim,
                    viewWidth = vw,
                    viewHeight = vh,
                    forOcr = forOcr,
                    qualityMode = qualityMode
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && service != null) {
            rootView?.visibility = View.INVISIBLE
            delay(50L)
            val result = screenCaptureManager.captureFullScreenViaAccessibility(service)
            rootView?.visibility = View.VISIBLE

            return when (result) {
                is ScreenCaptureManager.FullScreenCaptureResult.Success -> {
                    replaceSessionSnapshot(result.bitmap)
                    val effectiveRegion = if (entireScreen) {
                        val targetW = if (vw > 0) vw.toFloat() else result.bitmap.width.toFloat()
                        val targetH = if (vh > 0) vh.toFloat() else result.bitmap.height.toFloat()
                        SelectionRegion(
                            bounds = RectF(0f, 0f, targetW, targetH),
                            shape = SelectionShape.RECTANGLE
                        )
                    } else {
                        region
                    }
                    withContext(Dispatchers.Default) {
                        BitmapCropHelper.cropAndMask(
                            source = result.bitmap,
                            region = effectiveRegion,
                            maxDimensionPx = maxDim,
                            viewWidth = vw,
                            viewHeight = vh,
                            forOcr = forOcr,
                            qualityMode = qualityMode
                        )
                    }
                }
                ScreenCaptureManager.FullScreenCaptureResult.ProtectedContent -> {
                    haltAllAndDismiss(locCtx().getString(R.string.overlay_flag_secure_blocked))
                    null
                }
                ScreenCaptureManager.FullScreenCaptureResult.RateLimited -> {
                    updateStatusHint(locCtx().getString(R.string.overlay_wait_rate_limit))
                    null
                }
                is ScreenCaptureManager.FullScreenCaptureResult.Failed -> {
                    updateStatusHint(result.message)
                    null
                }
            }
        }

        if (!screenCaptureManager.hasActiveGrant) {
            pendingRegionAfterConsent = region
            updateStatusHint(locCtx().getString(R.string.overlay_requesting_capture))
            rootView?.visibility = View.GONE
            MediaProjectionConsentActivity.launch(context)
            return null
        }

        rootView?.visibility = View.INVISIBLE
        delay(50L)
        val captureResult = screenCaptureManager.captureCroppedRegion(region, maxDim)
        rootView?.visibility = View.VISIBLE

        return when (captureResult) {
            is ScreenCaptureManager.CaptureResult.Success -> captureResult.cropped
            ScreenCaptureManager.CaptureResult.ProtectedContent -> {
                haltAllAndDismiss(locCtx().getString(R.string.overlay_flag_secure_blocked))
                null
            }
            ScreenCaptureManager.CaptureResult.PermissionRequired -> {
                pendingRegionAfterConsent = region
                rootView?.visibility = View.GONE
                MediaProjectionConsentActivity.launch(context)
                null
            }
            is ScreenCaptureManager.CaptureResult.Failed -> {
                updateStatusHint(captureResult.message)
                null
            }
            ScreenCaptureManager.CaptureResult.Busy -> null
        }
    }

    fun resumePendingAfterCaptureGrant(service: AccessibilityService?) {
        rootView?.visibility = View.VISIBLE
        rootView?.requestFocus()
        val region = pendingRegionAfterConsent ?: return
        pendingRegionAfterConsent = null
        when (activeMode) {
            ActionMode.TRANSLATE -> executeTranslate(service, region)
            ActionMode.VISUAL_SEARCH -> requestVisualSearch(service, region)
        }
    }

    fun onCaptureDenied() {
        rootView?.visibility = View.VISIBLE
        rootView?.requestFocus()
        pendingRegionAfterConsent = null
        updateStatusHint(locCtx().getString(R.string.overlay_capture_denied))
    }

    fun haltAllAndDismiss(notice: String? = null) {
        debounceJob?.cancel()
        activeTaskJob?.cancel()
        qrScanJob?.cancel()
        fullScreenTextJob?.cancel()
        pendingRegionAfterConsent = null
        pendingConfirmedSearchRegion = null
        replaceSessionSnapshot(null)
        screenCaptureManager.releaseSession()
        MediaProjectionService.stop(context)
        if (preferences.autoCleanupTempImages) {
            TemporaryImageProvider.cleanupCache(context, forceDeleteAll = true)
        }
        dismiss()
        if (!notice.isNullOrBlank()) {
            Toast.makeText(context, notice, Toast.LENGTH_LONG).show()
        }
    }

    fun dismiss() {
        debounceJob?.cancel()
        activeTaskJob?.cancel()
        qrScanJob?.cancel()
        fullScreenTextJob?.cancel()
        replaceSessionSnapshot(null)
        ocrManager.release()
        qrScanner.release()
        translationManager.release()
        languageDetector.release()
        if (preferences.restrictCaptureToActiveOverlay) {
            screenCaptureManager.releaseSession()
            MediaProjectionService.stop(context)
        }
        val view = rootView
        rootView = null
        selectionView = null
        translationRenderer = null
        actionPanelView = null
        bottomActionRow = null
        btnToggleOriginal = null
        btnCopyResultText = null
        tvStatusHint = null
        topBarSpacer = null
        uploadConfirmCard = null
        tvUploadConfirmMsg = null
        languageBarHelper = null
        activeServiceRef = null
        currentState = BubbleOverlayState.Idle
        if (view != null) {
            runCatching { windowManager.removeViewImmediate(view) }
        }
    }

    private fun replaceSessionSnapshot(newBitmap: Bitmap?) {
        val old = sessionSnapshot
        if (old != null && old !== newBitmap && !old.isRecycled) {
            old.recycle()
        }
        sessionSnapshot = newBitmap
    }

    private fun updateStatusHint(text: String?) {
        if (text.isNullOrBlank()) {
            tvStatusHint?.visibility = View.GONE
            topBarSpacer?.visibility = View.VISIBLE
        } else {
            tvStatusHint?.text = text
            tvStatusHint?.visibility = View.VISIBLE
            topBarSpacer?.visibility = View.GONE
        }
    }

    private fun getStatusBarHeightFallback(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun getNavigationBarHeightFallback(): Int {
        val id = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }
}
