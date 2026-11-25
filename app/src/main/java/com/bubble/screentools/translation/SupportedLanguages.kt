package com.bubble.screentools.translation

/**
 * Supported ML Kit on-device language catalog and bidirectional swap logic for Bubble Translator.
 */
object SupportedLanguages {

    data class LanguageOption(
        val code: String,
        val displayNameEn: String,
        val displayNameId: String,
        val shortLabel: String
    ) {
        val displayName: String
            get() = if (isIndonesianLocale()) displayNameId else displayNameEn
    }

    const val AUTO_CODE = "auto"

    private fun isIndonesianLocale(): Boolean {
        val lang = java.util.Locale.getDefault().language.lowercase()
        return lang == "id" || lang == "in"
    }

    // Curated catalog of ML Kit On-Device Translate supported languages
    val TARGET_LANGUAGES: List<LanguageOption> = listOf(
        LanguageOption("id", "Indonesian", "Bahasa Indonesia", "ID"),
        LanguageOption("en", "English", "Bahasa Inggris", "EN"),
        LanguageOption("ja", "Japanese", "Bahasa Jepang", "JA"),
        LanguageOption("ko", "Korean", "Bahasa Korea", "KO"),
        LanguageOption("zh", "Chinese", "Bahasa Mandarin", "ZH"),
        LanguageOption("ar", "Arabic", "Bahasa Arab", "AR"),
        LanguageOption("ms", "Malay", "Bahasa Melayu", "MS"),
        LanguageOption("th", "Thai", "Bahasa Thailand", "TH"),
        LanguageOption("vi", "Vietnamese", "Bahasa Vietnam", "VI"),
        LanguageOption("tl", "Filipino / Tagalog", "Filipino / Tagalog", "TL"),
        LanguageOption("fr", "French", "Bahasa Prancis", "FR"),
        LanguageOption("de", "German", "Bahasa Jerman", "DE"),
        LanguageOption("es", "Spanish", "Bahasa Spanyol", "ES"),
        LanguageOption("pt", "Portuguese", "Bahasa Portugis", "PT"),
        LanguageOption("ru", "Russian", "Bahasa Rusia", "RU"),
        LanguageOption("it", "Italian", "Bahasa Italia", "IT"),
        LanguageOption("nl", "Dutch", "Bahasa Belanda", "NL"),
        LanguageOption("tr", "Turkish", "Bahasa Turki", "TR"),
        LanguageOption("hi", "Hindi", "Bahasa Hindi", "HI"),
        LanguageOption("bn", "Bengali", "Bahasa Bengali", "BN"),
        LanguageOption("ur", "Urdu", "Bahasa Urdu", "UR"),
        LanguageOption("fa", "Persian", "Bahasa Persia", "FA"),
        LanguageOption("pl", "Polish", "Bahasa Polandia", "PL"),
        LanguageOption("uk", "Ukrainian", "Bahasa Ukraina", "UK"),
        LanguageOption("sv", "Swedish", "Bahasa Swedia", "SV"),
        LanguageOption("el", "Greek", "Bahasa Yunani", "EL"),
        LanguageOption("cs", "Czech", "Bahasa Ceko", "CS"),
        LanguageOption("ro", "Romanian", "Bahasa Rumania", "RO"),
        LanguageOption("hu", "Hungarian", "Bahasa Hungaria", "HU"),
        LanguageOption("ta", "Tamil", "Bahasa Tamil", "TA"),
        LanguageOption("he", "Hebrew", "Bahasa Ibrani", "HE")
    )

    val SOURCE_LANGUAGES: List<LanguageOption> =
        listOf(LanguageOption(AUTO_CODE, "Auto-Detect", "Deteksi Otomatis", "AUTO")) + TARGET_LANGUAGES

    fun displayNameFor(code: String): String {
        return SOURCE_LANGUAGES.firstOrNull { it.code.equals(code, ignoreCase = true) }?.displayName
            ?: code.uppercase()
    }

    fun shortLabelFor(code: String): String {
        return SOURCE_LANGUAGES.firstOrNull { it.code.equals(code, ignoreCase = true) }?.shortLabel
            ?: code.uppercase()
    }

    fun swap(
        currentSourceCode: String,
        currentTargetCode: String,
        lastDetectedSourceCode: String? = null
    ): Pair<String, String> {
        val normSource = currentSourceCode.lowercase()
        val normTarget = currentTargetCode.lowercase()

        if (normSource != AUTO_CODE && normSource.isNotBlank()) {
            return if (normSource == normTarget) {
                val fallbackTarget = if (normTarget == "id") "en" else "id"
                normTarget to fallbackTarget
            } else {
                normTarget to normSource
            }
        }

        val detected = lastDetectedSourceCode?.lowercase()?.takeIf {
            it.isNotBlank() && it != AUTO_CODE && it != "und" && it != normTarget
        }
        val newTarget = detected ?: if (normTarget == "id") "en" else "id"
        return normTarget to newTarget
    }
}
