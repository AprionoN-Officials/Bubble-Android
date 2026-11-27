package com.bubble.screentools.translation

import com.bubble.screentools.model.TranslationContextProfile
import com.bubble.screentools.model.TranslationQualityMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure-Kotlin OCR Context Reconstruction & Text Normalization Engine.
 * Delivers genuine behavioral differences between FAST, ACCURATE, and NATURAL modes
 * and GENERAL / LITERAL / NATURAL / MANGA_GAME context profiles without requiring any generative LLM.
 */
object TranslationContextProcessor {

    data class SpatialTextBlock(
        val text: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val fromAccessibility: Boolean = false
    ) {
        val width: Float get() = (right - left).coerceAtLeast(1f)
        val height: Float get() = (bottom - top).coerceAtLeast(1f)
        val centerX: Float get() = (left + right) * 0.5f
        val centerY: Float get() = (top + bottom) * 0.5f
    }

    /**
     * Processes raw OCR / Accessibility text blocks according to quality mode & context profile.
     * - FAST: Minimal whitespace cleanup, zero spatial merging (fastest latency).
     * - ACCURATE: OCR artifact normalization, reading-order sorting, punctuation repair.
     * - NATURAL: Accurate + spatial paragraph/speech-bubble reconstruction, vertical CJK
     *   right-to-left column joining, de-hyphenation, and sentence flow preservation.
     */
    fun processBlocks(
        blocks: List<SpatialTextBlock>,
        qualityMode: TranslationQualityMode,
        contextProfile: TranslationContextProfile
    ): List<SpatialTextBlock> {
        if (blocks.isEmpty()) return emptyList()

        // Step 1: Normalize individual block text
        val normalized = blocks.mapNotNull { b ->
            val cleaned = normalizeText(b.text, qualityMode, contextProfile)
            if (cleaned.isBlank()) null else b.copy(text = cleaned)
        }
        if (normalized.isEmpty()) return emptyList()

        // FAST mode or LITERAL profile: keep line-by-line unmerged order
        if (qualityMode == TranslationQualityMode.FAST || contextProfile == TranslationContextProfile.LITERAL) {
            return sortReadingOrder(normalized, verticalManga = false)
        }

        // ACCURATE mode: clean reading order without aggressive cross-block merging (except hyphen breaks)
        if (qualityMode == TranslationQualityMode.ACCURATE) {
            val ordered = sortReadingOrder(normalized, verticalManga = isPredominantlyVerticalCjk(normalized))
            return ordered.map { it.copy(text = repairHyphenatedBreaks(it.text)) }
        }

        // NATURAL mode: full spatial clustering, paragraph/speech-bubble reconstruction, & vertical CJK handling
        val isVerticalCjk = isPredominantlyVerticalCjk(normalized)
        val ordered = sortReadingOrder(normalized, verticalManga = isVerticalCjk)
        val merged = if (isVerticalCjk) {
            mergeVerticalCjkColumns(ordered)
        } else {
            mergeHorizontalParagraphs(
                blocks = ordered,
                tightBubbleClustering = (contextProfile == TranslationContextProfile.MANGA_GAME)
            )
        }
        return merged.map { block ->
            block.copy(text = postProcessNaturalSentence(block.text, contextProfile))
        }
    }

    fun normalizeText(
        raw: String,
        qualityMode: TranslationQualityMode,
        contextProfile: TranslationContextProfile
    ): String {
        var text = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return ""

        if (qualityMode == TranslationQualityMode.FAST) {
            return text.replace(Regex("[ \\t]{2,}"), " ")
        }

        // Normalize curly quotes/dashes and fix common OCR glitches (stray pipes as 'I' inside words)
        text = text
            .replace('\u00A0', ' ')
            .replace("ﬁ", "fi")
            .replace("ﬂ", "fl")
            .replace(Regex("(?<=[A-Za-z])\\|(?=[A-Za-z\\s.,!?;:]|$)"), "l")
            .replace(Regex("^\\|(?=[a-z])"), "I")
            .replace(Regex("[ \\t]{2,}"), " ")

        // Remove spacing artifacts before Latin punctuation
        text = text.replace(Regex(" +([.,!?;:])"), "$1")

        // In Manga/Game mode preserve UI brackets like [HP], +15%, LV.10 cleanly
        if (contextProfile == TranslationContextProfile.MANGA_GAME) {
            text = text.replace(Regex("\\[\\s+"), "[").replace(Regex("\\s+]"), "]")
        }

        return text.trim()
    }

    fun repairHyphenatedBreaks(text: String): String {
        // Rejoin words broken across lines with trailing hyphen: "trans-\nlation" -> "translation"
        return text.replace(Regex("([A-Za-z]{2,})-\\s*\\n\\s*([a-z]{2,})"), "$1$2")
    }

    fun postProcessNaturalSentence(text: String, profile: TranslationContextProfile): String {
        var out = repairHyphenatedBreaks(text)
        // Join soft line breaks inside a paragraph without collapsing double-newline paragraph gaps
        val lines = out.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 1) return out.trim()

        val sb = StringBuilder()
        for (i in lines.indices) {
            val line = lines[i]
            if (i > 0) {
                val prev = lines[i - 1]
                val prevLast = prev.last()
                val currFirst = line.first()
                val isCjkBoundary = isCjkChar(prevLast) && isCjkChar(currFirst)
                val endsParagraph = prevLast in charArrayOf('.', '!', '?', '。', '！', '？') &&
                    profile != TranslationContextProfile.MANGA_GAME &&
                    line.length > 3 && currFirst.isUpperCase()
                sb.append(
                    when {
                        isCjkBoundary -> ""
                        endsParagraph -> " "
                        else -> " "
                    }
                )
            }
            sb.append(line)
        }
        out = sb.toString()
        return out.replace(Regex(" {2,}"), " ").trim()
    }

    fun isPredominantlyVerticalCjk(blocks: List<SpatialTextBlock>): Boolean {
        var verticalCjkCount = 0
        var cjkBlocks = 0
        for (b in blocks) {
            if (containsCjk(b.text)) {
                cjkBlocks++
                if (b.height > b.width * 1.35f) {
                    verticalCjkCount++
                }
            }
        }
        return cjkBlocks > 0 && verticalCjkCount * 2 >= cjkBlocks
    }

    fun sortReadingOrder(
        blocks: List<SpatialTextBlock>,
        verticalManga: Boolean
    ): List<SpatialTextBlock> {
        return if (verticalManga) {
            // Vertical Japanese/Chinese manga: columns right-to-left, top-to-bottom within column
            blocks.sortedWith(compareByDescending<SpatialTextBlock> { (it.centerX / 48f).toInt() }.thenBy { it.top })
        } else {
            // Horizontal reading order: top-to-bottom banded rows, then left-to-right
            blocks.sortedWith(compareBy<SpatialTextBlock> { (it.top / 24f).toInt() }.thenBy { it.left })
        }
    }

    private fun mergeHorizontalParagraphs(
        blocks: List<SpatialTextBlock>,
        tightBubbleClustering: Boolean
    ): List<SpatialTextBlock> {
        if (blocks.size <= 1) return blocks
        val result = ArrayList<SpatialTextBlock>()
        var current = blocks[0]

        for (i in 1 until blocks.size) {
            val next = blocks[i]
            val lineH = min(current.height, next.height).coerceAtLeast(12f)
            val verticalGap = next.top - current.bottom
            val maxGap = if (tightBubbleClustering) lineH * 1.15f else lineH * 1.45f

            val overlapX = min(current.right, next.right) - max(current.left, next.left)
            val minWidth = min(current.width, next.width).coerceAtLeast(1f)
            val horizontalAligned = overlapX > 0f && (overlapX / minWidth >= 0.25f) ||
                abs(current.left - next.left) <= lineH * 1.8f

            val sameColumn = verticalGap >= -lineH * 0.45f && verticalGap <= maxGap && horizontalAligned

            if (sameColumn) {
                val joiner = if (containsCjk(current.text) && containsCjk(next.text)) "" else "\n"
                current = SpatialTextBlock(
                    text = "${current.text}$joiner${next.text}",
                    left = min(current.left, next.left),
                    top = min(current.top, next.top),
                    right = max(current.right, next.right),
                    bottom = max(current.bottom, next.bottom),
                    fromAccessibility = current.fromAccessibility && next.fromAccessibility
                )
            } else {
                result.add(current)
                current = next
            }
        }
        result.add(current)
        return result
    }

    private fun mergeVerticalCjkColumns(blocks: List<SpatialTextBlock>): List<SpatialTextBlock> {
        if (blocks.size <= 1) return blocks
        val result = ArrayList<SpatialTextBlock>()
        var current = blocks[0]

        for (i in 1 until blocks.size) {
            val next = blocks[i]
            val colW = min(current.width, next.width).coerceAtLeast(12f)
            // Right-to-left adjacent column inside the same speech bubble
            val horizontalGap = current.left - next.right
            val overlapY = min(current.bottom, next.bottom) - max(current.top, next.top)
            val minHeight = min(current.height, next.height).coerceAtLeast(1f)
            val verticallyAligned = overlapY > 0f && (overlapY / minHeight >= 0.3f)

            if (horizontalGap >= -colW * 0.4f && horizontalGap <= colW * 1.6f && verticallyAligned) {
                current = SpatialTextBlock(
                    text = "${current.text}${next.text}",
                    left = min(current.left, next.left),
                    top = min(current.top, next.top),
                    right = max(current.right, next.right),
                    bottom = max(current.bottom, next.bottom),
                    fromAccessibility = current.fromAccessibility && next.fromAccessibility
                )
            } else {
                result.add(current)
                current = next
            }
        }
        result.add(current)
        return result
    }

    /**
     * Splits a mixed-language string into script-coherent segments when a line combines
     * substantial CJK and Latin phrases (e.g., game UI or bilingual captions).
     */
    fun detectMixedScriptSegments(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.length < 6) return listOf(trimmed)
        val hasCjk = containsCjk(trimmed)
        val hasLatin = trimmed.any { it in 'A'..'Z' || it in 'a'..'z' }
        if (!hasCjk || !hasLatin) return listOf(trimmed)

        val parts = trimmed.split(Regex("\\s*[|/•]\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size > 1) parts else listOf(trimmed)
    }

    fun containsCjk(text: String): Boolean = text.any { isCjkChar(it) }

    private fun isCjkChar(ch: Char): Boolean {
        val code = ch.code
        return code in 0x3040..0x30FF || // Hiragana & Katakana
            code in 0x4E00..0x9FFF ||    // CJK Unified Ideographs
            code in 0xAC00..0xD7AF       // Hangul
    }
}
