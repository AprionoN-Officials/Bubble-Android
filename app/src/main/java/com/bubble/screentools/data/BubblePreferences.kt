package com.bubble.screentools.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import com.bubble.screentools.model.ActionMode
import com.bubble.screentools.model.SelectionShape
import com.bubble.screentools.model.TranslationOverlayStyle
import com.bubble.screentools.model.VisualSearchEngine

/**
 * Streamlined, privacy-first preferences for Bubble.
 * Removes unused/redundant micro-toggles while adding preferences for:
 * - Multi Visual Search Engine (Default: Google Lens)
 * - Auto QR Code Detection (Default: ON)
 * - Custom Tesseract OCR (.traineddata) models
 */
class BubblePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Translation settings ---
    var sourceLanguageCode: String
        get() = prefs.getString(KEY_SOURCE_LANG, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_SOURCE_LANG, value).apply()

    var targetLanguageCode: String
        get() = prefs.getString(KEY_TARGET_LANG, "id") ?: "id"
        set(value) = prefs.edit().putString(KEY_TARGET_LANG, value).apply()

    var autoDetectLanguage: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DETECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DETECT, value).apply()

    var autoTranslateSelection: Boolean
        get() = prefs.getBoolean(KEY_AUTO_TRANSLATE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_TRANSLATE, value).apply()

    var translateEntireScreen: Boolean
        get() = prefs.getBoolean(KEY_TRANSLATE_ENTIRE_SCREEN, false)
        set(value) = prefs.edit().putBoolean(KEY_TRANSLATE_ENTIRE_SCREEN, value).apply()

    var translationOverlayStyle: TranslationOverlayStyle
        get() = runCatching {
            TranslationOverlayStyle.valueOf(
                prefs.getString(KEY_OVERLAY_STYLE, TranslationOverlayStyle.ADAPTIVE_COVER.name)!!
            )
        }.getOrDefault(TranslationOverlayStyle.ADAPTIVE_COVER)
        set(value) = prefs.edit().putString(KEY_OVERLAY_STYLE, value.name).apply()

    // Custom Tesseract OCR (.traineddata)
    var useTesseractOcr: Boolean
        get() = prefs.getBoolean(KEY_USE_TESSERACT_OCR, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_TESSERACT_OCR, value).apply()

    var activeTesseractLang: String
        get() = prefs.getString(KEY_ACTIVE_TESSERACT_LANG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ACTIVE_TESSERACT_LANG, value.trim()).apply()

    // --- Visual Search (Lens & Search Engines) ---
    var visualSearchEngine: VisualSearchEngine
        get() = runCatching {
            VisualSearchEngine.valueOf(
                prefs.getString(KEY_VISUAL_SEARCH_ENGINE, VisualSearchEngine.GOOGLE_LENS.name)!!
            )
        }.getOrDefault(VisualSearchEngine.GOOGLE_LENS)
        set(value) = prefs.edit().putString(KEY_VISUAL_SEARCH_ENGINE, value.name).apply()

    var autoSearchAfterSelection: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SEARCH, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SEARCH, value).apply()

    // Always clean temporary lens images automatically (privacy-first)
    val autoCleanupTempImages: Boolean = true

    // --- QR Code & Overlay settings ---
    var autoQrScanEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_QR_SCAN, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_QR_SCAN, value).apply()

    var appLanguageCode: String
        get() = prefs.getString(KEY_APP_LANGUAGE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_APP_LANGUAGE, value).apply()

    var defaultSelectionShape: SelectionShape
        get() = runCatching {
            SelectionShape.valueOf(
                prefs.getString(KEY_DEFAULT_SHAPE, SelectionShape.RECTANGLE.name)!!
            )
        }.getOrDefault(SelectionShape.RECTANGLE)
        set(value) = prefs.edit().putString(KEY_DEFAULT_SHAPE, value.name).apply()

    var lastActionMode: ActionMode
        get() = runCatching {
            ActionMode.valueOf(prefs.getString(KEY_LAST_ACTION_MODE, ActionMode.TRANSLATE.name)!!)
        }.getOrDefault(ActionMode.TRANSLATE)
        set(value) = prefs.edit().putString(KEY_LAST_ACTION_MODE, value.name).apply()

    val borderColor: Int
        get() = prefs.getInt(KEY_BORDER_COLOR, Color.parseColor("#2196F3"))

    val borderThicknessDp: Float
        get() = prefs.getFloat(KEY_BORDER_THICKNESS_DP, 3.5f)

    val overlayDimOpacity: Float
        get() = prefs.getFloat(KEY_OVERLAY_OPACITY, 0.38f)

    val animationsEnabled: Boolean = true
    val hapticFeedbackEnabled: Boolean = true

    // Smart automatic performance & caching defaults
    val lowRamMode: Boolean = false
    val batterySavingMode: Boolean = true
    val translationCacheEnabled: Boolean = true

    // --- Privacy & Safe Mode (consolidated into single main toggle + excluded list) ---
    var safeModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SAFE_MODE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SAFE_MODE_ENABLED, value).apply()

    val stopScanOnExcludedApp: Boolean
        get() = safeModeEnabled

    val restrictCaptureToActiveOverlay: Boolean
        get() = safeModeEnabled

    var lastBlockedPackageName: String?
        get() = prefs.getString(KEY_LAST_BLOCKED_PACKAGE, null)
        set(value) = prefs.edit().putString(KEY_LAST_BLOCKED_PACKAGE, value).apply()

    var excludedPackages: Set<String>
        get() = prefs.getStringSet(KEY_EXCLUDED_PACKAGES, DEFAULT_EXCLUDED_PACKAGES)?.toSet()
            ?: DEFAULT_EXCLUDED_PACKAGES
        set(value) = prefs.edit().putStringSet(KEY_EXCLUDED_PACKAGES, value.toSet()).apply()

    fun addExcludedPackage(packageName: String) {
        val updated = excludedPackages.toMutableSet()
        updated.add(packageName.trim())
        excludedPackages = updated
    }

    fun removeExcludedPackage(packageName: String) {
        val updated = excludedPackages.toMutableSet()
        updated.remove(packageName.trim())
        excludedPackages = updated
    }

    companion object {
        private const val PREFS_NAME = "bubble_preferences"
        private const val KEY_SOURCE_LANG = "source_lang"
        private const val KEY_TARGET_LANG = "target_lang"
        private const val KEY_AUTO_DETECT = "auto_detect"
        private const val KEY_AUTO_TRANSLATE = "auto_translate"
        private const val KEY_TRANSLATE_ENTIRE_SCREEN = "translate_entire_screen"
        private const val KEY_OVERLAY_STYLE = "overlay_style"
        private const val KEY_USE_TESSERACT_OCR = "use_tesseract_ocr"
        private const val KEY_ACTIVE_TESSERACT_LANG = "active_tesseract_lang"
        private const val KEY_VISUAL_SEARCH_ENGINE = "visual_search_engine"
        private const val KEY_AUTO_SEARCH = "auto_search"
        private const val KEY_AUTO_QR_SCAN = "auto_qr_scan"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_DEFAULT_SHAPE = "default_shape"
        private const val KEY_BORDER_COLOR = "border_color"
        private const val KEY_BORDER_THICKNESS_DP = "border_thickness_dp"
        private const val KEY_OVERLAY_OPACITY = "overlay_opacity"
        private const val KEY_LAST_ACTION_MODE = "last_action_mode"
        private const val KEY_SAFE_MODE_ENABLED = "safe_mode_enabled"
        private const val KEY_LAST_BLOCKED_PACKAGE = "last_blocked_package"
        private const val KEY_EXCLUDED_PACKAGES = "excluded_packages"

        val DEFAULT_EXCLUDED_PACKAGES = setOf(
            // Mobile banking & digital wallets (user-editable package list)
            "com.bca",
            "com.bca.mybca.omni.android",
            "id.co.bri.brimo",
            "id.bmri.livin",
            "src.com.bni",
            "com.gojek.gopay",
            "ovo.id",
            "id.dana",
            // Password managers & authenticators
            "com.x8bit.bitwarden",
            "com.onepassword.android",
            "com.google.android.apps.authenticator2",
            "com.azure.authenticator",
            "com.authy.authy",
            // Government identity apps
            "gov.dukcapil.mobile_id"
        )
    }
}
