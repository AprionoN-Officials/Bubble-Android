package com.bubble.screentools.ui.overlay

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bubble.screentools.R
import com.bubble.screentools.capture.BitmapCropHelper
import com.bubble.screentools.capture.SessionSnapshotStore
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.lens.LensIntegrationManager
import com.bubble.screentools.lens.SearchEngineRegistry
import com.bubble.screentools.model.ActionMode
import com.bubble.screentools.model.ActionPanelButtonType
import com.bubble.screentools.model.BubbleOverlayState
import com.bubble.screentools.model.SelectionRegion
import com.bubble.screentools.model.SelectionShape
import com.bubble.screentools.model.TextBlockResult
import com.bubble.screentools.ocr.OCRManager
import com.bubble.screentools.qr.QrActionHelper
import com.bubble.screentools.qr.QrCodeScanner
import com.bubble.screentools.theme.ThemeManager
import com.bubble.screentools.translation.LanguageDetectionManager
import com.bubble.screentools.translation.TranslationManager
import com.bubble.screentools.util.PerformanceManager
import com.bubble.screentools.util.ScreenLinkDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/**
 * Fullscreen edge-to-edge session UI launched when Bubble is invoked as the
 * Default Digital Assistant (zero recording prompt, zero Accessibility required).
 * Integrates custom Selection appearance, custom Action Panel (with 2-button default),
 * Quality/Context Translation, Multi-Engine Search, Auto QR, Auto Link Detection, Live Text,
 * and Copy/Save/Share image actions.
 */
class BubbleAssistantActivity : AppCompatActivity() {

    private lateinit var preferences: BubblePreferences
    private lateinit var performanceManager: PerformanceManager
    private lateinit var lensManager: LensIntegrationManager
    private lateinit var searchRegistry: SearchEngineRegistry
    private lateinit var ocrManager: OCRManager

    private val qrScanner = QrCodeScanner()
    private val languageDetector = LanguageDetectionManager()
    private val translationManager = TranslationManager(languageDetector)

    private var frozenScreenshot: Bitmap? = null
    private var assistTextBlocks: List<TextBlockResult> = emptyList()

    private lateinit var ivFrozenScreen: ImageView
    private lateinit var selectionView: SelectionOverlayView
    private lateinit var translationRenderer: TranslationOverlayRenderer
    private lateinit var actionPanelView: ActionPanelView
    private lateinit var bottomActionRow: View
    private lateinit var btnToggleOriginal: Button
    private lateinit var btnCopyResultText: Button
    private lateinit var tvStatusHint: TextView
    private lateinit var topBarSpacer: View
    private lateinit var uploadConfirmCard: View
    private lateinit var tvUploadConfirmMsg: TextView
    private lateinit var btnCancelUpload: View
    private lateinit var btnProceedUpload: View
    private lateinit var languageBarHelper: OverlayLanguageBarHelper

    private var activeMode: ActionMode = ActionMode.TRANSLATE
    private var currentState: BubbleOverlayState = BubbleOverlayState.Idle
    private var debounceJob: Job? = null
    private var activeTaskJob: Job? = null
    private var qrScanJob: Job? = null
    private var fullScreenTextJob: Job? = null
    private var showOriginalText = false
    private var pendingConfirmedSearchRegion: SelectionRegion? = null
    private var lastInsetLeft = 0
    private var lastInsetTop = 0
    private var lastInsetRight = 0
    private var lastInsetBottom = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = BubblePreferences(this)
        ThemeManager.applyToActivityBeforeContent(this, preferences)
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val payload = SessionSnapshotStore.consume()
        if (payload == null) {
            finish()
            return
        }

        frozenScreenshot = payload.screenshot
        assistTextBlocks = payload.assistTextBlocks

        performanceManager = PerformanceManager(preferences)
        lensManager = LensIntegrationManager(this)
        searchRegistry = SearchEngineRegistry(preferences)
        ocrManager = OCRManager(this, preferences)
        activeMode = preferences.lastActionMode

        setContentView(R.layout.overlay_bubble_container)

        val root = findViewById<OverlayRootFrameLayout>(R.id.overlayRoot)
        val topBar = findViewById<View>(R.id.topControlsBar)
        bottomActionRow = findViewById(R.id.bottomActionRow)
        ivFrozenScreen = findViewById(R.id.ivFrozenScreen)
        selectionView = findViewById(R.id.selectionOverlayView)
        translationRenderer = findViewById(R.id.translationOverlayRenderer)
        actionPanelView = findViewById(R.id.primaryActionButtonsRow)
        btnToggleOriginal = findViewById(R.id.btnToggleOriginal)
        btnCopyResultText = findViewById(R.id.btnCopyResultText)
        tvStatusHint = findViewById(R.id.tvStatusHint)
        topBarSpacer = findViewById(R.id.topBarSpacer)
        uploadConfirmCard = findViewById(R.id.uploadConfirmCard)
        tvUploadConfirmMsg = findViewById(R.id.tvUploadConfirmMsg)
        btnCancelUpload = findViewById(R.id.btnCancelUpload)
        btnProceedUpload = findViewById(R.id.btnProceedUpload)

        if (frozenScreenshot != null) {
            ivFrozenScreen.setImageBitmap(frozenScreenshot)
            ivFrozenScreen.visibility = View.VISIBLE
        }

        val panelConfig = preferences.actionPanelConfig
        val topBasePadding = topBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sysBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            lastInsetLeft = sysBars.left
            lastInsetTop = sysBars.top
            lastInsetRight = sysBars.right
            lastInsetBottom = sysBars.bottom
            topBar.setPadding(
                topBar.paddingLeft,
                topBasePadding + sysBars.top,
                topBar.paddingRight,
                topBar.paddingBottom
            )
            ActionPanelView.applyContainerPosition(
                container = bottomActionRow,
                config = panelConfig,
                insetLeft = sysBars.left,
                insetTop = sysBars.top,
                insetRight = sysBars.right,
                insetBottom = sysBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)

        val handleBack: () -> Unit = {
            when {
                uploadConfirmCard.visibility == View.VISIBLE -> {
                    uploadConfirmCard.visibility = View.GONE
                    pendingConfirmedSearchRegion = null
                }
                languageBarHelper.closePicker() -> Unit
                languageBarHelper.exitTextSelectMode() -> Unit
                else -> finishCleanly()
            }
        }
        root.onBackPressedListener = handleBack
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })

        findViewById<View>(R.id.btnFullScreenSelect).setOnClickListener {
            expandSelectionToFullScreen()
        }

        findViewById<ImageButton>(R.id.btnCloseOverlay).setOnClickListener {
            finishCleanly()
        }

        btnCancelUpload.setOnClickListener {
            uploadConfirmCard.visibility = View.GONE
            pendingConfirmedSearchRegion = null
            updateStatusHint(null)
        }

        btnProceedUpload.setOnClickListener {
            uploadConfirmCard.visibility = View.GONE
            val region = pendingConfirmedSearchRegion ?: return@setOnClickListener
            pendingConfirmedSearchRegion = null
            dispatchVisualSearchNow(region)
        }

        languageBarHelper = OverlayLanguageBarHelper(
            context = this,
            rootView = root,
            preferences = preferences,
            onLanguagePairChanged = {
                val region = selectionView.getCurrentRegion()
                if (activeMode == ActionMode.TRANSLATE && region.isValid()) {
                    executeTranslate(region)
                }
            },
            onSearchEngineChanged = {
                val region = selectionView.getCurrentRegion()
                if (activeMode == ActionMode.VISUAL_SEARCH && region.isValid()) {
                    requestVisualSearch(region)
                }
            },
            onTranslateSelectedBlocksRequested = { selectedBlocks ->
                translateExplicitBlocks(selectedBlocks)
            },
            onDismissOverlayRequested = { finishCleanly() }
        )

        btnToggleOriginal.setOnClickListener {
            val resultState = currentState as? BubbleOverlayState.Result ?: return@setOnClickListener
            showOriginalText = !showOriginalText
            btnToggleOriginal.text = getString(
                if (showOriginalText) R.string.overlay_translated else R.string.overlay_original
            )
            translationRenderer.setTranslations(
                resultState.blocks,
                showOriginalText,
                preferences.translationOverlayStyle
            )
        }

        btnCopyResultText.setOnClickListener {
            val resultState = currentState as? BubbleOverlayState.Result ?: return@setOnClickListener
            val text = resultState.blocks.joinToString("\n") {
                if (showOriginalText) it.originalText else it.translatedText
            }
            if (text.isNotBlank()) {
                QrActionHelper.copyToClipboard(
                    this,
                    getString(
                        if (showOriginalText) R.string.overlay_label_original_text else R.string.overlay_label_translated_text
                    ),
                    text
                )
            }
        }

        applySelectionPreferences()
        setupActionPanel()

        selectionView.listener = object : SelectionOverlayView.SelectionListener {
            override fun onSelectionInteractionStarted() {
                debounceJob?.cancel()
                uploadConfirmCard.visibility = View.GONE
                updateStatusHint(null)
                languageBarHelper.setDraggingMode(true)
            }

            override fun onSelectionInteractionEnded() {
                languageBarHelper.setDraggingMode(false)
            }

            override fun onSelectionChanged(region: SelectionRegion, wasMoveOnly: Boolean) {
                translationRenderer.clear()
                btnToggleOriginal.visibility = View.GONE
                btnCopyResultText.visibility = View.GONE
                languageBarHelper.setTranslationResultVisible(false)
                if (preferences.autoQrScanEnabled) {
                    runAutoQrScanForRegion(region)
                }
                if (preferences.autoLinkDetectEnabled) {
                    languageBarHelper.updateLinksForRegion(region.bounds)
                }
                val shouldAutoRun = when (activeMode) {
                    ActionMode.TRANSLATE -> preferences.autoTranslateSelection
                    ActionMode.VISUAL_SEARCH -> preferences.autoSearchAfterSelection && !wasMoveOnly
                }
                if (shouldAutoRun) {
                    scheduleDebouncedAction(region)
                }
            }

            override fun onSelectionCleared() {
                debounceJob?.cancel()
                translationRenderer.clear()
                btnToggleOriginal.visibility = View.GONE
                btnCopyResultText.visibility = View.GONE
                languageBarHelper.setTranslationResultVisible(false)
                updateStatusHint(null)
            }
        }

        currentState = BubbleOverlayState.Selecting(activeMode)

        root.post {
            if (isFinishing) return@post
            if (preferences.autoQrScanEnabled && frozenScreenshot != null) {
                runAutoQrScanFullScreen()
            }
            runAutoFullScreenTextScan()
        }
    }

    private fun applySelectionPreferences() {
        val selConfig = preferences.selectionConfig
        val themeConfig = preferences.themeConfig
        val resolvedBorder = ThemeManager.resolveSelectionBorderColor(this, selConfig, themeConfig)
        val allowAnim = ThemeManager.areAnimationsAllowed(this, selConfig.animationEnabled)
        selectionView.applyConfig(selConfig, resolvedBorder, allowAnim)
        selectionView.hapticEnabled = preferences.hapticFeedbackEnabled
    }

    private fun setupActionPanel() {
        val accent = ThemeManager.resolveOverlayAccentColor(this, preferences)
        actionPanelView.render(preferences.actionPanelConfig, activeMode, accent)
        actionPanelView.onActionButtonClick = { actionType ->
            handlePanelAction(actionType)
        }
        languageBarHelper.setVisibleForMode(activeMode)
    }

    private fun handlePanelAction(actionType: ActionPanelButtonType) {
        debounceJob?.cancel()
        uploadConfirmCard.visibility = View.GONE
        val region = selectionView.getCurrentRegion()

        when (actionType) {
            ActionPanelButtonType.TRANSLATE -> {
                activeMode = ActionMode.TRANSLATE
                preferences.lastActionMode = activeMode
                actionPanelView.updateActiveMode(activeMode)
                languageBarHelper.setVisibleForMode(activeMode)
                if (region.isValid()) executeTranslate(region)
            }
            ActionPanelButtonType.SEARCH_IMAGE -> {
                activeMode = ActionMode.VISUAL_SEARCH
                preferences.lastActionMode = activeMode
                actionPanelView.updateActiveMode(activeMode)
                languageBarHelper.setVisibleForMode(activeMode)
                if (region.isValid()) requestVisualSearch(region)
            }
            ActionPanelButtonType.FULL_SCREEN -> {
                expandSelectionToFullScreen()
            }
            ActionPanelButtonType.SELECT_TEXT -> {
                languageBarHelper.enterTextSelectMode()
            }
            ActionPanelButtonType.COPY_IMAGE -> executeImageTool(region) { bmp ->
                val ok = OverlayExtraActionsHelper.copyImageToClipboard(this, bmp)
                Toast.makeText(
                    this,
                    getString(if (ok) R.string.panel_toast_image_copied else R.string.panel_toast_action_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
            ActionPanelButtonType.SAVE_IMAGE -> executeImageTool(region) { bmp ->
                val ok = OverlayExtraActionsHelper.saveImageToGallery(this, bmp)
                Toast.makeText(
                    this,
                    getString(if (ok) R.string.panel_toast_image_saved else R.string.panel_toast_action_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
            ActionPanelButtonType.SHARE_IMAGE -> executeImageTool(region) { bmp ->
                val ok = OverlayExtraActionsHelper.shareImage(this, bmp, getString(R.string.panel_action_share_image))
                if (ok) finishCleanly()
            }
        }
    }

    private fun executeImageTool(region: SelectionRegion, onBitmapReady: (Bitmap) -> Unit) {
        val snapshot = frozenScreenshot
        if (snapshot == null || snapshot.isRecycled) {
            updateStatusHint(getString(R.string.overlay_flag_secure_blocked))
            return
        }
        if (!region.isValid()) return
        activeTaskJob?.cancel()
        activeTaskJob = lifecycleScope.launch {
            val cropped = withContext(Dispatchers.Default) {
                BitmapCropHelper.cropAndMask(
                    source = snapshot,
                    region = region,
                    maxDimensionPx = 2400,
                    viewWidth = selectionView.width,
                    viewHeight = selectionView.height,
                    forOcr = false
                )
            }
            try {
                onBitmapReady(cropped.bitmap)
            } finally {
                if (!cropped.bitmap.isRecycled) cropped.bitmap.recycle()
            }
        }
    }

    private fun expandSelectionToFullScreen() {
        debounceJob?.cancel()
        uploadConfirmCard.visibility = View.GONE
        pendingConfirmedSearchRegion = null
        translationRenderer.clear()
        btnToggleOriginal.visibility = View.GONE
        btnCopyResultText.visibility = View.GONE
        languageBarHelper.setTranslationResultVisible(false)
        selectionView.selectFullScreen(
            insetLeft = lastInsetLeft,
            insetTop = lastInsetTop,
            insetRight = lastInsetRight,
            insetBottom = lastInsetBottom
        )
        updateStatusHint(getString(R.string.overlay_fullscreen_ready_hint))
    }

    private fun runAutoFullScreenTextScan() {
        fullScreenTextJob?.cancel()
        fullScreenTextJob = lifecycleScope.launch {
            val snapshot = frozenScreenshot
            val vw = selectionView.width
            val vh = selectionView.height

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

            val finalBlocks = if (ocrBlocks.isNotEmpty()) ocrBlocks else assistTextBlocks
            if (!isFinishing && finalBlocks.isNotEmpty()) {
                languageBarHelper.setDetectedFullScreenTextBlocks(finalBlocks)
            }
            if (!isFinishing && preferences.autoLinkDetectEnabled && assistTextBlocks.isNotEmpty()) {
                val extraLinks = ScreenLinkDetector.extractLinksFromBlocks(assistTextBlocks)
                if (extraLinks.isNotEmpty()) {
                    languageBarHelper.mergeExtraDetectedLinks(extraLinks)
                }
            }
        }
    }

    private fun runAutoQrScanFullScreen() {
        val snapshot = frozenScreenshot ?: return
        if (snapshot.isRecycled) return
        qrScanJob?.cancel()
        qrScanJob = lifecycleScope.launch {
            val found = withContext(Dispatchers.Default) {
                qrScanner.scanBitmap(snapshot)
            }
            val first = found.firstOrNull()
            if (first != null && !isFinishing) {
                languageBarHelper.showDetectedQr(first) { frozenScreenshot }
            }
        }
    }

    private fun runAutoQrScanForRegion(region: SelectionRegion) {
        val snapshot = frozenScreenshot ?: return
        if (snapshot.isRecycled) return
        qrScanJob?.cancel()
        qrScanJob = lifecycleScope.launch {
            val cropped = withContext(Dispatchers.Default) {
                BitmapCropHelper.cropAndMask(
                    source = snapshot,
                    region = region,
                    maxDimensionPx = 1400,
                    viewWidth = selectionView.width,
                    viewHeight = selectionView.height,
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
            if (first != null && !isFinishing) {
                languageBarHelper.showDetectedQr(first) { frozenScreenshot }
            }
        }
    }

    private fun scheduleDebouncedAction(region: SelectionRegion) {
        debounceJob?.cancel()
        debounceJob = lifecycleScope.launch {
            when (activeMode) {
                ActionMode.TRANSLATE -> {
                    delay(performanceManager.getTranslateDebounceMs())
                    executeTranslate(region)
                }
                ActionMode.VISUAL_SEARCH -> {
                    val totalMs = performanceManager.getVisualSearchDebounceMs()
                    val engineName = searchRegistry.getActiveVisualEngine().name
                    if (totalMs <= 0L) {
                        requestVisualSearch(region)
                    } else {
                        var remainingMs = totalMs
                        while (remainingMs > 0L) {
                            val secLabel = ceil(remainingMs / 1000.0).toInt().coerceAtLeast(1)
                            updateStatusHint(getString(R.string.overlay_countdown_fmt, engineName, secLabel))
                            val stepMs = minOf(1000L, remainingMs)
                            delay(stepMs)
                            remainingMs -= stepMs
                        }
                        requestVisualSearch(region)
                    }
                }
            }
        }
    }

    private fun translateExplicitBlocks(blocks: List<TextBlockResult>) {
        if (blocks.isEmpty()) return
        activeTaskJob?.cancel()
        activeTaskJob = lifecycleScope.launch {
            updateStatusHint(getString(R.string.overlay_translating_fmt, blocks.size))
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
            languageBarHelper.lastDetectedSourceLang = translated.firstOrNull()?.detectedLangCode
            showOriginalText = false
            currentState = BubbleOverlayState.Result(ActionMode.TRANSLATE, translated, showOriginal = false)
            translationRenderer.setTranslations(
                translated,
                showOriginalText = false,
                style = preferences.translationOverlayStyle
            )
            btnToggleOriginal.text = getString(R.string.overlay_original)
            btnToggleOriginal.visibility = View.VISIBLE
            btnCopyResultText.visibility = View.VISIBLE
            languageBarHelper.setTranslationResultVisible(true)
            updateStatusHint(null)
        }
    }

    private fun executeTranslate(region: SelectionRegion) {
        activeTaskJob?.cancel()
        activeTaskJob = lifecycleScope.launch {
            currentState = BubbleOverlayState.Scanning(ActionMode.TRANSLATE, region)
            updateStatusHint(getString(R.string.overlay_reading_text))

            val snapshot = frozenScreenshot
            val vw = selectionView.width
            val vh = selectionView.height

            val ocrBlocks = if (snapshot != null && !snapshot.isRecycled) {
                val effectiveRegion = if (preferences.translateEntireScreen) {
                    val targetW = if (vw > 0) vw.toFloat() else snapshot.width.toFloat()
                    val targetH = if (vh > 0) vh.toFloat() else snapshot.height.toFloat()
                    SelectionRegion(
                        bounds = RectF(0f, 0f, targetW, targetH),
                        shape = SelectionShape.RECTANGLE
                    )
                } else {
                    region
                }
                val cropped = withContext(Dispatchers.Default) {
                    BitmapCropHelper.cropAndMask(
                        source = snapshot,
                        region = effectiveRegion,
                        maxDimensionPx = performanceManager.getMaxOcrDimensionPx(),
                        viewWidth = vw,
                        viewHeight = vh,
                        forOcr = true,
                        qualityMode = preferences.translationQualityMode
                    )
                }
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
                assistTextBlocks.filter {
                    preferences.translateEntireScreen || RectF.intersects(it.boundingBox, region.bounds)
                }
            }

            if (blocks.isEmpty()) {
                updateStatusHint(getString(R.string.overlay_no_text_found))
                return@launch
            }

            currentState = BubbleOverlayState.Translating(region, blocks.size)
            updateStatusHint(getString(R.string.overlay_translating_fmt, blocks.size))

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

            languageBarHelper.lastDetectedSourceLang = translated.firstOrNull()?.detectedLangCode
            showOriginalText = false
            currentState = BubbleOverlayState.Result(ActionMode.TRANSLATE, translated, showOriginal = false)
            translationRenderer.setTranslations(
                translated,
                showOriginalText = false,
                style = preferences.translationOverlayStyle
            )
            btnToggleOriginal.text = getString(R.string.overlay_original)
            btnToggleOriginal.visibility = View.VISIBLE
            btnCopyResultText.visibility = View.VISIBLE
            languageBarHelper.setTranslationResultVisible(true)
            updateStatusHint(null)
        }
    }

    private fun requestVisualSearch(region: SelectionRegion) {
        if (preferences.confirmBeforeImageUpload) {
            pendingConfirmedSearchRegion = region
            val engineName = searchRegistry.getActiveVisualEngine().name
            tvUploadConfirmMsg.text = getString(R.string.search_confirm_upload_msg, engineName)
            uploadConfirmCard.visibility = View.VISIBLE
            return
        }
        dispatchVisualSearchNow(region)
    }

    private fun dispatchVisualSearchNow(region: SelectionRegion) {
        val snapshot = frozenScreenshot
        if (snapshot == null || snapshot.isRecycled) {
            updateStatusHint(getString(R.string.overlay_flag_secure_blocked))
            return
        }

        activeTaskJob?.cancel()
        activeTaskJob = lifecycleScope.launch {
            currentState = BubbleOverlayState.Searching(region)
            val engineItem = searchRegistry.getActiveVisualEngine()
            updateStatusHint(getString(R.string.overlay_preparing_search_fmt, engineItem.name))

            val cropped = withContext(Dispatchers.Default) {
                BitmapCropHelper.cropAndMask(
                    source = snapshot,
                    region = region,
                    maxDimensionPx = preferences.searchMaxResolutionPx,
                    viewWidth = selectionView.width,
                    viewHeight = selectionView.height,
                    forOcr = false
                )
            }
            val dispatch = try {
                lensManager.launchVisualSearch(cropped.bitmap, engineItem)
            } finally {
                if (!cropped.bitmap.isRecycled) {
                    cropped.bitmap.recycle()
                }
            }

            when (dispatch) {
                is LensIntegrationManager.VisualSearchDispatchResult.OpenedSharesheet -> {
                    Toast.makeText(this@BubbleAssistantActivity, dispatch.fallbackNotice, Toast.LENGTH_LONG).show()
                    finishCleanly()
                }
                is LensIntegrationManager.VisualSearchDispatchResult.Error -> {
                    updateStatusHint(dispatch.message)
                }
                else -> finishCleanly()
            }
        }
    }

    private fun updateStatusHint(text: String?) {
        if (text.isNullOrBlank()) {
            tvStatusHint.visibility = View.GONE
            topBarSpacer.visibility = View.VISIBLE
        } else {
            tvStatusHint.text = text
            tvStatusHint.visibility = View.VISIBLE
            topBarSpacer.visibility = View.GONE
        }
    }

    private fun finishCleanly() {
        debounceJob?.cancel()
        activeTaskJob?.cancel()
        qrScanJob?.cancel()
        fullScreenTextJob?.cancel()
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        debounceJob?.cancel()
        activeTaskJob?.cancel()
        qrScanJob?.cancel()
        fullScreenTextJob?.cancel()
        ocrManager.release()
        qrScanner.release()
        translationManager.release()
        languageDetector.release()
        ivFrozenScreen.setImageDrawable(null)
        frozenScreenshot?.let { if (!it.isRecycled) it.recycle() }
        frozenScreenshot = null
        super.onDestroy()
    }
}
