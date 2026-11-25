package com.bubble.screentools.model

import android.graphics.Path
import android.graphics.RectF

enum class SelectionShape {
    RECTANGLE,
    ELLIPSE,
    FREEFORM
}

enum class ActionMode {
    TRANSLATE,
    VISUAL_SEARCH
}

enum class VisualSearchEngine(val displayName: String, val shortLabel: String) {
    GOOGLE_LENS("Google Lens (Utama)", "Google Lens"),
    YANDEX("Yandex Images", "Yandex"),
    TINEYE("TinEye Reverse Search", "TinEye"),
    SHARESHEET("Bagikan ke Aplikasi Lain", "Bagikan")
}

enum class TranslationOverlayStyle {
    SEMI_TRANSPARENT_BOX,
    ADAPTIVE_COVER
}

data class SelectionRegion(
    val bounds: RectF,
    val shape: SelectionShape,
    val freeformPath: Path? = null
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
