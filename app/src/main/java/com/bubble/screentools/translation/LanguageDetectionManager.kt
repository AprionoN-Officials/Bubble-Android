package com.bubble.screentools.translation

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LanguageDetectionManager {

    private var identifier: LanguageIdentifier? = null

    private fun client(): LanguageIdentifier {
        return identifier ?: LanguageIdentification.getClient().also { identifier = it }
    }

    suspend fun detectLanguageCode(text: String): String = suspendCancellableCoroutine { cont ->
        if (text.isBlank()) {
            cont.resume("en")
            return@suspendCancellableCoroutine
        }
        client().identifyLanguage(text)
            .addOnSuccessListener { code ->
                if (code == "und" || code.isBlank()) {
                    cont.resume("en")
                } else {
                    cont.resume(code)
                }
            }
            .addOnFailureListener {
                cont.resume("en")
            }
    }

    fun release() {
        identifier?.close()
        identifier = null
    }
}
