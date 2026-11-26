package com.bubble.screentools.ui.main

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.ocr.TesseractOcrManager
import com.bubble.screentools.ui.theme.BubbleExpressiveTheme
import kotlinx.coroutines.launch

/**
 * Offline ML Kit Translation Models & Custom Tesseract 5 OCR Model Manager.
 */
class LanguageManagerActivity : AppCompatActivity() {

    private lateinit var preferences: BubblePreferences
    private lateinit var uiState: BubbleUiState

    private val importTrainedDataLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                Toast.makeText(this@LanguageManagerActivity, getString(R.string.toast_importing_ocr), Toast.LENGTH_SHORT).show()
                val result = TesseractOcrManager.importFromUri(this@LanguageManagerActivity, uri)
                result
                    .onSuccess { code ->
                        uiState.activateTesseractModel(code)
                        Toast.makeText(
                            this@LanguageManagerActivity,
                            getString(R.string.toast_imported_ocr_ok, code),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    .onFailure { err ->
                        Toast.makeText(
                            this@LanguageManagerActivity,
                            err.message ?: "Import failed.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferences = BubblePreferences(this)
        uiState = BubbleUiState(this, preferences)
        val initialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, 0)

        setContent {
            val systemDark = isSystemInDarkTheme()
            val darkTheme =
                when (uiState.themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> systemDark
                }

            LaunchedEffect(Unit) {
                uiState.syncPreferencesAndStatus()
                uiState.refreshDownloadedModels()
            }

            BubbleExpressiveTheme(
                darkTheme = darkTheme,
                pureBlack = uiState.pureBlack,
                palette = uiState.themePalette,
            ) {
                ModelsScreen(
                    state = uiState,
                    initialTab = initialTab,
                    onImportTrainedData = { importTrainedDataLauncher.launch(arrayOf("*/*")) },
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"
    }
}
