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
import android.widget.LinearLayout
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
import com.bubble.screentools.lens.TemporaryImageProvider
import com.bubble.screentools.model.ActionMode
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
import com.bubble.screentools.translation.LanguageDetectionManager
import com.bubble.screentools.translation.TranslationManager
import com.bubble.screentools.util.CompatibilityHelper
import com.bubble.screentools.util.LocaleHelper
import com.bubble.screentools.util.PerformanceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrates Mode B (Assistant Overlay) lifecycle:
 * - Safe Mode check before showing overlay and during live sessions
 * - Silent Android 11+ AccessibilityService.takeScreenshot() snapshot (NO "Start recording" prompt!)
 * - Automatic full-screen QR Code detection + Full-screen Live Text Select & Copy (without Lens!)
 * - Comfortable countdown debounce before external Visual Search (immediately canceled on touch/resize!)
 * - High-accuracy OCR-first + Accessibility fallback text detection
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
    private val performanceManager = PerformanceManager(preferences)

    private var rootView: OverlayRootFrameLayout? = null
    private var selectionView: SelectionOverlayView? = null
    private var translationRenderer: TranslationOverlayRenderer? = null
    private var btnTranslate: LinearLayout? = null
    private var btnVisualSearch: LinearLayout? = null
    private var btnToggleOriginal: Button? = null
    private var btnCopyResultText: Button? = null
    private var tvStatusHint: TextView? = null
    private var topBarSpacer: View? = null
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
    private var showOriginalText = false

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
                "Berikan izin 'Tampilkan di atas aplikasi lain' terlebih dahulu.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        activeServiceRef = service
        replaceSessionSnapshot(initialSnapshot)

        val localizedContext = LocaleHelper.wrapContext(context, preferences.appLanguageCode)
        val themedContext = ContextThemeWrapper(localizedContext, R.style.Theme_Bubble)
        val view = LayoutInflater.from(themedContext)
            .inflate(R.layout.overlay_bubble_container, null) as OverlayRootFrameLayout
        rootView = view

        val topBar = view.findViewById<View>(R.id.topControlsBar)
        val bottomBar = view.findViewById<View>(R.id.bottomActionRow)
        selectionView = view.findViewById(R.id.selectionOverlayView)
        translationRenderer = view.findViewById(R.id.translationOverlayRenderer)
        btnTranslate = view.findViewById(R.id.btnActionTranslate)
        btnVisualSearch = view.findViewById(R.id.btnActionVisualSearch)
        btnToggleOriginal = view.findViewById(R.id.btnToggleOriginal)
        btnCopyResultText = view.findViewById(R.id.btnCopyResultText)
        tvStatusHint = view.findViewById(R.id.tvStatusHint)
        topBarSpacer = view.findViewById(R.id.topBarSpacer)

        val topBasePadding = topBar.paddingTop
        val bottomBasePadding = bottomBar.paddingBottom
        val fallbackTopInset = getStatusBarHeightFallback()
        val fallbackBottomInset = getNavigationBarHeightFallback()
        topBar.setPadding(
            topBar.paddingLeft,
            topBasePadding + fallbackTopInset,
            topBar.paddingRight,
            topBar.paddingBottom
        )
        bottomBar.setPadding(
            bottomBar.paddingLeft,
            bottomBar.paddingTop,
            bottomBar.paddingRight,
            bottomBasePadding + fallbackBottomInset
        )
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val sysBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            topBar.setPadding(
                topBar.paddingLeft,
                topBasePadding + maxOf(sysBars.top, 0),
                topBar.paddingRight,
                topBar.paddingBottom
            )
            bottomBar.setPadding(
                bottomBar.paddingLeft,
                bottomBar.paddingTop,
                bottomBar.paddingRight,
                bottomBasePadding + maxOf(sysBars.bottom, 0)
            )
            insets
        }

        view.onBackPressedListener = {
            when {
                languageBarHelper?.closePicker() == true -> Unit
                languageBarHelper?.exitTextSelectMode() == true -> Unit
                else -> dismiss()
            }
        }

        view.findViewById<ImageButton>(R.id.btnCloseOverlay).setOnClickListener {
            dismiss()
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
                    executeVisualSearch(service, region)
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

        applyOverlayPreferences()
        activeMode = preferences.lastActionMode
        updateActionButtonsUi()

        selectionView?.listener = object : SelectionOverlayView.SelectionListener {
            override fun onSelectionInteractionStarted() {
                // Immediately cancel any pending auto-launch and hide buttons while resizing/selecting!
                debounceJob?.cancel()
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

        btnTranslate?.setOnClickListener {
            debounceJob?.cancel()
            activeMode = ActionMode.TRANSLATE
            preferences.lastActionMode = activeMode
            updateActionButtonsUi()
            val region = selectionView?.getCurrentRegion() ?: return@setOnClickListener
            if (region.isValid()) {
                executeTranslate(service, region)
            }
        }

        btnVisualSearch?.setOnClickListener {
            debounceJob?.cancel()
            activeMode = ActionMode.VISUAL_SEARCH
            preferences.lastActionMode = activeMode
            updateActionButtonsUi()
            val region = selectionView?.getCurrentRegion() ?: return@setOnClickListener
            if (region.isValid()) {
                executeVisualSearch(service, region)
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

        if (preferences.animationsEnabled) {
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

        // Launch background Auto QR Scan + Auto Full-Screen Live Text Scan once view lays out
        view.post {
            if (!isShowing) return@post
            if (preferences.autoQrScanEnabled && sessionSnapshot != null) {
                runAutoQrScanFullScreen()
            }
            runAutoFullScreenTextScan(service)
        }
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

            val finalBlocks = if (ocrBlocks.isNotEmpty()) {
                ocrBlocks
            } else {
                withContext(Dispatchers.Default) {
                    val fullRect = RectF(0f, 0f, vw.toFloat().coerceAtLeast(1080f), vh.toFloat().coerceAtLeast(1920f))
                    textExtractor.extractFromAccessibilityTree(
                        service = service,
                        region = SelectionRegion(fullRect, SelectionShape.RECTANGLE),
                        entireScreen = true
                    )
                }
            }

            if (isShowing && finalBlocks.isNotEmpty()) {
                languageBarHelper?.setDetectedFullScreenTextBlocks(finalBlocks)
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

    private fun applyOverlayPreferences() {
        selectionView?.apply {
            shape = preferences.defaultSelectionShape
            borderColor = preferences.borderColor
            borderThicknessDp = preferences.borderThicknessDp
            dimOpacity = preferences.overlayDimOpacity
            hapticEnabled = preferences.hapticFeedbackEnabled
        }
    }

    private fun updateActionButtonsUi() {
        val translateActive = activeMode == ActionMode.TRANSLATE
        btnTranslate?.setBackgroundResource(
            if (translateActive) R.drawable.bg_action_button_active else R.drawable.bg_action_button_inactive
        )
        btnVisualSearch?.setBackgroundResource(
            if (!translateActive) R.drawable.bg_action_button_active else R.drawable.bg_action_button_inactive
        )
        languageBarHelper?.setVisibleForMode(activeMode)
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
                    val engineName = preferences.visualSearchEngine.shortLabel
                    updateStatusHint(locCtx().getString(R.string.overlay_countdown_fmt, engineName, 2))
                    delay(totalMs / 2)
                    updateStatusHint(locCtx().getString(R.string.overlay_countdown_fmt, engineName, 1))
                    delay(totalMs - totalMs / 2)
                    executeVisualSearch(service, region)
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
                useCache = preferences.translationCacheEnabled
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
                useCache = preferences.translationCacheEnabled
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

    private fun executeVisualSearch(service: AccessibilityService?, region: SelectionRegion) {
        val decision = safeModeManager.evaluateActiveApp()
        if (decision is SafeModeManager.SafeModeDecision.Blocked) {
            haltAllAndDismiss(decision.reason)
            return
        }

        activeTaskJob?.cancel()
        activeTaskJob = scope.launch {
            currentState = BubbleOverlayState.Searching(region)
            val engine = preferences.visualSearchEngine
            updateStatusHint(locCtx().getString(R.string.overlay_preparing_search_fmt, engine.shortLabel))

            val cropped = obtainCroppedBitmap(
                service = service,
                region = region,
                entireScreen = false,
                forOcr = false
            ) ?: return@launch

            val dispatch = try {
                lensManager.launchVisualSearch(cropped.bitmap, engine)
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
        forOcr: Boolean
    ): CroppedBitmapResult? {
        val maxDim = performanceManager.getMaxOcrDimensionPx()
        val vw = selectionView?.width ?: 0
        val vh = selectionView?.height ?: 0

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
                    forOcr = forOcr
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
                            forOcr = forOcr
                        )
                    }
                }
                ScreenCaptureManager.FullScreenCaptureResult.ProtectedContent -> {
                    haltAllAndDismiss("Layar dilindungi FLAG_SECURE. Bubble tidak membaca konten terlindungi.")
                    null
                }
                ScreenCaptureManager.FullScreenCaptureResult.RateLimited -> {
                    updateStatusHint("Tunggu sebentar sebelum memindai ulang...")
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
            updateStatusHint("Meminta izin capture layar...")
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
                haltAllAndDismiss("Layar dilindungi FLAG_SECURE. Bubble tidak membaca konten terlindungi.")
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
            ActionMode.VISUAL_SEARCH -> executeVisualSearch(service, region)
        }
    }

    fun onCaptureDenied() {
        rootView?.visibility = View.VISIBLE
        rootView?.requestFocus()
        pendingRegionAfterConsent = null
        updateStatusHint("Izin screen capture ditolak.")
    }

    fun haltAllAndDismiss(notice: String? = null) {
        debounceJob?.cancel()
        activeTaskJob?.cancel()
        qrScanJob?.cancel()
        fullScreenTextJob?.cancel()
        pendingRegionAfterConsent = null
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
        languageBarHelper = null
        activeServiceRef = null
        if (view != null) {
            runCatching { windowManager.removeView(view) }
        }
        currentState = BubbleOverlayState.Idle
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
