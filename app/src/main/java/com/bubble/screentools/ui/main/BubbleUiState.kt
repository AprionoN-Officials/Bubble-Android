package com.bubble.screentools.ui.main

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.lens.LensIntegrationManager
import com.bubble.screentools.model.ActionMode
import com.bubble.screentools.model.SelectionShape
import com.bubble.screentools.model.VisualSearchEngine
import com.bubble.screentools.ocr.TesseractOcrManager
import com.bubble.screentools.service.BubbleAccessibilityService
import com.bubble.screentools.translation.LanguageDetectionManager
import com.bubble.screentools.translation.SupportedLanguages
import com.bubble.screentools.translation.TranslationManager
import com.bubble.screentools.ui.theme.BubblePalette
import com.bubble.screentools.util.CompatibilityHelper
import com.bubble.screentools.util.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Stable
class BubbleUiState(
    private val context: Context,
    val preferences: BubblePreferences,
) {
    private val lensManager = LensIntegrationManager(context)

    // Appearance
    var themeMode by mutableStateOf(preferences.themeMode)
        private set
    var pureBlack by mutableStateOf(preferences.pureBlack)
        private set
    var themePalette by mutableStateOf(BubblePalette.fromKey(preferences.themePalette))
        private set
    var appLanguageCode by mutableStateOf(preferences.appLanguageCode)
        private set

    // Translation
    var sourceLanguageCode by mutableStateOf(preferences.sourceLanguageCode)
        private set
    var targetLanguageCode by mutableStateOf(preferences.targetLanguageCode)
        private set
    var autoDetectLanguage by mutableStateOf(preferences.autoDetectLanguage)
        private set
    var autoTranslateSelection by mutableStateOf(preferences.autoTranslateSelection)
        private set
    var translateEntireScreen by mutableStateOf(preferences.translateEntireScreen)
        private set

    // OCR
    var useTesseractOcr by mutableStateOf(preferences.useTesseractOcr)
        private set
    var activeTesseractLang by mutableStateOf(preferences.activeTesseractLang)
        private set
    var installedTessModels by mutableStateOf(TesseractOcrManager.listInstalledModels(context))
        private set

    // Visual Search
    var visualSearchEngine by mutableStateOf(preferences.visualSearchEngine)
        private set
    var autoSearchAfterSelection by mutableStateOf(preferences.autoSearchAfterSelection)
        private set

    // Overlay & QR
    var autoQrScanEnabled by mutableStateOf(preferences.autoQrScanEnabled)
        private set
    var defaultSelectionShape by mutableStateOf(preferences.defaultSelectionShape)
        private set
    var lastActionMode by mutableStateOf(preferences.lastActionMode)
        private set

    // Privacy & Safe Mode
    var safeModeEnabled by mutableStateOf(preferences.safeModeEnabled)
        private set
    var excludedPackages by mutableStateOf(preferences.excludedPackages.sorted())
        private set
    var lastBlockedPackageName by mutableStateOf(preferences.lastBlockedPackageName)
        private set

    // Live runtime status
    var isDefaultAssistant by mutableStateOf(CompatibilityHelper.isDefaultDigitalAssistant(context))
        private set
    var isAccessibilityActive by mutableStateOf(BubbleAccessibilityService.isRunning())
        private set
    var supportsSilentA11yCapture by mutableStateOf(CompatibilityHelper.supportsAccessibilityScreenshot())
        private set
    var lensAvailability by mutableStateOf(lensManager.checkAvailability())
        private set
    var downloadedMlKitModels by mutableStateOf<Set<String>>(emptySet())
        private set

    val effectiveSourceLanguageCode: String
        get() = if (autoDetectLanguage) SupportedLanguages.AUTO_CODE else sourceLanguageCode

    val isReadyToScan: Boolean
        get() = isDefaultAssistant || isAccessibilityActive

    fun syncPreferencesAndStatus() {
        themeMode = preferences.themeMode
        pureBlack = preferences.pureBlack
        themePalette = BubblePalette.fromKey(preferences.themePalette)
        appLanguageCode = preferences.appLanguageCode

        sourceLanguageCode = preferences.sourceLanguageCode
        targetLanguageCode = preferences.targetLanguageCode
        autoDetectLanguage = preferences.autoDetectLanguage
        autoTranslateSelection = preferences.autoTranslateSelection
        translateEntireScreen = preferences.translateEntireScreen

        useTesseractOcr = preferences.useTesseractOcr
        activeTesseractLang = preferences.activeTesseractLang
        installedTessModels = TesseractOcrManager.listInstalledModels(context)

        visualSearchEngine = preferences.visualSearchEngine
        autoSearchAfterSelection = preferences.autoSearchAfterSelection

        autoQrScanEnabled = preferences.autoQrScanEnabled
        defaultSelectionShape = preferences.defaultSelectionShape
        lastActionMode = preferences.lastActionMode

        safeModeEnabled = preferences.safeModeEnabled
        excludedPackages = preferences.excludedPackages.sorted()
        lastBlockedPackageName = preferences.lastBlockedPackageName

        isDefaultAssistant = CompatibilityHelper.isDefaultDigitalAssistant(context)
        isAccessibilityActive = BubbleAccessibilityService.isRunning()
        supportsSilentA11yCapture = CompatibilityHelper.supportsAccessibilityScreenshot()
        lensAvailability = lensManager.checkAvailability()
    }

    suspend fun refreshDownloadedModels() {
        val models =
            withContext(Dispatchers.Default) {
                val tm = TranslationManager(LanguageDetectionManager())
                try {
                    tm.getDownloadedModels()
                } finally {
                    tm.release()
                }
            }
        downloadedMlKitModels = models
        installedTessModels = TesseractOcrManager.listInstalledModels(context)
    }

    fun updateThemeMode(mode: String) {
        preferences.themeMode = mode
        themeMode = mode
    }

    fun updatePureBlack(enabled: Boolean) {
        preferences.pureBlack = enabled
        pureBlack = enabled
    }

    fun updateThemePalette(palette: BubblePalette) {
        preferences.themePalette = palette.key
        themePalette = palette
    }

    fun updateAppLanguage(langCode: String) {
        if (langCode != preferences.appLanguageCode) {
            preferences.appLanguageCode = langCode
            appLanguageCode = langCode
            LocaleHelper.applyAppLocale(langCode)
        }
    }

    fun updateSourceLanguage(code: String) {
        preferences.sourceLanguageCode = code
        preferences.autoDetectLanguage = (code == SupportedLanguages.AUTO_CODE)
        sourceLanguageCode = code
        autoDetectLanguage = preferences.autoDetectLanguage
    }

    fun updateTargetLanguage(code: String) {
        preferences.targetLanguageCode = code
        targetLanguageCode = code
    }

    fun swapLanguages(): Pair<String, String> {
        val (newSource, newTarget) =
            SupportedLanguages.swap(
                currentSourceCode = effectiveSourceLanguageCode,
                currentTargetCode = targetLanguageCode,
            )
        preferences.sourceLanguageCode = newSource
        preferences.targetLanguageCode = newTarget
        preferences.autoDetectLanguage = (newSource == SupportedLanguages.AUTO_CODE)
        sourceLanguageCode = newSource
        targetLanguageCode = newTarget
        autoDetectLanguage = preferences.autoDetectLanguage
        return newSource to newTarget
    }

    fun updateAutoDetect(enabled: Boolean) {
        preferences.autoDetectLanguage = enabled
        if (enabled) {
            preferences.sourceLanguageCode = SupportedLanguages.AUTO_CODE
        } else if (preferences.sourceLanguageCode == SupportedLanguages.AUTO_CODE) {
            preferences.sourceLanguageCode = if (preferences.targetLanguageCode == "id") "en" else "id"
        }
        autoDetectLanguage = enabled
        sourceLanguageCode = preferences.sourceLanguageCode
    }

    fun updateAutoTranslate(enabled: Boolean) {
        preferences.autoTranslateSelection = enabled
        autoTranslateSelection = enabled
    }

    fun updateTranslateEntireScreen(enabled: Boolean) {
        preferences.translateEntireScreen = enabled
        translateEntireScreen = enabled
    }

    fun updateUseTesseractOcr(enabled: Boolean) {
        preferences.useTesseractOcr = enabled
        if (enabled && preferences.activeTesseractLang.isBlank() && installedTessModels.isNotEmpty()) {
            preferences.activeTesseractLang = installedTessModels.first().code
        }
        useTesseractOcr = preferences.useTesseractOcr
        activeTesseractLang = preferences.activeTesseractLang
    }

    fun activateTesseractModel(code: String) {
        preferences.activeTesseractLang = code
        preferences.useTesseractOcr = true
        activeTesseractLang = code
        useTesseractOcr = true
        installedTessModels = TesseractOcrManager.listInstalledModels(context)
    }

    fun deleteTesseractModel(code: String) {
        TesseractOcrManager.deleteModel(context, code)
        val remaining = TesseractOcrManager.listInstalledModels(context)
        installedTessModels = remaining
        if (preferences.activeTesseractLang == code) {
            preferences.activeTesseractLang = remaining.firstOrNull()?.code.orEmpty()
            if (remaining.isEmpty()) {
                preferences.useTesseractOcr = false
            }
        }
        activeTesseractLang = preferences.activeTesseractLang
        useTesseractOcr = preferences.useTesseractOcr
    }

    fun updateVisualSearchEngine(engine: VisualSearchEngine) {
        preferences.visualSearchEngine = engine
        visualSearchEngine = engine
    }

    fun updateAutoVisualSearch(enabled: Boolean) {
        preferences.autoSearchAfterSelection = enabled
        autoSearchAfterSelection = enabled
    }

    fun updateAutoQrScan(enabled: Boolean) {
        preferences.autoQrScanEnabled = enabled
        autoQrScanEnabled = enabled
    }

    fun updateSelectionShape(shape: SelectionShape) {
        preferences.defaultSelectionShape = shape
        defaultSelectionShape = shape
    }

    fun updateActionMode(mode: ActionMode) {
        preferences.lastActionMode = mode
        lastActionMode = mode
    }

    fun updateSafeMode(enabled: Boolean) {
        preferences.safeModeEnabled = enabled
        safeModeEnabled = enabled
    }

    fun addExcludedPackage(packageName: String) {
        val clean = packageName.trim()
        if (clean.isEmpty()) return
        preferences.addExcludedPackage(clean)
        excludedPackages = preferences.excludedPackages.sorted()
    }

    fun removeExcludedPackage(packageName: String) {
        preferences.removeExcludedPackage(packageName)
        excludedPackages = preferences.excludedPackages.sorted()
    }

    fun clearLastBlockedNotice() {
        preferences.lastBlockedPackageName = null
        lastBlockedPackageName = null
    }
}
