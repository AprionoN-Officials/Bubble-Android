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

    /** Configurable pause before launching Visual Search (defaults to 2s, supports 0s–60s) */
    fun getVisualSearchDebounceMs(): Long = preferences.visualSearchDelayMs.coerceIn(0L, 60_000L)

    companion object {
        fun clearVolatileCaches(context: Context) {
            TranslationManager.clearMemoryCache()
            TemporaryImageProvider.cleanupCache(context, forceDeleteAll = true)
        }
    }
}
