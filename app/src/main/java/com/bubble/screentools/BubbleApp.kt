package com.bubble.screentools

import android.app.Application
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.lens.TemporaryImageProvider
import com.bubble.screentools.theme.ThemeManager
import com.bubble.screentools.util.LocaleHelper
import com.bubble.screentools.util.PerformanceManager

class BubbleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = BubblePreferences(this)
        // Apply user's saved App Interface Language (System / English / Indonesian)
        LocaleHelper.applyAppLocale(prefs.appLanguageCode)
        // Apply user's saved Theme Mode (Follow System / Light / Dark / AMOLED)
        ThemeManager.applyNightMode(prefs.themeConfig.themeMode)
        // Record initial usage timestamp once for 72h donation reminder baseline (never triggers dialog on launch)
        if (prefs.donationFirstUseTimestampMs <= 0L) {
            prefs.donationFirstUseTimestampMs = System.currentTimeMillis()
        }
        // Clean stale temporary Lens & update caches on startup (privacy-first)
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
