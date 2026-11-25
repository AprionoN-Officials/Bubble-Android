package com.bubble.screentools.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.lens.LensIntegrationManager
import com.bubble.screentools.model.ActionMode
import com.bubble.screentools.model.SelectionShape
import com.bubble.screentools.model.VisualSearchEngine
import com.bubble.screentools.ocr.TesseractOcrManager
import com.bubble.screentools.service.BubbleAccessibilityService
import com.bubble.screentools.service.MediaProjectionService
import com.bubble.screentools.translation.LanguageDetectionManager
import com.bubble.screentools.translation.SupportedLanguages
import com.bubble.screentools.translation.TranslationManager
import com.bubble.screentools.util.CompatibilityHelper
import com.bubble.screentools.util.LocaleHelper
import com.bubble.screentools.util.PerformanceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

/**
 * Mode A — Bilingual (English & Indonesian) Material 3 Segmented Settings & Dashboard.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var preferences: BubblePreferences
    private lateinit var lensManager: LensIntegrationManager

    private lateinit var cardSafeModeNotice: View
    private lateinit var tvSafeModeExplanation: TextView
    private lateinit var tvPermissionSummary: TextView
    private lateinit var btnPickSourceLang: MaterialButton
    private lateinit var btnPickTargetLang: MaterialButton
    private lateinit var tvActiveLangPairDesc: TextView
    private lateinit var tvOcrEngineStatus: TextView

    private lateinit var swAutoDetectLang: MaterialSwitch
    private lateinit var swAutoTranslate: MaterialSwitch
    private lateinit var swTranslateEntireScreen: MaterialSwitch
    private lateinit var swAutoVisualSearch: MaterialSwitch
    private lateinit var swAutoQrScan: MaterialSwitch
    private lateinit var swSafeMode: MaterialSwitch

    private lateinit var chipGroupSearchEngine: ChipGroup
    private lateinit var chipGroupShape: ChipGroup
    private lateinit var chipGroupActionMode: ChipGroup
    private lateinit var chipGroupAppLanguage: ChipGroup
    private lateinit var btnManageExcludedApps: MaterialButton
    private lateinit var btnLegacyCapture: MaterialButton

    private var isBindingUi = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionService.startWithGrant(this, result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, getString(R.string.overlay_capture_denied), Toast.LENGTH_SHORT).show()
        }
        refreshUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        CompatibilityHelper.applySystemBarAppearance(window, this)
        setContentView(R.layout.activity_main)

        preferences = BubblePreferences(this)
        lensManager = LensIntegrationManager(this)

        val root = findViewById<View>(R.id.mainContainer)
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                baseLeft + bars.left,
                baseTop + bars.top,
                baseRight + bars.right,
                baseBottom + bars.bottom
            )
            insets
        }

        bindViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun bindViews() {
        cardSafeModeNotice = findViewById(R.id.cardSafeModeNotice)
        tvSafeModeExplanation = findViewById(R.id.tvSafeModeExplanation)
        tvPermissionSummary = findViewById(R.id.tvPermissionSummary)
        btnPickSourceLang = findViewById(R.id.btnPickSourceLang)
        btnPickTargetLang = findViewById(R.id.btnPickTargetLang)
        tvActiveLangPairDesc = findViewById(R.id.tvActiveLangPairDesc)
        tvOcrEngineStatus = findViewById(R.id.tvOcrEngineStatus)

        swAutoDetectLang = findViewById(R.id.swAutoDetectLang)
        swAutoTranslate = findViewById(R.id.swAutoTranslate)
        swTranslateEntireScreen = findViewById(R.id.swTranslateEntireScreen)
        swAutoVisualSearch = findViewById(R.id.swAutoVisualSearch)
        swAutoQrScan = findViewById(R.id.swAutoQrScan)
        swSafeMode = findViewById(R.id.swSafeMode)

        chipGroupSearchEngine = findViewById(R.id.chipGroupSearchEngine)
        chipGroupShape = findViewById(R.id.chipGroupShape)
        chipGroupActionMode = findViewById(R.id.chipGroupActionMode)
        chipGroupAppLanguage = findViewById(R.id.chipGroupAppLanguage)
        btnManageExcludedApps = findViewById(R.id.btnManageExcludedApps)
        btnLegacyCapture = findViewById(R.id.btnLegacyCapture)
    }

    private fun setupListeners() {
        findViewById<MaterialButton>(R.id.btnTestOverlay).setOnClickListener {
            val svc = BubbleAccessibilityService.instance
            if (svc == null) {
                Toast.makeText(
                    this,
                    getString(R.string.toast_enable_a11y_or_assistant),
                    Toast.LENGTH_LONG
                ).show()
                startActivity(CompatibilityHelper.createAccessibilitySettingsIntent())
            } else {
                moveTaskToBack(true)
                svc.triggerOverlayWithDelay(380L)
            }
        }

        findViewById<MaterialButton>(R.id.btnSetDefaultAssistant).setOnClickListener {
            runCatching {
                startActivity(CompatibilityHelper.createVoiceInputSettingsIntent())
            }.onFailure {
                Toast.makeText(
                    this,
                    getString(R.string.toast_open_assistant_manual),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        findViewById<MaterialButton>(R.id.btnOpenAccessibility).setOnClickListener {
            startActivity(CompatibilityHelper.createAccessibilitySettingsIntent())
        }

        findViewById<MaterialButton>(R.id.btnGrantOverlay).setOnClickListener {
            startActivity(CompatibilityHelper.createOverlayPermissionIntent(this))
        }

        btnPickSourceLang.setOnClickListener {
            val options = SupportedLanguages.SOURCE_LANGUAGES
            val labels = options.map { it.displayName }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_pick_source_lang))
                .setItems(labels) { _, idx ->
                    val chosen = options[idx].code
                    preferences.sourceLanguageCode = chosen
                    preferences.autoDetectLanguage = (chosen == SupportedLanguages.AUTO_CODE)
                    refreshUi()
                }
                .show()
        }

        btnPickTargetLang.setOnClickListener {
            val options = SupportedLanguages.TARGET_LANGUAGES
            val labels = options.map { it.displayName }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_pick_target_lang))
                .setItems(labels) { _, idx ->
                    preferences.targetLanguageCode = options[idx].code
                    refreshUi()
                }
                .show()
        }

        findViewById<MaterialButton>(R.id.btnSwapLangSettings).setOnClickListener {
            val effectiveSource = if (preferences.autoDetectLanguage) {
                SupportedLanguages.AUTO_CODE
            } else {
                preferences.sourceLanguageCode
            }
            val (newSource, newTarget) = SupportedLanguages.swap(
                currentSourceCode = effectiveSource,
                currentTargetCode = preferences.targetLanguageCode
            )
            preferences.sourceLanguageCode = newSource
            preferences.targetLanguageCode = newTarget
            preferences.autoDetectLanguage = (newSource == SupportedLanguages.AUTO_CODE)
            Toast.makeText(
                this,
                getString(
                    R.string.toast_swapped_fmt,
                    SupportedLanguages.shortLabelFor(newSource),
                    SupportedLanguages.shortLabelFor(newTarget)
                ),
                Toast.LENGTH_SHORT
            ).show()
            refreshUi()
        }

        swAutoDetectLang.setOnCheckedChangeListener { _, isChecked ->
            if (isBindingUi) return@setOnCheckedChangeListener
            preferences.autoDetectLanguage = isChecked
            if (isChecked) {
                preferences.sourceLanguageCode = SupportedLanguages.AUTO_CODE
            } else if (preferences.sourceLanguageCode == SupportedLanguages.AUTO_CODE) {
                preferences.sourceLanguageCode = if (preferences.targetLanguageCode == "id") "en" else "id"
            }
            refreshUi()
        }

        swAutoTranslate.setOnCheckedChangeListener { _, isChecked ->
            if (!isBindingUi) preferences.autoTranslateSelection = isChecked
        }

        swTranslateEntireScreen.setOnCheckedChangeListener { _, isChecked ->
            if (!isBindingUi) preferences.translateEntireScreen = isChecked
        }

        findViewById<MaterialButton>(R.id.btnManageOfflineLangs).setOnClickListener {
            startActivity(
                Intent(this, LanguageManagerActivity::class.java)
                    .putExtra(LanguageManagerActivity.EXTRA_INITIAL_TAB, 0)
            )
        }

        findViewById<MaterialButton>(R.id.btnManageCustomOcr).setOnClickListener {
            startActivity(
                Intent(this, LanguageManagerActivity::class.java)
                    .putExtra(LanguageManagerActivity.EXTRA_INITIAL_TAB, 1)
            )
        }

        chipGroupSearchEngine.setOnCheckedStateChangeListener { _, checkedIds ->
            if (isBindingUi || checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            preferences.visualSearchEngine = when (checkedIds.first()) {
                R.id.chipEngineYandex -> VisualSearchEngine.YANDEX
                R.id.chipEngineTinEye -> VisualSearchEngine.TINEYE
                R.id.chipEngineSharesheet -> VisualSearchEngine.SHARESHEET
                else -> VisualSearchEngine.GOOGLE_LENS
            }
        }

        swAutoVisualSearch.setOnCheckedChangeListener { _, isChecked ->
            if (!isBindingUi) preferences.autoSearchAfterSelection = isChecked
        }

        swAutoQrScan.setOnCheckedChangeListener { _, isChecked ->
            if (!isBindingUi) preferences.autoQrScanEnabled = isChecked
        }

        chipGroupShape.setOnCheckedStateChangeListener { _, checkedIds ->
            if (isBindingUi || checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            preferences.defaultSelectionShape = when (checkedIds.first()) {
                R.id.chipShapeEllipse -> SelectionShape.ELLIPSE
                R.id.chipShapeFreeform -> SelectionShape.FREEFORM
                else -> SelectionShape.RECTANGLE
            }
        }

        chipGroupActionMode.setOnCheckedStateChangeListener { _, checkedIds ->
            if (isBindingUi || checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            preferences.lastActionMode = when (checkedIds.first()) {
                R.id.chipModeVisualSearch -> ActionMode.VISUAL_SEARCH
                else -> ActionMode.TRANSLATE
            }
        }

        chipGroupAppLanguage.setOnCheckedStateChangeListener { _, checkedIds ->
            if (isBindingUi || checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val newLang = when (checkedIds.first()) {
                R.id.chipAppLangEnglish -> LocaleHelper.LANG_ENGLISH
                R.id.chipAppLangIndonesian -> LocaleHelper.LANG_INDONESIAN
                else -> LocaleHelper.LANG_SYSTEM
            }
            if (newLang != preferences.appLanguageCode) {
                preferences.appLanguageCode = newLang
                LocaleHelper.applyAppLocale(newLang)
            }
        }

        swSafeMode.setOnCheckedChangeListener { _, isChecked ->
            if (!isBindingUi) preferences.safeModeEnabled = isChecked
        }

        btnManageExcludedApps.setOnClickListener {
            showExcludedAppsDialog()
        }

        findViewById<MaterialButton>(R.id.btnClearCache).setOnClickListener {
            PerformanceManager.clearVolatileCaches(this)
            Toast.makeText(this, getString(R.string.toast_cache_cleared), Toast.LENGTH_SHORT).show()
        }

        btnLegacyCapture.visibility =
            if (CompatibilityHelper.supportsAccessibilityScreenshot()) View.GONE else View.VISIBLE
        btnLegacyCapture.setOnClickListener {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }
    }

    private fun refreshUi() {
        isBindingUi = true
        try {
            val effectiveSource = if (preferences.autoDetectLanguage) {
                SupportedLanguages.AUTO_CODE
            } else {
                preferences.sourceLanguageCode
            }
            btnPickSourceLang.text = getString(
                R.string.btn_source_lang_fmt,
                SupportedLanguages.shortLabelFor(effectiveSource)
            )
            btnPickTargetLang.text = getString(
                R.string.btn_target_lang_fmt,
                SupportedLanguages.shortLabelFor(preferences.targetLanguageCode)
            )
            tvActiveLangPairDesc.text = getString(
                R.string.active_lang_pair_fmt,
                SupportedLanguages.displayNameFor(effectiveSource),
                SupportedLanguages.displayNameFor(preferences.targetLanguageCode)
            )

            swAutoDetectLang.isChecked = preferences.autoDetectLanguage
            swAutoTranslate.isChecked = preferences.autoTranslateSelection
            swTranslateEntireScreen.isChecked = preferences.translateEntireScreen
            swAutoVisualSearch.isChecked = preferences.autoSearchAfterSelection
            swAutoQrScan.isChecked = preferences.autoQrScanEnabled
            swSafeMode.isChecked = preferences.safeModeEnabled

            val engineChipId = when (preferences.visualSearchEngine) {
                VisualSearchEngine.GOOGLE_LENS -> R.id.chipEngineGoogleLens
                VisualSearchEngine.YANDEX -> R.id.chipEngineYandex
                VisualSearchEngine.TINEYE -> R.id.chipEngineTinEye
                VisualSearchEngine.SHARESHEET -> R.id.chipEngineSharesheet
            }
            chipGroupSearchEngine.check(engineChipId)

            val shapeChipId = when (preferences.defaultSelectionShape) {
                SelectionShape.RECTANGLE -> R.id.chipShapeRect
                SelectionShape.ELLIPSE -> R.id.chipShapeEllipse
                SelectionShape.FREEFORM -> R.id.chipShapeFreeform
            }
            chipGroupShape.check(shapeChipId)

            val modeChipId = when (preferences.lastActionMode) {
                ActionMode.TRANSLATE -> R.id.chipModeTranslate
                ActionMode.VISUAL_SEARCH -> R.id.chipModeVisualSearch
            }
            chipGroupActionMode.check(modeChipId)

            val appLangChipId = when (preferences.appLanguageCode) {
                LocaleHelper.LANG_ENGLISH -> R.id.chipAppLangEnglish
                LocaleHelper.LANG_INDONESIAN -> R.id.chipAppLangIndonesian
                else -> R.id.chipAppLangSystem
            }
            chipGroupAppLanguage.check(appLangChipId)

            btnManageExcludedApps.text = getString(
                R.string.btn_excluded_apps_fmt,
                preferences.excludedPackages.size
            )

            val tessModels = TesseractOcrManager.listInstalledModels(this)
            tvOcrEngineStatus.text = if (preferences.useTesseractOcr && preferences.activeTesseractLang.isNotBlank()) {
                getString(R.string.ocr_engine_tess_fmt, preferences.activeTesseractLang, tessModels.size)
            } else {
                getString(R.string.ocr_engine_mlkit_fmt, tessModels.size)
            }
        } finally {
            isBindingUi = false
        }

        val a11yActive = BubbleAccessibilityService.isRunning()
        val isAssistant = CompatibilityHelper.isDefaultDigitalAssistant(this)
        val silentCaptureSupported = CompatibilityHelper.supportsAccessibilityScreenshot()
        val captureModeText = when {
            isAssistant && a11yActive -> getString(R.string.status_capture_both)
            isAssistant -> getString(R.string.status_capture_assistant)
            silentCaptureSupported && a11yActive -> getString(R.string.status_capture_a11y)
            else -> getString(R.string.status_capture_enable_hint)
        }
        val lensStatus = when (lensManager.checkAvailability()) {
            LensIntegrationManager.LensAvailability.GOOGLE_LENS_APP -> getString(R.string.status_lens_ready)
            LensIntegrationManager.LensAvailability.GOOGLE_APP_SEARCH -> getString(R.string.status_google_app_ready)
            LensIntegrationManager.LensAvailability.SHARESHEET_FALLBACK -> getString(R.string.status_multi_engine_ready)
        }

        lifecycleScope.launch {
            val tm = TranslationManager(LanguageDetectionManager())
            val models = tm.getDownloadedModels()
            tm.release()
            val modelSummary = if (models.isEmpty()) {
                getString(R.string.status_models_auto)
            } else {
                getString(R.string.status_models_count, models.size, models.joinToString(", "))
            }

            tvPermissionSummary.text = buildString {
                append("${getString(R.string.status_assistant_label)}: ${if (isAssistant) getString(R.string.status_active_check) else getString(R.string.status_not_set)}\n")
                append("${getString(R.string.status_accessibility_label)}: ${if (a11yActive) getString(R.string.status_active_check) else getString(R.string.status_inactive)}\n")
                append("${getString(R.string.status_capture_label)}: $captureModeText\n")
                append("${getString(R.string.status_visual_search_label)}: ${preferences.visualSearchEngine.shortLabel} ($lensStatus)\n")
                append("${getString(R.string.status_offline_langs_label)}: $modelSummary")
            }
        }

        val lastBlocked = preferences.lastBlockedPackageName
        if (!lastBlocked.isNullOrBlank()) {
            cardSafeModeNotice.visibility = View.VISIBLE
            tvSafeModeExplanation.text =
                "${getString(R.string.safe_mode_blocked_message)}\n${getString(R.string.status_last_blocked_app, lastBlocked)}"
        } else {
            cardSafeModeNotice.visibility = View.GONE
        }
    }

    private fun showExcludedAppsDialog() {
        val current = preferences.excludedPackages.sorted()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_excluded_title))
            .setItems(current.toTypedArray()) { _, which ->
                val pkg = current[which]
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_remove_excluded_title))
                    .setMessage(getString(R.string.dialog_remove_excluded_msg, pkg))
                    .setPositiveButton(getString(R.string.btn_remove)) { _, _ ->
                        preferences.removeExcludedPackage(pkg)
                        Toast.makeText(this, getString(R.string.toast_pkg_removed, pkg), Toast.LENGTH_SHORT).show()
                        refreshUi()
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show()
            }
            .setPositiveButton(getString(R.string.btn_add_package)) { _, _ ->
                showAddExcludedPackageDialog()
            }
            .setNegativeButton(getString(R.string.btn_close), null)
            .show()
    }

    private fun showAddExcludedPackageDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.hint_package_name)
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_excluded_title))
            .setView(input)
            .setPositiveButton(getString(R.string.btn_add)) { _, _ ->
                val pkg = input.text.toString().trim()
                if (pkg.isNotEmpty()) {
                    preferences.addExcludedPackage(pkg)
                    Toast.makeText(this, getString(R.string.toast_pkg_added, pkg), Toast.LENGTH_SHORT).show()
                    refreshUi()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }
}
