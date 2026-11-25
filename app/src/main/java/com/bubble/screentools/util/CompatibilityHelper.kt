package com.bubble.screentools.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowInsetsControllerCompat
import com.bubble.screentools.service.BubbleVoiceInteractionService

object CompatibilityHelper {

    fun applySystemBarAppearance(window: Window, context: Context) {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isNight
        controller.isAppearanceLightNavigationBars = !isNight
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun createOverlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun createAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun createVoiceInputSettingsIntent(): Intent {
        return Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun isDefaultDigitalAssistant(context: Context): Boolean {
        val component = ComponentName(context, BubbleVoiceInteractionService::class.java)
        return runCatching {
            VoiceInteractionService.isActiveService(context, component)
        }.getOrElse {
            val current = runCatching {
                Settings.Secure.getString(context.contentResolver, "assistant")
            }.getOrNull()
            current?.contains(context.packageName) == true
        }
    }

    /**
     * Android 11+ (API 30+) supports AccessibilityService.takeScreenshot() with ZERO
     * MediaProjection "Start recording" dialog.
     */
    fun supportsAccessibilityScreenshot(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun getOverlayWindowType(fromAccessibilityService: Boolean = false): Int {
        if (fromAccessibilityService) {
            return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    fun supportsQuickSettingsTile(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    fun requiresForegroundMediaProjectionService(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun requiresSingleUseMediaProjectionGrant(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE // Android 14+
}
