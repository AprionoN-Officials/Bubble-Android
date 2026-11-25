package com.bubble.screentools.util

import android.content.Context
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.lens.TemporaryImageProvider
import com.bubble.screentools.translation.TranslationManager

class PerformanceManager(private val preferences: BubblePreferences) {

    fun getMaxOcrDimensionPx(): Int {
        return when {
            preferences.lowRamMode -> 1080
            preferences.batterySavingMode -> 1440
            else -> 1800
        }
    }

    /** Comfortable pause after lifting finger before translating so user can adjust handles */
    fun getTranslateDebounceMs(): Long = 650L

    /** Longer pause before launching external Visual Search (Google Lens / Yandex / TinEye) */
    fun getVisualSearchDebounceMs(): Long = 1800L

    companion object {
        fun clearVolatileCaches(context: Context) {
            TranslationManager.clearMemoryCache()
            TemporaryImageProvider.cleanupCache(context, forceDeleteAll = true)
        }
    }
}
