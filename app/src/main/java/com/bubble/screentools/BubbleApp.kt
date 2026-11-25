package com.bubble.screentools

import android.app.Application
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.lens.TemporaryImageProvider
import com.bubble.screentools.util.LocaleHelper
import com.bubble.screentools.util.PerformanceManager

class BubbleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Apply user's chosen App Interface Language (System / English / Indonesian)
        val prefs = BubblePreferences(this)
        LocaleHelper.applyAppLocale(prefs.appLanguageCode)

        // Clean stale temporary Lens cache on startup (privacy-first)
        TemporaryImageProvider.cleanupCache(this, forceDeleteAll = true)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            PerformanceManager.clearVolatileCaches(this)
        }
    }
}
