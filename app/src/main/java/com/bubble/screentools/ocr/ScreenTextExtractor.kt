package com.bubble.screentools.ocr

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.RectF
import android.view.accessibility.AccessibilityNodeInfo
import com.bubble.screentools.model.SelectionRegion
import com.bubble.screentools.model.TextBlockResult

/**
 * Hybrid Screen Text Extractor:
 * Primary Engine -> AccessibilityNodeInfo tree via getRootInActiveWindow().
 * Skips password/sensitive nodes strictly.
 */
class ScreenTextExtractor {

    fun extractFromAccessibilityTree(
        service: AccessibilityService?,
        region: SelectionRegion,
        entireScreen: Boolean
    ): List<TextBlockResult> {
        val root = runCatching { service?.rootInActiveWindow }.getOrNull() ?: return emptyList()
        val results = mutableListOf<TextBlockResult>()
        val seenTexts = HashSet<String>()

        try {
            traverseNode(root, region, entireScreen, results, seenTexts)
        } finally {
            @Suppress("DEPRECATION")
            runCatching { root.recycle() }
        }
        return results
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        region: SelectionRegion,
        entireScreen: Boolean,
        out: MutableList<TextBlockResult>,
        seen: MutableSet<String>
    ) {
        // Privacy safeguard: never read password fields
        if (node.isPassword) return

        if (node.isVisibleToUser) {
            val rawText = (node.text ?: node.contentDescription)?.toString()?.trim()
            if (!rawText.isNullOrEmpty() && rawText.length <= 600) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val rectF = RectF(bounds)
                val intersects = entireScreen || RectF.intersects(rectF, region.bounds)
                val key = "$rawText@${bounds.left},${bounds.top}"
                if (intersects && bounds.width() > 4 && bounds.height() > 4 && seen.add(key)) {
                    // Prefer leaf-level text nodes to avoid duplicating container summaries
                    if (node.childCount == 0 || !node.text.isNullOrBlank()) {
                        out.add(
                            TextBlockResult(
                                text = rawText,
                                boundingBox = rectF,
                                fromAccessibility = true
                            )
                        )
                    }
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                traverseNode(child, region, entireScreen, out, seen)
            } finally {
                @Suppress("DEPRECATION")
                runCatching { child.recycle() }
            }
        }
    }
}
