package com.bubble.screentools.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.service.BubbleAccessibilityService
import com.bubble.screentools.service.MediaProjectionService
import com.bubble.screentools.ui.theme.BubbleExpressiveTheme
import com.bubble.screentools.util.CompatibilityHelper

/**
 * Mode A — Single-scroll Material You Expressive Dashboard & Settings.
 * Extends AppCompatActivity so per-app LocaleHelper works across Android 7–15.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var preferences: BubblePreferences
    private lateinit var uiState: BubbleUiState
    private var resumeTick by mutableIntStateOf(0)

    private val projectionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                MediaProjectionService.startWithGrant(this, result.resultCode, result.data!!)
            } else {
                Toast.makeText(this, getString(R.string.overlay_capture_denied), Toast.LENGTH_SHORT).show()
            }
            resumeTick++
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferences = BubblePreferences(this)
        uiState = BubbleUiState(this, preferences)

        setContent {
            val systemDark = isSystemInDarkTheme()
            val darkTheme =
                when (uiState.themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> systemDark
                }

            LaunchedEffect(resumeTick) {
                uiState.syncPreferencesAndStatus()
                uiState.refreshDownloadedModels()
            }

            BubbleExpressiveTheme(
                darkTheme = darkTheme,
                pureBlack = uiState.pureBlack,
                palette = uiState.themePalette,
            ) {
                BubbleDashboardScreen(
                    state = uiState,
                    darkTheme = darkTheme,
                    onTryBubble = { triggerBubbleNow() },
                    onOpenAssistantSettings = { openAssistantSettings() },
                    onOpenAccessibilitySettings = {
                        startActivity(CompatibilityHelper.createAccessibilitySettingsIntent())
                    },
                    onOpenOverlayPermission = {
                        startActivity(CompatibilityHelper.createOverlayPermissionIntent(this))
                    },
                    onRequestLegacyCapture = {
                        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        projectionLauncher.launch(mpm.createScreenCaptureIntent())
                    },
                    onOpenLanguageManager = { initialTab ->
                        startActivity(
                            Intent(this, LanguageManagerActivity::class.java)
                                .putExtra(LanguageManagerActivity.EXTRA_INITIAL_TAB, initialTab),
                        )
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    private fun triggerBubbleNow() {
        val svc = BubbleAccessibilityService.instance
        if (svc == null) {
            Toast.makeText(
                this,
                getString(R.string.toast_enable_a11y_or_assistant),
                Toast.LENGTH_LONG,
            ).show()
            startActivity(CompatibilityHelper.createAccessibilitySettingsIntent())
        } else {
            moveTaskToBack(true)
            svc.triggerOverlayWithDelay(380L)
        }
    }

    private fun openAssistantSettings() {
        runCatching {
            startActivity(CompatibilityHelper.createVoiceInputSettingsIntent())
        }.onFailure {
            Toast.makeText(
                this,
                getString(R.string.toast_open_assistant_manual),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
