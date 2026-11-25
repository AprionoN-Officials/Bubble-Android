package com.bubble.screentools.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bubble.screentools.service.BubbleAccessibilityService
import com.bubble.screentools.service.MediaProjectionService

/**
 * Transparent consent launcher used ONLY on legacy Android 7–10 devices when
 * AccessibilityService.takeScreenshot() is unavailable and Default Assistant mode isn't used.
 */
class MediaProjectionConsentActivity : AppCompatActivity() {

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // Passes token into MediaProjectionService so startForeground() finishes BEFORE getMediaProjection()
            MediaProjectionService.startWithGrant(this, result.resultCode, result.data!!)
        } else {
            BubbleAccessibilityService.instance?.onMediaProjectionDenied()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(manager.createScreenCaptureIntent())
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, MediaProjectionConsentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
