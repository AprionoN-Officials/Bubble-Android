package com.bubble.screentools.translation

import android.graphics.RectF
import android.util.LruCache
import com.bubble.screentools.model.TextBlockResult
import com.bubble.screentools.model.TranslatedBlock
import com.bubble.screentools.model.TranslationContextProfile
import com.bubble.screentools.model.TranslationQualityMode
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Enhanced On-Device ML Kit Translation Manager with:
 * - Quality-mode aware context reconstruction (FAST / ACCURATE / NATURAL)
 * - Context profile processing (GENERAL / LITERAL / NATURAL / MANGA_GAME)
 * - Preferred Source Language & Fallback Language disambiguation
 * - In-memory 256-entry LRU cache & offline model management
 */
class TranslationManager(
    private val languageDetectionManager: LanguageDetectionManager
) {

    private val activeTranslators = HashMap<String, Translator>()

    suspend fun translateBlocks(
        blocks: List<TextBlockResult>,
        sourceLangPref: String,
        targetLangPref: String,
        autoDetect: Boolean,
        useCache: Boolean,
        qualityMode: TranslationQualityMode = TranslationQualityMode.NATURAL,
        contextProfile: TranslationContextProfile = TranslationContextProfile.NATURAL,
        preferredSources: List<String> = emptyList(),
        fallbackSourceLang: String = "en"
    ): List<TranslatedBlock> = withContext(Dispatchers.Default) {
        val targetMlKit = mapToMlKitLanguage(targetLangPref) ?: TranslateLanguage.INDONESIAN

        // Run context reconstruction pipeline tailored to qualityMode & contextProfile
        val processedSpatial = TranslationContextProcessor.processBlocks(
            blocks = blocks.map {
                TranslationContextProcessor.SpatialTextBlock(
                    text = it.text,
                    left = it.boundingBox.left,
                    top = it.boundingBox.top,
                    right = it.boundingBox.right,
                    bottom = it.boundingBox.bottom,
                    fromAccessibility = it.fromAccessibility
                )
            },
            qualityMode = qualityMode,
            contextProfile = contextProfile
        )

        val out = ArrayList<TranslatedBlock>(processedSpatial.size)

        for (block in processedSpatial) {
            val detected = resolveSourceLanguage(
                text = block.text,
                sourceLangPref = sourceLangPref,
                autoDetect = autoDetect,
                preferredSources = preferredSources,
                fallbackSourceLang = fallbackSourceLang
            )

            val sourceMlKit = mapToMlKitLanguage(detected) ?: mapToMlKitLanguage(fallbackSourceLang) ?: TranslateLanguage.ENGLISH
            val box = RectF(block.left, block.top, block.right, block.bottom)

            if (sourceMlKit == targetMlKit) {
                out.add(
                    TranslatedBlock(
                        originalText = block.text,
                        translatedText = block.text,
                        detectedLangCode = detected,
                        boundingBox = box
                    )
                )
                continue
            }

            val cacheKey = "$sourceMlKit->$targetMlKit|${qualityMode.name}|${contextProfile.name}:${block.text}"
            val cached = if (useCache) memoryCache.get(cacheKey) else null

            val translated = cached ?: run {
                val segments = if (qualityMode == TranslationQualityMode.NATURAL) {
                    TranslationContextProcessor.detectMixedScriptSegments(block.text)
                } else {
                    listOf(block.text)
                }
                val translatedSegments = segments.map { segment ->
                    runTranslator(sourceMlKit, targetMlKit, segment) ?: segment
                }
                val joined = translatedSegments.joinToString(" | ")
                if (useCache) memoryCache.put(cacheKey, joined)
                joined
            }

            out.add(
                TranslatedBlock(
                    originalText = block.text,
                    translatedText = translated,
                    detectedLangCode = detected,
                    boundingBox = box
                )
            )
        }
        out
    }

    private suspend fun resolveSourceLanguage(
        text: String,
        sourceLangPref: String,
        autoDetect: Boolean,
        preferredSources: List<String>,
        fallbackSourceLang: String
    ): String {
        if (!autoDetect && sourceLangPref != "auto") {
            return sourceLangPref
        }
        val detected = languageDetectionManager.detectLanguageCode(text)
        if (detected.isNotBlank() && detected != "und" && mapToMlKitLanguage(detected) != null) {
            return detected
        }
        if (TranslationContextProcessor.containsCjk(text)) {
            val preferredCjk = preferredSources.firstOrNull { it in setOf("ja", "zh", "ko") }
            if (preferredCjk != null) return preferredCjk
        }
        return preferredSources.firstOrNull { mapToMlKitLanguage(it) != null }
            ?: fallbackSourceLang.ifBlank { "en" }
    }

    private suspend fun runTranslator(
        sourceLang: String,
        targetLang: String,
        text: String
    ): String? {
        val key = "$sourceLang->$targetLang"
        val translator = activeTranslators[key] ?: Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(targetLang)
                .build()
        ).also { activeTranslators[key] = it }

        val downloaded = ensureModelDownloaded(translator)
        if (!downloaded) return null

        return suspendCancellableCoroutine { cont ->
            translator.translate(text)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }
    }

    private suspend fun ensureModelDownloaded(translator: Translator): Boolean =
        suspendCancellableCoroutine { cont ->
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }

    suspend fun getDownloadedModels(): Set<String> = suspendCancellableCoroutine { cont ->
        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                cont.resume(models.map { it.language }.toSet())
            }
            .addOnFailureListener {
                cont.resume(emptySet())
            }
    }

    suspend fun downloadLanguageModel(langCode: String): Boolean {
        val mlKitLang = mapToMlKitLanguage(langCode) ?: return false
        val model = TranslateRemoteModel.Builder(mlKitLang).build()
        val conditions = DownloadConditions.Builder().build()
        return suspendCancellableCoroutine { cont ->
            RemoteModelManager.getInstance()
                .download(model, conditions)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    suspend fun deleteLanguageModel(langCode: String): Boolean {
        val mlKitLang = mapToMlKitLanguage(langCode) ?: return false
        val model = TranslateRemoteModel.Builder(mlKitLang).build()
        return suspendCancellableCoroutine { cont ->
            RemoteModelManager.getInstance()
                .deleteDownloadedModel(model)
                .addOnSuccessListener { cont.resume(true) }
                .addOnFailureListener { cont.resume(false) }
        }
    }

    suspend fun deleteUnusedModels(keepCodes: Set<String>): Int {
        val downloaded = getDownloadedModels()
        val normalizedKeep = keepCodes.mapNotNull { mapToMlKitLanguage(it) }.toSet()
        var deletedCount = 0
        for (modelCode in downloaded) {
            if (modelCode !in normalizedKeep) {
                if (deleteLanguageModel(modelCode)) {
                    deletedCount++
                }
            }
        }
        return deletedCount
    }

    fun release() {
        activeTranslators.values.forEach { it.close() }
        activeTranslators.clear()
    }

    companion object {
        const val ESTIMATED_MLKIT_MODEL_SIZE_MB = 30
        private val memoryCache = LruCache<String, String>(256)

        fun clearMemoryCache() {
            memoryCache.evictAll()
        }

        fun mapToMlKitLanguage(code: String): String? {
            return TranslateLanguage.fromLanguageTag(code.lowercase())
        }
    }
}
