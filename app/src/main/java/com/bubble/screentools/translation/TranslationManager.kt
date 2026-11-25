package com.bubble.screentools.translation

import android.util.LruCache
import com.bubble.screentools.model.TextBlockResult
import com.bubble.screentools.model.TranslatedBlock
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
 * On-device ML Kit Translation Manager with in-memory LRU caching,
 * on-demand offline language model downloads, and explicit download/delete management.
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
        useCache: Boolean
    ): List<TranslatedBlock> = withContext(Dispatchers.Default) {
        val targetMlKit = mapToMlKitLanguage(targetLangPref) ?: TranslateLanguage.INDONESIAN
        val out = ArrayList<TranslatedBlock>(blocks.size)

        for (block in blocks) {
            val detected = if (autoDetect || sourceLangPref == "auto") {
                languageDetectionManager.detectLanguageCode(block.text)
            } else {
                sourceLangPref
            }

            val sourceMlKit = mapToMlKitLanguage(detected) ?: TranslateLanguage.ENGLISH
            if (sourceMlKit == targetMlKit) {
                out.add(
                    TranslatedBlock(
                        originalText = block.text,
                        translatedText = block.text,
                        detectedLangCode = detected,
                        boundingBox = block.boundingBox
                    )
                )
                continue
            }

            val cacheKey = "$sourceMlKit->$targetMlKit:${block.text}"
            val cached = if (useCache) memoryCache.get(cacheKey) else null
            val translated = cached ?: runTranslator(sourceMlKit, targetMlKit, block.text)?.also {
                if (useCache) memoryCache.put(cacheKey, it)
            } ?: block.text

            out.add(
                TranslatedBlock(
                    originalText = block.text,
                    translatedText = translated,
                    detectedLangCode = detected,
                    boundingBox = block.boundingBox
                )
            )
        }
        out
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

    fun release() {
        activeTranslators.values.forEach { it.close() }
        activeTranslators.clear()
    }

    companion object {
        private val memoryCache = LruCache<String, String>(256)

        fun clearMemoryCache() {
            memoryCache.evictAll()
        }

        fun mapToMlKitLanguage(code: String): String? {
            return TranslateLanguage.fromLanguageTag(code.lowercase())
        }
    }
}
