package com.bubble.screentools.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Bilingual App Language helper (System / English / Indonesian).
 * Applies locale to both AppCompatActivity screens (via AppCompatDelegate)
 * and Service/Overlay contexts (via createConfigurationContext).
 */
object LocaleHelper {

    const val LANG_SYSTEM = "system"
    const val LANG_ENGLISH = "en"
    const val LANG_INDONESIAN = "in"

    fun resolveLanguageTag(langCode: String): String = when (langCode) {
        LANG_ENGLISH -> "en"
        LANG_INDONESIAN -> "id"
        else -> ""
    }

    fun applyAppLocale(langCode: String) {
        val tag = resolveLanguageTag(langCode)
        val locales = if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun wrapContext(base: Context, langCode: String): Context {
        if (langCode == LANG_SYSTEM || langCode.isBlank()) return base
        val locale = when (langCode) {
            LANG_ENGLISH -> Locale.ENGLISH
            LANG_INDONESIAN -> Locale("in", "ID")
            else -> return base
        }
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
