package com.bubble.screentools.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import com.bubble.screentools.model.ActionMode
import com.bubble.screentools.model.ActionPanelButtonSize
import com.bubble.screentools.model.ActionPanelButtonType
import com.bubble.screentools.model.ActionPanelConfig
import com.bubble.screentools.model.ActionPanelLayoutMode
import com.bubble.screentools.model.ActionPanelPosition
import com.bubble.screentools.model.SearchImageFormat
import com.bubble.screentools.model.SearchImageQuality
import com.bubble.screentools.model.SelectionBorderStyle
import com.bubble.screentools.model.SelectionColorMode
import com.bubble.screentools.model.SelectionConfig
import com.bubble.screentools.model.SelectionShape
import com.bubble.screentools.model.ThemeConfig
import com.bubble.screentools.model.ThemeMode
import com.bubble.screentools.model.ThemePreset
import com.bubble.screentools.model.TranslationContextProfile
import com.bubble.screentools.model.TranslationOverlayStyle
import com.bubble.screentools.model.TranslationQualityMode
import com.bubble.screentools.model.UpdateCheckInterval
import com.bubble.screentools.model.VisualSearchEngine

/**
 * Consolidated, backward-compatible persistent settings repository for Bubble.
 * Preserves all v1.0.0 keys while storing structured personalization settings for
 * Theme, Selection, Action Panel, Search Engines, Translation Quality, Auto-Link Detection,
 * GitHub Updates, and Donation Reminder cooldown timestamps.
 */
class BubblePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Translation settings ---
    var sourceLanguageCode: String
        get() = prefs.getString(KEY_SOURCE_LANG, "auto") ?: "auto"
        set(value) {
            prefs.edit().putString(KEY_SOURCE_LANG, value).apply()
            if (value != "auto") recordRecentLanguage(value)
        }

    var targetLanguageCode: String
        get() = prefs.getString(KEY_TARGET_LANG, "id") ?: "id"
        set(value) {
            prefs.edit().putString(KEY_TARGET_LANG, value).apply()
            recordRecentLanguage(value)
        }

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

    var translationQualityMode: TranslationQualityMode
        get() = runCatching {
            TranslationQualityMode.valueOf(
                prefs.getString(KEY_TRANSLATION_QUALITY, TranslationQualityMode.NATURAL.name)!!
            )
        }.getOrDefault(TranslationQualityMode.NATURAL)
        set(value) = prefs.edit().putString(KEY_TRANSLATION_QUALITY, value.name).apply()

    var translationContextProfile: TranslationContextProfile
        get() = runCatching {
            TranslationContextProfile.valueOf(
                prefs.getString(KEY_TRANSLATION_CONTEXT, TranslationContextProfile.NATURAL.name)!!
            )
        }.getOrDefault(TranslationContextProfile.NATURAL)
        set(value) = prefs.edit().putString(KEY_TRANSLATION_CONTEXT, value.name).apply()

    var preferredSourceLanguages: List<String>
        get() = prefs.getString(KEY_PREFERRED_SOURCE_LANGS, "en,ja,ko,zh")
            ?.split(",")
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() && it != "auto" }
            ?.distinct()
            ?: listOf("en", "ja", "ko", "zh")
        set(value) = prefs.edit()
            .putString(KEY_PREFERRED_SOURCE_LANGS, value.map { it.trim().lowercase() }.distinct().joinToString(","))
            .apply()

    var fallbackLanguageCode: String
        get() = prefs.getString(KEY_FALLBACK_LANG, "en") ?: "en"
        set(value) = prefs.edit().putString(KEY_FALLBACK_LANG, value.trim().lowercase()).apply()

    var recentLanguageCodes: List<String>
        get() = prefs.getString(KEY_RECENT_LANGS, "en,id,ja")
            ?.split(",")
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() && it != "auto" }
            ?.distinct()
            ?.take(6)
            ?: listOf("en", "id", "ja")
        set(value) = prefs.edit()
            .putString(KEY_RECENT_LANGS, value.distinct().take(6).joinToString(","))
            .apply()

    fun recordRecentLanguage(code: String) {
        val norm = code.trim().lowercase()
        if (norm.isEmpty() || norm == "auto") return
        val updated = (listOf(norm) + recentLanguageCodes.filterNot { it == norm }).take(6)
        recentLanguageCodes = updated
    }

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

    var defaultVisualEngineId: String
        get() = prefs.getString(KEY_DEFAULT_VISUAL_ENGINE_ID, null) ?: mapLegacyEngineToId(visualSearchEngine)
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_VISUAL_ENGINE_ID, value).apply()
            mapIdToLegacyEngine(value)?.let { visualSearchEngine = it }
        }

    var defaultTextEngineId: String
        get() = prefs.getString(KEY_DEFAULT_TEXT_ENGINE_ID, "text_google") ?: "text_google"
        set(value) = prefs.edit().putString(KEY_DEFAULT_TEXT_ENGINE_ID, value).apply()

    var lastUsedVisualEngineId: String
        get() = prefs.getString(KEY_LAST_VISUAL_ENGINE_ID, defaultVisualEngineId) ?: defaultVisualEngineId
        set(value) = prefs.edit().putString(KEY_LAST_VISUAL_ENGINE_ID, value).apply()

    var rememberLastSearchEngine: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_LAST_ENGINE, true)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER_LAST_ENGINE, value).apply()

    var searchEnginesJson: String?
        get() = prefs.getString(KEY_SEARCH_ENGINES_JSON, null)
        set(value) = prefs.edit().putString(KEY_SEARCH_ENGINES_JSON, value).apply()

    var searchImageQuality: SearchImageQuality
        get() = runCatching {
            SearchImageQuality.valueOf(
                prefs.getString(KEY_SEARCH_IMAGE_QUALITY, SearchImageQuality.BALANCED.name)!!
            )
        }.getOrDefault(SearchImageQuality.BALANCED)
        set(value) = prefs.edit().putString(KEY_SEARCH_IMAGE_QUALITY, value.name).apply()

    var searchImageFormat: SearchImageFormat
        get() = runCatching {
            SearchImageFormat.valueOf(
                prefs.getString(KEY_SEARCH_IMAGE_FORMAT, SearchImageFormat.AUTO.name)!!
            )
        }.getOrDefault(SearchImageFormat.AUTO)
        set(value) = prefs.edit().putString(KEY_SEARCH_IMAGE_FORMAT, value.name).apply()

    var searchMaxResolutionPx: Int
        get() = prefs.getInt(KEY_SEARCH_MAX_RES_PX, searchImageQuality.maxDimensionPx)
        set(value) = prefs.edit().putInt(KEY_SEARCH_MAX_RES_PX, value.coerceIn(480, 3000)).apply()

    var searchCompressionQuality: Int
        get() = prefs.getInt(KEY_SEARCH_COMPRESSION_QUALITY, searchImageQuality.jpegQuality)
        set(value) = prefs.edit().putInt(KEY_SEARCH_COMPRESSION_QUALITY, value.coerceIn(50, 100)).apply()

    var confirmBeforeImageUpload: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_BEFORE_UPLOAD, false)
        set(value) = prefs.edit().putBoolean(KEY_CONFIRM_BEFORE_UPLOAD, value).apply()

    var openSearchInExternalBrowser: Boolean
        get() = prefs.getBoolean(KEY_SEARCH_EXTERNAL_BROWSER, false)
        set(value) = prefs.edit().putBoolean(KEY_SEARCH_EXTERNAL_BROWSER, value).apply()

    var autoSearchAfterSelection: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SEARCH, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SEARCH, value).apply()

    var visualSearchDelayMs: Long
        get() = prefs.getLong(KEY_VISUAL_SEARCH_DELAY_MS, DEFAULT_VISUAL_SEARCH_DELAY_MS).coerceIn(0L, MAX_VISUAL_SEARCH_DELAY_MS)
        set(value) = prefs.edit().putLong(KEY_VISUAL_SEARCH_DELAY_MS, value.coerceIn(0L, MAX_VISUAL_SEARCH_DELAY_MS)).apply()

    var webAdBlockerEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEB_ADBLOCKER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WEB_ADBLOCKER_ENABLED, value).apply()

    val autoCleanupTempImages: Boolean = true

    // --- QR Code, Link Detection & Overlay settings ---
    var autoQrScanEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_QR_SCAN, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_QR_SCAN, value).apply()

    var autoLinkDetectEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LINK_DETECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_LINK_DETECT, value).apply()

    var appLanguageCode: String
        get() = prefs.getString(KEY_APP_LANGUAGE, "system") ?: "system"
        set(value) = prefs.edit().putString(KEY_APP_LANGUAGE, value).apply()

    var defaultSelectionShape: SelectionShape
        get() = runCatching {
            val raw = prefs.getString(KEY_DEFAULT_SHAPE, SelectionShape.ROUNDED_RECTANGLE.name)!!
            SelectionShape.valueOf(raw)
        }.getOrDefault(SelectionShape.ROUNDED_RECTANGLE)
        set(value) = prefs.edit().putString(KEY_DEFAULT_SHAPE, value.name).apply()

    var lastActionMode: ActionMode
        get() = runCatching {
            ActionMode.valueOf(prefs.getString(KEY_LAST_ACTION_MODE, ActionMode.TRANSLATE.name)!!)
        }.getOrDefault(ActionMode.TRANSLATE)
        set(value) = prefs.edit().putString(KEY_LAST_ACTION_MODE, value.name).apply()

    // --- Advanced Selection Customization ---
    var selectionConfig: SelectionConfig
        get() {
            val shape = defaultSelectionShape
            val colorMode = runCatching {
                SelectionColorMode.valueOf(
                    prefs.getString(KEY_SEL_COLOR_MODE, SelectionColorMode.MATERIAL_YOU.name)!!
                )
            }.getOrDefault(SelectionColorMode.MATERIAL_YOU)
            val borderStyle = runCatching {
                SelectionBorderStyle.valueOf(
                    prefs.getString(KEY_SEL_BORDER_STYLE, SelectionBorderStyle.SOLID.name)!!
                )
            }.getOrDefault(SelectionBorderStyle.SOLID)
            return SelectionConfig(
                shape = shape,
                colorMode = colorMode,
                borderColor = prefs.getInt(KEY_BORDER_COLOR, Color.parseColor("#2196F3")),
                borderThicknessDp = prefs.getFloat(KEY_BORDER_THICKNESS_DP, 3.5f).coerceIn(1.5f, 8.0f),
                cornerRadiusDp = prefs.getFloat(KEY_SEL_CORNER_RADIUS_DP, 16f).coerceIn(0f, 36f),
                curvatureLevel = prefs.getFloat(KEY_SEL_CURVATURE, 0.55f).coerceIn(0f, 1f),
                fillOpacity = prefs.getFloat(KEY_SEL_FILL_OPACITY, 0f).coerceIn(0f, 0.45f),
                outerDimOpacity = prefs.getFloat(KEY_OVERLAY_OPACITY, 0.38f).coerceIn(0.10f, 0.75f),
                borderStyle = borderStyle,
                showResizeHandles = prefs.getBoolean(KEY_SEL_SHOW_HANDLES, true),
                handleSizeDp = prefs.getFloat(KEY_SEL_HANDLE_SIZE_DP, 7.0f).coerceIn(4f, 13f),
                handleColor = prefs.getInt(KEY_SEL_HANDLE_COLOR, Color.WHITE),
                handleUseAccentStroke = prefs.getBoolean(KEY_SEL_HANDLE_ACCENT_STROKE, true),
                smoothFreeformPath = prefs.getBoolean(KEY_SEL_SMOOTH_FREEFORM, true),
                edgeSmoothing = prefs.getBoolean(KEY_SEL_EDGE_SMOOTHING, true),
                animationEnabled = prefs.getBoolean(KEY_SEL_ANIMATION, true),
                minSelectionSizeDp = prefs.getFloat(KEY_SEL_MIN_SIZE_DP, 42f).coerceIn(24f, 96f)
            )
        }
        set(value) {
            prefs.edit()
                .putString(KEY_DEFAULT_SHAPE, value.shape.name)
                .putString(KEY_SEL_COLOR_MODE, value.colorMode.name)
                .putInt(KEY_BORDER_COLOR, value.borderColor)
                .putFloat(KEY_BORDER_THICKNESS_DP, value.borderThicknessDp.coerceIn(1.5f, 8.0f))
                .putFloat(KEY_SEL_CORNER_RADIUS_DP, value.cornerRadiusDp.coerceIn(0f, 36f))
                .putFloat(KEY_SEL_CURVATURE, value.curvatureLevel.coerceIn(0f, 1f))
                .putFloat(KEY_SEL_FILL_OPACITY, value.fillOpacity.coerceIn(0f, 0.45f))
                .putFloat(KEY_OVERLAY_OPACITY, value.outerDimOpacity.coerceIn(0.10f, 0.75f))
                .putString(KEY_SEL_BORDER_STYLE, value.borderStyle.name)
                .putBoolean(KEY_SEL_SHOW_HANDLES, value.showResizeHandles)
                .putFloat(KEY_SEL_HANDLE_SIZE_DP, value.handleSizeDp.coerceIn(4f, 13f))
                .putInt(KEY_SEL_HANDLE_COLOR, value.handleColor)
                .putBoolean(KEY_SEL_HANDLE_ACCENT_STROKE, value.handleUseAccentStroke)
                .putBoolean(KEY_SEL_SMOOTH_FREEFORM, value.smoothFreeformPath)
                .putBoolean(KEY_SEL_EDGE_SMOOTHING, value.edgeSmoothing)
                .putBoolean(KEY_SEL_ANIMATION, value.animationEnabled)
                .putFloat(KEY_SEL_MIN_SIZE_DP, value.minSelectionSizeDp.coerceIn(24f, 96f))
                .apply()
        }

    fun resetSelectionConfig() {
        selectionConfig = SelectionConfig()
    }

    val borderColor: Int
        get() = selectionConfig.borderColor

    val borderThicknessDp: Float
        get() = selectionConfig.borderThicknessDp

    val overlayDimOpacity: Float
        get() = selectionConfig.outerDimOpacity

    // --- Material 3 Expressive & Theme Settings ---
    var themeConfig: ThemeConfig
        get() {
            val mode = runCatching {
                ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.FOLLOW_SYSTEM.name)!!)
            }.getOrDefault(ThemeMode.FOLLOW_SYSTEM)
            val preset = runCatching {
                ThemePreset.valueOf(prefs.getString(KEY_THEME_PRESET, ThemePreset.BUBBLE_BLUE.name)!!)
            }.getOrDefault(ThemePreset.BUBBLE_BLUE)
            return ThemeConfig(
                themeMode = mode,
                useDynamicColor = prefs.getBoolean(KEY_THEME_DYNAMIC_COLOR, true),
                preset = preset,
                customSeedColor = prefs.getInt(KEY_THEME_CUSTOM_SEED, Color.parseColor("#2196F3")),
                applyToMainUi = prefs.getBoolean(KEY_THEME_SCOPE_MAIN, true),
                applyToOverlay = prefs.getBoolean(KEY_THEME_SCOPE_OVERLAY, true),
                applyToSelection = prefs.getBoolean(KEY_THEME_SCOPE_SELECTION, true)
            )
        }
        set(value) {
            prefs.edit()
                .putString(KEY_THEME_MODE, value.themeMode.name)
                .putBoolean(KEY_THEME_DYNAMIC_COLOR, value.useDynamicColor)
                .putString(KEY_THEME_PRESET, value.preset.name)
                .putInt(KEY_THEME_CUSTOM_SEED, value.customSeedColor)
                .putBoolean(KEY_THEME_SCOPE_MAIN, value.applyToMainUi)
                .putBoolean(KEY_THEME_SCOPE_OVERLAY, value.applyToOverlay)
                .putBoolean(KEY_THEME_SCOPE_SELECTION, value.applyToSelection)
                .apply()
        }

    fun resetThemeConfig() {
        themeConfig = ThemeConfig()
    }

    // --- Custom Action Panel Settings ---
    var actionPanelConfig: ActionPanelConfig
        get() {
            val rawButtons = prefs.getString(KEY_PANEL_BUTTONS, null)
            val parsedButtons = rawButtons
                ?.split(",")
                ?.mapNotNull { ActionPanelButtonType.fromId(it.trim()) }
                ?.distinct()
                ?.takeIf { it.isNotEmpty() }
                ?: ActionPanelButtonType.DEFAULT_ENABLED_LIST

            val layoutMode = runCatching {
                ActionPanelLayoutMode.valueOf(
                    prefs.getString(KEY_PANEL_LAYOUT_MODE, ActionPanelLayoutMode.HORIZONTAL_SCROLL.name)!!
                )
            }.getOrDefault(ActionPanelLayoutMode.HORIZONTAL_SCROLL)

            val position = runCatching {
                ActionPanelPosition.valueOf(
                    prefs.getString(KEY_PANEL_POSITION, ActionPanelPosition.BOTTOM.name)!!
                )
            }.getOrDefault(ActionPanelPosition.BOTTOM)

            val size = runCatching {
                ActionPanelButtonSize.valueOf(
                    prefs.getString(KEY_PANEL_BUTTON_SIZE, ActionPanelButtonSize.STANDARD.name)!!
                )
            }.getOrDefault(ActionPanelButtonSize.STANDARD)

            return ActionPanelConfig(
                enabledButtons = parsedButtons,
                layoutMode = layoutMode,
                showLabels = prefs.getBoolean(KEY_PANEL_SHOW_LABELS, true),
                position = position,
                customPosX = prefs.getFloat(KEY_PANEL_CUSTOM_X, 0.5f),
                customPosY = prefs.getFloat(KEY_PANEL_CUSTOM_Y, 0.88f),
                buttonSize = size,
                buttonSpacingDp = prefs.getFloat(KEY_PANEL_BTN_SPACING_DP, 8f),
                buttonPaddingHorizontalDp = prefs.getFloat(KEY_PANEL_BTN_PAD_DP, 12f),
                buttonCornerRadiusDp = prefs.getFloat(KEY_PANEL_BTN_RADIUS_DP, 24f),
                panelWidthPercent = prefs.getInt(KEY_PANEL_WIDTH_PCT, 92),
                overallScale = prefs.getFloat(KEY_PANEL_SCALE, 1.0f),
                panelOpacity = prefs.getFloat(KEY_PANEL_OPACITY, 0.96f),
                horizontalMarginDp = prefs.getFloat(KEY_PANEL_MARGIN_H_DP, 16f),
                verticalMarginDp = prefs.getFloat(KEY_PANEL_MARGIN_V_DP, 14f),
                showSearchEngineSelector = prefs.getBoolean(KEY_PANEL_SHOW_ENGINE_SELECTOR, true)
            ).sanitized()
        }
        set(value) {
            val safe = value.sanitized()
            prefs.edit()
                .putString(KEY_PANEL_BUTTONS, safe.enabledButtons.joinToString(",") { it.id })
                .putString(KEY_PANEL_LAYOUT_MODE, safe.layoutMode.name)
                .putBoolean(KEY_PANEL_SHOW_LABELS, safe.showLabels)
                .putString(KEY_PANEL_POSITION, safe.position.name)
                .putFloat(KEY_PANEL_CUSTOM_X, safe.customPosX)
                .putFloat(KEY_PANEL_CUSTOM_Y, safe.customPosY)
                .putString(KEY_PANEL_BUTTON_SIZE, safe.buttonSize.name)
                .putFloat(KEY_PANEL_BTN_SPACING_DP, safe.buttonSpacingDp)
                .putFloat(KEY_PANEL_BTN_PAD_DP, safe.buttonPaddingHorizontalDp)
                .putFloat(KEY_PANEL_BTN_RADIUS_DP, safe.buttonCornerRadiusDp)
                .putInt(KEY_PANEL_WIDTH_PCT, safe.panelWidthPercent)
                .putFloat(KEY_PANEL_SCALE, safe.overallScale)
                .putFloat(KEY_PANEL_OPACITY, safe.panelOpacity)
                .putFloat(KEY_PANEL_MARGIN_H_DP, safe.horizontalMarginDp)
                .putFloat(KEY_PANEL_MARGIN_V_DP, safe.verticalMarginDp)
                .putBoolean(KEY_PANEL_SHOW_ENGINE_SELECTOR, safe.showSearchEngineSelector)
                .apply()
        }

    fun resetActionPanelConfig() {
        actionPanelConfig = ActionPanelConfig()
    }

    // --- GitHub Auto-Update Settings ---
    var autoUpdateCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_UPDATE_AUTO_CHECK, true)
        set(value) = prefs.edit().putBoolean(KEY_UPDATE_AUTO_CHECK, value).apply()

    var checkUpdateOnLaunch: Boolean
        get() = prefs.getBoolean(KEY_UPDATE_CHECK_ON_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_UPDATE_CHECK_ON_LAUNCH, value).apply()

    var updateCheckInterval: UpdateCheckInterval
        get() = runCatching {
            UpdateCheckInterval.valueOf(
                prefs.getString(KEY_UPDATE_INTERVAL, UpdateCheckInterval.HOURS_24.name)!!
            )
        }.getOrDefault(UpdateCheckInterval.HOURS_24)
        set(value) = prefs.edit().putString(KEY_UPDATE_INTERVAL, value.name).apply()

    var updateWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_UPDATE_WIFI_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_UPDATE_WIFI_ONLY, value).apply()

    var includePrereleaseUpdates: Boolean
        get() = prefs.getBoolean(KEY_UPDATE_INCLUDE_PRERELEASE, false)
        set(value) = prefs.edit().putBoolean(KEY_UPDATE_INCLUDE_PRERELEASE, value).apply()

    var lastUpdateCheckTimestampMs: Long
        get() = prefs.getLong(KEY_UPDATE_LAST_CHECKED_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_UPDATE_LAST_CHECKED_MS, value).apply()

    var lastKnownLatestVersion: String
        get() = prefs.getString(KEY_UPDATE_LATEST_VERSION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_UPDATE_LATEST_VERSION, value).apply()

    var lastKnownReleaseNotes: String
        get() = prefs.getString(KEY_UPDATE_RELEASE_NOTES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_UPDATE_RELEASE_NOTES, value).apply()

    var lastKnownReleaseUrl: String
        get() = prefs.getString(KEY_UPDATE_RELEASE_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_UPDATE_RELEASE_URL, value).apply()

    var lastKnownReleaseDate: String
        get() = prefs.getString(KEY_UPDATE_RELEASE_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_UPDATE_RELEASE_DATE, value).apply()

    var lastUpdateEtag: String
        get() = prefs.getString(KEY_UPDATE_ETAG, "") ?: ""
        set(value) = prefs.edit().putString(KEY_UPDATE_ETAG, value).apply()

    var lastRateLimitResetMs: Long
        get() = prefs.getLong(KEY_UPDATE_RATE_LIMIT_RESET_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_UPDATE_RATE_LIMIT_RESET_MS, value).apply()

    // --- Donation Reminder Persistence ---
    var donationFirstUseTimestampMs: Long
        get() = prefs.getLong(KEY_DONATION_FIRST_USE_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_DONATION_FIRST_USE_MS, value).apply()

    var donationLastAcceptedTimestampMs: Long
        get() = prefs.getLong(KEY_DONATION_LAST_ACCEPTED_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_DONATION_LAST_ACCEPTED_MS, value).apply()

    // --- Runtime / UX flags ---
    val animationsEnabled: Boolean
        get() = selectionConfig.animationEnabled
    val hapticFeedbackEnabled: Boolean = true
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
        private const val KEY_TRANSLATION_QUALITY = "translation_quality_mode"
        private const val KEY_TRANSLATION_CONTEXT = "translation_context_profile"
        private const val KEY_PREFERRED_SOURCE_LANGS = "preferred_source_langs"
        private const val KEY_FALLBACK_LANG = "fallback_lang"
        private const val KEY_RECENT_LANGS = "recent_langs"
        private const val KEY_USE_TESSERACT_OCR = "use_tesseract_ocr"
        private const val KEY_ACTIVE_TESSERACT_LANG = "active_tesseract_lang"
        private const val KEY_VISUAL_SEARCH_ENGINE = "visual_search_engine"
        private const val KEY_DEFAULT_VISUAL_ENGINE_ID = "default_visual_engine_id"
        private const val KEY_DEFAULT_TEXT_ENGINE_ID = "default_text_engine_id"
        private const val KEY_LAST_VISUAL_ENGINE_ID = "last_visual_engine_id"
        private const val KEY_REMEMBER_LAST_ENGINE = "remember_last_engine"
        private const val KEY_SEARCH_ENGINES_JSON = "search_engines_json"
        private const val KEY_SEARCH_IMAGE_QUALITY = "search_image_quality"
        private const val KEY_SEARCH_IMAGE_FORMAT = "search_image_format"
        private const val KEY_SEARCH_MAX_RES_PX = "search_max_res_px"
        private const val KEY_SEARCH_COMPRESSION_QUALITY = "search_compression_quality"
        private const val KEY_CONFIRM_BEFORE_UPLOAD = "confirm_before_upload"
        private const val KEY_SEARCH_EXTERNAL_BROWSER = "search_external_browser"
        private const val KEY_AUTO_SEARCH = "auto_search"
        private const val KEY_VISUAL_SEARCH_DELAY_MS = "visual_search_delay_ms"
        private const val KEY_WEB_ADBLOCKER_ENABLED = "web_adblocker_enabled"
        const val DEFAULT_VISUAL_SEARCH_DELAY_MS = 2000L
        const val MAX_VISUAL_SEARCH_DELAY_MS = 60_000L
        private const val KEY_AUTO_QR_SCAN = "auto_qr_scan"
        private const val KEY_AUTO_LINK_DETECT = "auto_link_detect"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_DEFAULT_SHAPE = "default_shape"
        private const val KEY_BORDER_COLOR = "border_color"
        private const val KEY_BORDER_THICKNESS_DP = "border_thickness_dp"
        private const val KEY_OVERLAY_OPACITY = "overlay_opacity"
        private const val KEY_SEL_COLOR_MODE = "sel_color_mode"
        private const val KEY_SEL_CORNER_RADIUS_DP = "sel_corner_radius_dp"
        private const val KEY_SEL_CURVATURE = "sel_curvature"
        private const val KEY_SEL_FILL_OPACITY = "sel_fill_opacity"
        private const val KEY_SEL_BORDER_STYLE = "sel_border_style"
        private const val KEY_SEL_SHOW_HANDLES = "sel_show_handles"
        private const val KEY_SEL_HANDLE_SIZE_DP = "sel_handle_size_dp"
        private const val KEY_SEL_HANDLE_COLOR = "sel_handle_color"
        private const val KEY_SEL_HANDLE_ACCENT_STROKE = "sel_handle_accent_stroke"
        private const val KEY_SEL_SMOOTH_FREEFORM = "sel_smooth_freeform"
        private const val KEY_SEL_EDGE_SMOOTHING = "sel_edge_smoothing"
        private const val KEY_SEL_ANIMATION = "sel_animation"
        private const val KEY_SEL_MIN_SIZE_DP = "sel_min_size_dp"
        private const val KEY_LAST_ACTION_MODE = "last_action_mode"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_THEME_DYNAMIC_COLOR = "theme_dynamic_color"
        private const val KEY_THEME_PRESET = "theme_preset"
        private const val KEY_THEME_CUSTOM_SEED = "theme_custom_seed"
        private const val KEY_THEME_SCOPE_MAIN = "theme_scope_main"
        private const val KEY_THEME_SCOPE_OVERLAY = "theme_scope_overlay"
        private const val KEY_THEME_SCOPE_SELECTION = "theme_scope_selection"
        private const val KEY_PANEL_BUTTONS = "panel_buttons"
        private const val KEY_PANEL_LAYOUT_MODE = "panel_layout_mode"
        private const val KEY_PANEL_SHOW_LABELS = "panel_show_labels"
        private const val KEY_PANEL_POSITION = "panel_position"
        private const val KEY_PANEL_CUSTOM_X = "panel_custom_x"
        private const val KEY_PANEL_CUSTOM_Y = "panel_custom_y"
        private const val KEY_PANEL_BUTTON_SIZE = "panel_button_size"
        private const val KEY_PANEL_BTN_SPACING_DP = "panel_btn_spacing_dp"
        private const val KEY_PANEL_BTN_PAD_DP = "panel_btn_pad_dp"
        private const val KEY_PANEL_BTN_RADIUS_DP = "panel_btn_radius_dp"
        private const val KEY_PANEL_WIDTH_PCT = "panel_width_pct"
        private const val KEY_PANEL_SCALE = "panel_scale"
        private const val KEY_PANEL_OPACITY = "panel_opacity"
        private const val KEY_PANEL_MARGIN_H_DP = "panel_margin_h_dp"
        private const val KEY_PANEL_MARGIN_V_DP = "panel_margin_v_dp"
        private const val KEY_PANEL_SHOW_ENGINE_SELECTOR = "panel_show_engine_selector"
        private const val KEY_UPDATE_AUTO_CHECK = "update_auto_check"
        private const val KEY_UPDATE_CHECK_ON_LAUNCH = "update_check_on_launch"
        private const val KEY_UPDATE_INTERVAL = "update_interval"
        private const val KEY_UPDATE_WIFI_ONLY = "update_wifi_only"
        private const val KEY_UPDATE_INCLUDE_PRERELEASE = "update_include_prerelease"
        private const val KEY_UPDATE_LAST_CHECKED_MS = "update_last_checked_ms"
        private const val KEY_UPDATE_LATEST_VERSION = "update_latest_version"
        private const val KEY_UPDATE_RELEASE_NOTES = "update_release_notes"
        private const val KEY_UPDATE_RELEASE_URL = "update_release_url"
        private const val KEY_UPDATE_RELEASE_DATE = "update_release_date"
        private const val KEY_UPDATE_ETAG = "update_etag"
        private const val KEY_UPDATE_RATE_LIMIT_RESET_MS = "update_rate_limit_reset_ms"
        private const val KEY_DONATION_FIRST_USE_MS = "donation_first_use_ms"
        private const val KEY_DONATION_LAST_ACCEPTED_MS = "donation_last_accepted_ms"
        private const val KEY_SAFE_MODE_ENABLED = "safe_mode_enabled"
        private const val KEY_LAST_BLOCKED_PACKAGE = "last_blocked_package"
        private const val KEY_EXCLUDED_PACKAGES = "excluded_packages"

        fun mapLegacyEngineToId(engine: VisualSearchEngine): String = when (engine) {
            VisualSearchEngine.GOOGLE_LENS -> "visual_google_lens"
            VisualSearchEngine.YANDEX -> "visual_yandex"
            VisualSearchEngine.TINEYE -> "visual_tineye"
            VisualSearchEngine.BING_VISUAL -> "visual_bing"
            VisualSearchEngine.SAUCENAO -> "visual_saucenao"
            VisualSearchEngine.TRACE_MOE -> "visual_tracemoe"
            VisualSearchEngine.SHARESHEET -> "visual_open_with"
        }

        fun mapIdToLegacyEngine(id: String): VisualSearchEngine? = when (id) {
            "visual_google_lens" -> VisualSearchEngine.GOOGLE_LENS
            "visual_yandex" -> VisualSearchEngine.YANDEX
            "visual_tineye" -> VisualSearchEngine.TINEYE
            "visual_bing" -> VisualSearchEngine.BING_VISUAL
            "visual_saucenao" -> VisualSearchEngine.SAUCENAO
            "visual_tracemoe" -> VisualSearchEngine.TRACE_MOE
            "visual_open_with" -> VisualSearchEngine.SHARESHEET
            else -> null
        }

        val DEFAULT_EXCLUDED_PACKAGES = setOf(
            "com.bca",
            "com.bca.mybca.omni.android",
            "id.co.bri.brimo",
            "id.bmri.livin",
            "src.com.bni",
            "com.gojek.gopay",
            "ovo.id",
            "id.dana",
            "com.x8bit.bitwarden",
            "com.onepassword.android",
            "com.google.android.apps.authenticator2",
            "com.azure.authenticator",
            "com.authy.authy",
            "gov.dukcapil.mobile_id"
        )
    }
}
