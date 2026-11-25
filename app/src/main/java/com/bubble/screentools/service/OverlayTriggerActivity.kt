package com.bubble.screentools.service

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.bubble.screentools.ui.main.MainActivity

/**
 * Transparent trampoline Activity used by:
 * 1. BubbleTileService via startActivityAndCollapse() to safely collapse Quick Settings on Android 12-15.
 * 2. ACTION_ASSIST fallback on OEMs that invoke an assist Activity directly.
 */
class OverlayTriggerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        val service = BubbleAccessibilityService.instance
        if (service != null) {
            // Wait briefly for shade collapse / window transition before taking silent screenshot
            service.triggerOverlayWithDelay(320L)
        } else {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        finish()
    }
}
