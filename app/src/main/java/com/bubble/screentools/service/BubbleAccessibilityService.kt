package com.bubble.screentools.service

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.bubble.screentools.R
import com.bubble.screentools.capture.ScreenCaptureManager
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.security.SafeModeManager
import com.bubble.screentools.ui.overlay.BubbleOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BubbleAccessibilityService : AccessibilityService() {

    lateinit var preferences: BubblePreferences
        private set
    lateinit var safeModeManager: SafeModeManager
        private set
    lateinit var screenCaptureManager: ScreenCaptureManager
        private set

    private var overlayController: BubbleOverlayController? = null
    private var accessibilityButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val delayedTriggerRunnable = Runnable {
        toggleAssistantOverlay()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        preferences = BubblePreferences(this)
        safeModeManager = SafeModeManager(preferences)
        screenCaptureManager = ScreenCaptureManager(this)
        overlayController = BubbleOverlayController(
            context = this,
            preferences = preferences,
            safeModeManager = safeModeManager,
            screenCaptureManager = screenCaptureManager
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val callback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) {
                    toggleAssistantOverlay()
                }
            }
            accessibilityButtonCallback = callback
            accessibilityButtonController.registerAccessibilityButtonCallback(callback)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            safeModeManager.updateForegroundPackage(pkg)
            if (preferences.stopScanOnExcludedApp) {
                val decision = safeModeManager.evaluateActiveApp(pkg)
                if (decision is SafeModeManager.SafeModeDecision.Blocked && overlayController?.isShowing == true) {
                    overlayController?.haltAllAndDismiss(getString(R.string.safe_mode_blocked_message))
                }
            }
        }
    }

    override fun onInterrupt() {
        mainHandler.removeCallbacks(delayedTriggerRunnable)
        overlayController?.haltAllAndDismiss()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(delayedTriggerRunnable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            accessibilityButtonCallback?.let {
                accessibilityButtonController.unregisterAccessibilityButtonCallback(it)
            }
        }
        overlayController?.haltAllAndDismiss()
        overlayController = null
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }

    /**
     * Triggers the overlay after a brief delay (used after collapsing Quick Settings
     * or moving MainActivity to back so the target app resumes cleanly first).
     */
    fun triggerOverlayWithDelay(delayMs: Long = 320L) {
        mainHandler.removeCallbacks(delayedTriggerRunnable)
        mainHandler.postDelayed(delayedTriggerRunnable, delayMs)
    }

    fun toggleAssistantOverlay() {
        val controller = overlayController ?: return
        if (controller.isShowing) {
            controller.dismiss()
            return
        }

        val activePkg = resolveActiveForegroundPackage()
        safeModeManager.updateForegroundPackage(activePkg)

        val decision = safeModeManager.evaluateActiveApp(activePkg)
        if (decision is SafeModeManager.SafeModeDecision.Blocked) {
            controller.haltAllAndDismiss("Safe Mode: ${decision.reason}")
            return
        }

        if (safeModeManager.isProtectedWindowPresent(this)) {
            controller.haltAllAndDismiss(getString(R.string.safe_mode_blocked_message))
            return
        }

        // Android 11+ (API 30+): silently capture full-screen snapshot BEFORE showing overlay
        // using AccessibilityService.takeScreenshot() — ZERO "Start recording" dialog!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            serviceScope.launch {
                val capture = screenCaptureManager.captureFullScreenViaAccessibility(this@BubbleAccessibilityService)
                when (capture) {
                    is ScreenCaptureManager.FullScreenCaptureResult.Success -> {
                        controller.show(
                            service = this@BubbleAccessibilityService,
                            initialPackage = activePkg,
                            initialSnapshot = capture.bitmap
                        )
                    }
                    ScreenCaptureManager.FullScreenCaptureResult.ProtectedContent -> {
                        controller.haltAllAndDismiss(
                            "Layar dilindungi FLAG_SECURE. Bubble tidak membaca konten terlindungi."
                        )
                    }
                    else -> {
                        // Still open overlay so Accessibility text extraction can work even if snapshot rate-limited
                        controller.show(
                            service = this@BubbleAccessibilityService,
                            initialPackage = activePkg,
                            initialSnapshot = null
                        )
                    }
                }
            }
        } else {
            controller.show(
                service = this,
                initialPackage = activePkg,
                initialSnapshot = null
            )
        }
    }

    private fun resolveActiveForegroundPackage(): String? {
        val rootPkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (!rootPkg.isNullOrBlank() && rootPkg != packageName) {
            return rootPkg
        }

        val windows = runCatching { this.windows }.getOrNull()
        if (windows != null) {
            for (window in windows) {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    val pkg = runCatching { window.root?.packageName?.toString() }.getOrNull()
                    if (!pkg.isNullOrBlank() && pkg != packageName) {
                        return pkg
                    }
                }
            }
        }

        return safeModeManager.currentForegroundPackage
    }

    fun onMediaProjectionGranted(resultCode: Int, data: Intent) {
        if (screenCaptureManager.attachGrant(resultCode, data)) {
            overlayController?.resumePendingAfterCaptureGrant(this)
        } else {
            overlayController?.onCaptureDenied()
        }
    }

    fun onMediaProjectionDenied() {
        overlayController?.onCaptureDenied()
    }

    companion object {
        @Volatile
        var instance: BubbleAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }
}
