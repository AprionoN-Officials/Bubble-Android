package com.bubble.screentools

import com.bubble.screentools.model.TranslationContextProfile
import com.bubble.screentools.model.TranslationQualityMode
import com.bubble.screentools.ocr.TesseractOcrManager
import com.bubble.screentools.translation.TranslationContextProcessor
import com.bubble.screentools.translation.TranslationContextProcessor.SpatialTextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests real behavioral differentiation across Translation Quality Modes (FAST/ACCURATE/NATURAL),
 * spatial paragraph & vertical CJK reconstruction, and strict Tesseract .traineddata validation.
 */
class TranslationPipelineTest {

    @Test
    fun qualityModesProduceGenuinePipelineDifferencesOnSameCorpus() {
        val corpus = listOf(
            SpatialTextBlock("Critica| Damage increases trans-", 10f, 10f, 300f, 34f),
            SpatialTextBlock("lation accuracy across multi-line", 10f, 40f, 300f, 64f),
            SpatialTextBlock("paragraphs.", 10f, 70f, 140f, 94f)
        )

        val fast = TranslationContextProcessor.processBlocks(
            corpus, TranslationQualityMode.FAST, TranslationContextProfile.GENERAL
        )
        val accurate = TranslationContextProcessor.processBlocks(
            corpus, TranslationQualityMode.ACCURATE, TranslationContextProfile.GENERAL
        )
        val natural = TranslationContextProcessor.processBlocks(
            corpus, TranslationQualityMode.NATURAL, TranslationContextProfile.NATURAL
        )

        // FAST leaves 3 separate blocks with minimal cleanup
        assertEquals(3, fast.size)
        assertTrue(fast[0].text.contains("Critica|"))

        // ACCURATE cleans OCR pipe glitch into 'Critical' while keeping line structure
        assertEquals(3, accurate.size)
        assertTrue(accurate[0].text.contains("Critical"))

        // NATURAL reconstructs all 3 lines into 1 coherent paragraph AND repairs 'trans-\nlation' -> 'translation'
        assertEquals(1, natural.size)
        assertEquals("Critical Damage increases translation accuracy across multi-line paragraphs.", natural[0].text)
    }

    @Test
    fun verticalJapaneseMangaColumnsSortRightToLeftAndMerge() {
        val rightCol = SpatialTextBlock("今日は", 200f, 20f, 230f, 180f)
        val leftCol = SpatialTextBlock("いい天気だね", 155f, 20f, 185f, 180f)

        val merged = TranslationContextProcessor.processBlocks(
            listOf(leftCol, rightCol),
            TranslationQualityMode.NATURAL,
            TranslationContextProfile.MANGA_GAME
        )
        assertEquals(1, merged.size)
        assertEquals("今日はいい天気だね", merged[0].text)
    }

    @Test
    fun tesseractValidationAcceptsBinaryTraineddataAndRejectsCorruptOrTextFiles() {
        val validBinaryHeader = byteArrayOf(0x01, 0x00, 0x54, 0x45, 0x53, 0x53)
        assertTrue(
            TesseractOcrManager.validateTrainedDataPayload("jpn.traineddata", 2_500_000L, validBinaryHeader).isSuccess
        )

        // Reject too-small corrupted file
        assertFalse(
            TesseractOcrManager.validateTrainedDataPayload("jpn.traineddata", 256L, validBinaryHeader).isSuccess
        )

        // Reject JSON text file renamed to .traineddata
        val jsonHeader = """{"name":"NotOcr"}""".toByteArray()
        assertFalse(
            TesseractOcrManager.validateTrainedDataPayload("fake.traineddata", 50_000L, jsonHeader).isSuccess
        )

        // Reject wrong extension
        assertFalse(
            TesseractOcrManager.validateTrainedDataPayload("jpn.json", 50_000L, validBinaryHeader).isSuccess
        )
    }
}
