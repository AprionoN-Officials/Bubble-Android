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
import android.widget.LinearLayout
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
import com.bubble.screentools.model.ActionMode
import com.bubble.screentools.model.BubbleOverlayState
import com.bubble.screentools.model.SelectionRegion
import com.bubble.screentools.model.SelectionShape
import com.bubble.screentools.model.TextBlockResult
import com.bubble.screentools.ocr.OCRManager
import com.bubble.screentools.qr.QrActionHelper
import com.bubble.screentools.qr.QrCodeScanner
import com.bubble.screentools.translation.LanguageDetectionManager
import com.bubble.screentools.translation.TranslationManager
import com.bubble.screentools.util.PerformanceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fullscreen edge-to-edge session UI launched when Bubble is invoked as the
 * Default Digital Assistant (like CircleToSearch v0.5).
 * Includes Auto QR Code detection, Auto Full-Screen Live Text Select & Copy (without Lens!),
 * cancelable Visual Search countdown, and high-accuracy OCR-first text detection.
 */
class BubbleAssistantActivity : AppCompatActivity() {

    private lateinit var preferences: BubblePreferences
    private lateinit var performanceManager: PerformanceManager
    private lateinit var lensManager: LensIntegrationManager
    private lateinit var ocrManager: OCRManager

    private val qrScanner = QrCodeScanner()
    private val languageDetector = LanguageDetectionManager()
    private val translationManager = TranslationManager(languageDetector)

    private var frozenScreenshot: Bitmap? = null
    private var assistTextBlocks: List<TextBlockResult> = emptyList()

    private lateinit var ivFrozenScreen: ImageView
    private lateinit var selectionView: SelectionOverlayView
    private lateinit var translationRenderer: TranslationOverlayRenderer
    private lateinit var btnTranslate: LinearLayout
    private lateinit var btnVisualSearch: LinearLayout
    private lateinit var btnToggleOriginal: Button
    private lateinit var btnCopyResultText: Button
    private lateinit var tvStatusHint: TextView
    private lateinit var topBarSpacer: View
    private lateinit var languageBarHelper: OverlayLanguageBarHelper

    private var activeMode: ActionMode = ActionMode.TRANSLATE
    private var currentState: BubbleOverlayState = BubbleOverlayState.Idle
    private var debounceJob: Job? = null
    private var activeTaskJob: Job? = null
    private var qrScanJob: Job? = null
    private var fullScreenTextJob: Job? = null
    private var showOriginalText = false

    override fun onCreate(savedInstanceState: Bundle?) {
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

        preferences = BubblePreferences(this)
        performanceManager = PerformanceManager(preferences)
        lensManager = LensIntegrationManager(this)
        ocrManager = OCRManager(this, preferences)
        activeMode = preferences.lastActionMode

        setContentView(R.layout.overlay_bubble_container)

        val root = findViewById<OverlayRootFrameLayout>(R.id.overlayRoot)
        val topBar = findViewById<View>(R.id.topControlsBar)
        val bottomBar = findViewById<View>(R.id.bottomActionRow)
        ivFrozenScreen = findViewById(R.id.ivFrozenScreen)
        selectionView = findViewById(R.id.selectionOverlayView)
        translationRenderer = findViewById(R.id.translationOverlayRenderer)
        btnTranslate = findViewById(R.id.btnActionTranslate)
        btnVisualSearch = findViewById(R.id.btnActionVisualSearch)
        btnToggleOriginal = findViewById(R.id.btnToggleOriginal)
        btnCopyResultText = findViewById(R.id.btnCopyResultText)
        tvStatusHint = findViewById(R.id.tvStatusHint)
        topBarSpacer = findViewById(R.id.topBarSpacer)

        if (frozenScreenshot != null) {
            ivFrozenScreen.setImageBitmap(frozenScreenshot)
            ivFrozenScreen.visibility = View.VISIBLE
        }

        val topBasePadding = topBar.paddingTop
        val bottomBasePadding = bottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sysBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            topBar.setPadding(
                topBar.paddingLeft,
                topBasePadding + sysBars.top,
                topBar.paddingRight,
                topBar.paddingBottom
            )
            bottomBar.setPadding(
                bottomBar.paddingLeft,
                bottomBar.paddingTop,
                bottomBar.paddingRight,
                bottomBasePadding + sysBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)

        val handleBack: () -> Unit = {
            when {
                languageBarHelper.closePicker() -> Unit
                languageBarHelper.exitTextSelectMode() -> Unit
                else -> finishCleanly()
            }
        }
        root.onBackPressedListener = handleBack
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })

        findViewById<ImageButton>(R.id.btnCloseOverlay).setOnClickListener {
            finishCleanly()
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
                    executeVisualSearch(region)
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

        selectionView.apply {
            shape = preferences.defaultSelectionShape
            borderColor = preferences.borderColor
            borderThicknessDp = preferences.borderThicknessDp
            dimOpacity = preferences.overlayDimOpacity
            hapticEnabled = preferences.hapticFeedbackEnabled
        }
        updateActionButtonsUi()

        selectionView.listener = object : SelectionOverlayView.SelectionListener {
            override fun onSelectionInteractionStarted() {
                // Immediately cancel any pending auto-launch and hide buttons while resizing/selecting!
                debounceJob?.cancel()
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

        btnTranslate.setOnClickListener {
            debounceJob?.cancel()
            activeMode = ActionMode.TRANSLATE
            preferences.lastActionMode = activeMode
            updateActionButtonsUi()
            val region = selectionView.getCurrentRegion()
            if (region.isValid()) {
                executeTranslate(region)
            }
        }

        btnVisualSearch.setOnClickListener {
            debounceJob?.cancel()
            activeMode = ActionMode.VISUAL_SEARCH
            preferences.lastActionMode = activeMode
            updateActionButtonsUi()
            val region = selectionView.getCurrentRegion()
            if (region.isValid()) {
                executeVisualSearch(region)
            }
        }

        currentState = BubbleOverlayState.Selecting(activeMode)

        // Auto-scan QR codes + full-screen Live Text immediately once view dimensions are ready!
        root.post {
            if (isFinishing) return@post
            if (preferences.autoQrScanEnabled && frozenScreenshot != null) {
                runAutoQrScanFullScreen()
            }
            runAutoFullScreenTextScan()
        }
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

    private fun updateActionButtonsUi() {
        val translateActive = activeMode == ActionMode.TRANSLATE
        btnTranslate.setBackgroundResource(
            if (translateActive) R.drawable.bg_action_button_active else R.drawable.bg_action_button_inactive
        )
        btnVisualSearch.setBackgroundResource(
            if (!translateActive) R.drawable.bg_action_button_active else R.drawable.bg_action_button_inactive
        )
        languageBarHelper.setVisibleForMode(activeMode)
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
                    val engineName = preferences.visualSearchEngine.shortLabel
                    updateStatusHint(getString(R.string.overlay_countdown_fmt, engineName, 2))
                    delay(totalMs / 2)
                    updateStatusHint(getString(R.string.overlay_countdown_fmt, engineName, 1))
                    delay(totalMs - totalMs / 2)
                    executeVisualSearch(region)
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
                useCache = preferences.translationCacheEnabled
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
                        forOcr = true
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
                useCache = preferences.translationCacheEnabled
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

    private fun executeVisualSearch(region: SelectionRegion) {
        val snapshot = frozenScreenshot
        if (snapshot == null || snapshot.isRecycled) {
            updateStatusHint(getString(R.string.overlay_flag_secure_blocked))
            return
        }

        activeTaskJob?.cancel()
        activeTaskJob = lifecycleScope.launch {
            currentState = BubbleOverlayState.Searching(region)
            val engine = preferences.visualSearchEngine
            updateStatusHint(getString(R.string.overlay_preparing_search_fmt, engine.shortLabel))

            val cropped = withContext(Dispatchers.Default) {
                BitmapCropHelper.cropAndMask(
                    source = snapshot,
                    region = region,
                    maxDimensionPx = performanceManager.getMaxOcrDimensionPx(),
                    viewWidth = selectionView.width,
                    viewHeight = selectionView.height,
                    forOcr = false
                )
            }
            val dispatch = try {
                lensManager.launchVisualSearch(cropped.bitmap, engine)
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
        finish()
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
