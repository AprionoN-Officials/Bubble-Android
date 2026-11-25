package com.bubble.screentools.security

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityWindowInfo
import com.bubble.screentools.data.BubblePreferences

/**
 * Bubble Safe Mode: Fail-safe, privacy-first protection for sensitive apps.
 * Never attempts to bypass FLAG_SECURE, hide from banking security checks,
 * or inspect passwords/PINs/OTPs.
 */
class SafeModeManager(private val preferences: BubblePreferences) {

    @Volatile
    var currentForegroundPackage: String? = null
        private set

    fun updateForegroundPackage(packageName: String?) {
        if (!packageName.isNullOrBlank()) {
            currentForegroundPackage = packageName
        }
    }

    /**
     * Conservative check: if Safe Mode is enabled and foreground package is either
     * unknown or in the user's excluded list, block scanning & overlay activation.
     */
    fun evaluateActiveApp(packageName: String? = currentForegroundPackage): SafeModeDecision {
        if (!preferences.safeModeEnabled) {
            return SafeModeDecision.Allowed
        }

        val isId = java.util.Locale.getDefault().language.lowercase().let { it == "id" || it == "in" }
        if (packageName.isNullOrBlank()) {
            return SafeModeDecision.Blocked(
                packageName = "unknown",
                reason = if (isId) {
                    "Status aplikasi aktif tidak dapat dipastikan; pemindaian ditahan demi keamanan."
                } else {
                    "Active app could not be verified; scanning paused for security."
                }
            )
        }

        if (preferences.excludedPackages.contains(packageName)) {
            preferences.lastBlockedPackageName = packageName
            return SafeModeDecision.Blocked(
                packageName = packageName,
                reason = if (isId) {
                    "Bubble dinonaktifkan pada aplikasi ini untuk melindungi privasi dan mencegah gangguan terhadap fitur keamanan."
                } else {
                    "Bubble is disabled on this app to protect privacy and avoid interfering with security features."
                }
            )
        }

        return SafeModeDecision.Allowed
    }

    /**
     * Inspect AccessibilityWindowInfo flags where supported to avoid interacting with
     * protected/secure layers or soft keyboards (IME).
     */
    fun isProtectedWindowPresent(service: AccessibilityService?): Boolean {
        if (service == null) return true
        val windows = runCatching { service.windows }.getOrNull() ?: return false
        for (window in windows) {
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                continue
            }
            val root = runCatching { window.root }.getOrNull()
            val pkg = root?.packageName?.toString()
            if (pkg != null && preferences.safeModeEnabled && preferences.excludedPackages.contains(pkg)) {
                return true
            }
        }
        return false
    }

    sealed class SafeModeDecision {
        data object Allowed : SafeModeDecision()
        data class Blocked(val packageName: String, val reason: String) : SafeModeDecision()
    }
}
