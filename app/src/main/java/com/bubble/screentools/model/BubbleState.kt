package com.bubble.screentools.model

import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF

enum class SelectionShape {
    RECTANGLE,
    ROUNDED_RECTANGLE,
    ELLIPSE,
    FREEFORM,
    CUSTOM_CURVED
}

enum class SelectionBorderStyle {
    SOLID,
    DASHED,
    DOTTED,
    DASH_DOT
}

enum class SelectionColorMode {
    MATERIAL_YOU,
    CUSTOM
}

data class SelectionConfig(
    val shape: SelectionShape = SelectionShape.ROUNDED_RECTANGLE,
    val colorMode: SelectionColorMode = SelectionColorMode.MATERIAL_YOU,
    val borderColor: Int = 0xFF2196F3.toInt(),
    val borderThicknessDp: Float = 3.5f,
    val cornerRadiusDp: Float = 16f,
    val curvatureLevel: Float = 0.55f,
    val fillOpacity: Float = 0.0f,
    val outerDimOpacity: Float = 0.38f,
    val borderStyle: SelectionBorderStyle = SelectionBorderStyle.SOLID,
    val showResizeHandles: Boolean = true,
    val handleSizeDp: Float = 7.0f,
    val handleColor: Int = 0xFFFFFFFF.toInt(),
    val handleUseAccentStroke: Boolean = true,
    val smoothFreeformPath: Boolean = true,
    val edgeSmoothing: Boolean = true,
    val animationEnabled: Boolean = true,
    val minSelectionSizeDp: Float = 42f
)

enum class ActionMode {
    TRANSLATE,
    VISUAL_SEARCH
}

enum class VisualSearchEngine(val displayName: String, val shortLabel: String) {
    GOOGLE_LENS("Google Lens (Utama)", "Google Lens"),
    YANDEX("Yandex Images", "Yandex"),
    TINEYE("TinEye Reverse Search", "TinEye"),
    BING_VISUAL("Bing Visual Search", "Bing"),
    SAUCENAO("SauceNAO", "SauceNAO"),
    TRACE_MOE("trace.moe (Anime)", "trace.moe"),
    SHARESHEET("Open With / Share", "Open With")
}

enum class SearchEngineType {
    VISUAL,
    TEXT
}

enum class SearchImageMethod {
    GOOGLE_LENS_NATIVE,
    WEB_DATA_TRANSFER_UPLOAD,
    ANDROID_OPEN_WITH,
    CUSTOM_INTENT_PACKAGE,
    BROWSER_URL_ONLY
}

enum class SearchImageQuality(val maxDimensionPx: Int, val jpegQuality: Int) {
    ORIGINAL(2400, 96),
    BALANCED(1440, 86),
    DATA_SAVER(960, 72)
}

enum class SearchImageFormat {
    AUTO,
    PNG,
    JPEG,
    WEBP
}

data class SearchEngineItem(
    val id: String,
    val name: String,
    val type: SearchEngineType,
    val baseUrl: String,
    val textQueryTemplate: String = "",
    val imageMethod: SearchImageMethod = SearchImageMethod.WEB_DATA_TRANSFER_UPLOAD,
    val targetPackage: String = "",
    val mimeType: String = "image/png",
    val maxImageSizeKb: Int = 5120,
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = true,
    val order: Int = 0
)

enum class TranslationOverlayStyle {
    SEMI_TRANSPARENT_BOX,
    ADAPTIVE_COVER
}

enum class TranslationQualityMode {
    FAST,
    ACCURATE,
    NATURAL
}

enum class TranslationContextProfile {
    GENERAL,
    LITERAL,
    NATURAL,
    MANGA_GAME
}

enum class ThemeMode {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
    AMOLED_BLACK
}

enum class ThemePreset(val seedHex: String) {
    BUBBLE_BLUE("#2196F3"),
    ROSE("#E11D48"),
    LAVENDER("#7C3AED"),
    BLUE("#2563EB"),
    GREEN("#16A34A"),
    AMBER("#D97706"),
    MONOCHROME("#52525B"),
    CUSTOM("#2196F3")
}

data class ThemeConfig(
    val themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    val useDynamicColor: Boolean = true,
    val preset: ThemePreset = ThemePreset.BUBBLE_BLUE,
    val customSeedColor: Int = 0xFF2196F3.toInt(),
    val applyToMainUi: Boolean = true,
    val applyToOverlay: Boolean = true,
    val applyToSelection: Boolean = true
)

enum class ActionPanelButtonType(val id: String, val isDefaultEnabled: Boolean) {
    TRANSLATE("translate", true),
    SEARCH_IMAGE("search_image", true),
    FULL_SCREEN("full_screen", false),
    SELECT_TEXT("select_text", false),
    COPY_IMAGE("copy_image", false),
    SAVE_IMAGE("save_image", false),
    SHARE_IMAGE("share_image", false);

    companion object {
        fun fromId(id: String): ActionPanelButtonType? = entries.firstOrNull { it.id == id }
        val DEFAULT_ENABLED_LIST: List<ActionPanelButtonType> = listOf(TRANSLATE, SEARCH_IMAGE)
    }
}

enum class ActionPanelLayoutMode {
    HORIZONTAL_SCROLL,
    TWO_ROWS,
    ICON_ONLY
}

enum class ActionPanelPosition {
    BOTTOM,
    TOP,
    LEFT,
    RIGHT,
    CUSTOM
}

enum class ActionPanelButtonSize(val heightDp: Int, val iconDp: Int, val textSp: Float) {
    COMPACT(36, 16, 11.5f),
    STANDARD(42, 18, 13f),
    LARGE(48, 20, 14f)
}

data class ActionPanelConfig(
    val enabledButtons: List<ActionPanelButtonType> = ActionPanelButtonType.DEFAULT_ENABLED_LIST,
    val layoutMode: ActionPanelLayoutMode = ActionPanelLayoutMode.HORIZONTAL_SCROLL,
    val showLabels: Boolean = true,
    val position: ActionPanelPosition = ActionPanelPosition.BOTTOM,
    val customPosX: Float = 0.5f,
    val customPosY: Float = 0.88f,
    val buttonSize: ActionPanelButtonSize = ActionPanelButtonSize.STANDARD,
    val buttonSpacingDp: Float = 8f,
    val buttonPaddingHorizontalDp: Float = 12f,
    val buttonCornerRadiusDp: Float = 24f,
    val panelWidthPercent: Int = 92,
    val overallScale: Float = 1.0f,
    val panelOpacity: Float = 0.96f,
    val horizontalMarginDp: Float = 16f,
    val verticalMarginDp: Float = 14f,
    val showSearchEngineSelector: Boolean = true
) {
    /** Guarantees at least one action button is always enabled. */
    fun sanitized(): ActionPanelConfig {
        val validButtons = enabledButtons.distinct().ifEmpty { listOf(ActionPanelButtonType.TRANSLATE) }
        return copy(
            enabledButtons = validButtons,
            customPosX = customPosX.coerceIn(0.05f, 0.95f),
            customPosY = customPosY.coerceIn(0.08f, 0.94f),
            buttonSpacingDp = buttonSpacingDp.coerceIn(2f, 20f),
            buttonPaddingHorizontalDp = buttonPaddingHorizontalDp.coerceIn(6f, 24f),
            buttonCornerRadiusDp = buttonCornerRadiusDp.coerceIn(4f, 32f),
            panelWidthPercent = panelWidthPercent.coerceIn(50, 100),
            overallScale = overallScale.coerceIn(0.75f, 1.25f),
            panelOpacity = panelOpacity.coerceIn(0.45f, 1.0f),
            horizontalMarginDp = horizontalMarginDp.coerceIn(4f, 40f),
            verticalMarginDp = verticalMarginDp.coerceIn(4f, 64f)
        )
    }
}

enum class UpdateCheckInterval(val hours: Long) {
    HOURS_6(6L),
    HOURS_12(12L),
    HOURS_24(24L),
    DAYS_7(168L)
}

data class SelectionRegion(
    val bounds: RectF,
    val shape: SelectionShape,
    val freeformPath: Path? = null,
    val cornerRadiusPx: Float = 0f,
    val curvatureLevel: Float = 0.55f
) {
    fun isValid(minSizePx: Float = 24f): Boolean {
        return bounds.width() >= minSizePx && bounds.height() >= minSizePx
    }
}

data class TextBlockResult(
    val text: String,
    val boundingBox: RectF,
    val fromAccessibility: Boolean
)

data class TranslatedBlock(
    val originalText: String,
    val translatedText: String,
    val detectedLangCode: String,
    val boundingBox: RectF
)

sealed class BubbleOverlayState {
    data object Idle : BubbleOverlayState()
    data class Selecting(val mode: ActionMode) : BubbleOverlayState()
    data class Scanning(val mode: ActionMode, val region: SelectionRegion) : BubbleOverlayState()
    data class Translating(val region: SelectionRegion, val blockCount: Int) : BubbleOverlayState()
    data class Searching(val region: SelectionRegion) : BubbleOverlayState()
    data class Result(
        val mode: ActionMode,
        val blocks: List<TranslatedBlock> = emptyList(),
        val showOriginal: Boolean = false
    ) : BubbleOverlayState()
    data class ProtectedAppBlocked(val packageName: String, val reason: String) : BubbleOverlayState()
    data class Error(val message: String, val recoverable: Boolean = true) : BubbleOverlayState()
}
