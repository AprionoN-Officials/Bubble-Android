package com.bubble.screentools.ui.main

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.ocr.TesseractOcrManager
import com.bubble.screentools.translation.LanguageDetectionManager
import com.bubble.screentools.translation.SupportedLanguages
import com.bubble.screentools.translation.TranslationManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

/**
 * Bilingual (EN/ID) Material 3 Manager for Offline ML Kit Translation Models
 * and Custom Tesseract 5 OCR `.traineddata` Import & Quick Download.
 */
class LanguageManagerActivity : AppCompatActivity() {

    private lateinit var preferences: BubblePreferences
    private lateinit var translationManager: TranslationManager
    private lateinit var contentContainer: LinearLayout
    private lateinit var tabLayout: TabLayout

    private var downloadedMlKitModels: Set<String> = emptySet()
    private var busyCodes: MutableSet<String> = mutableSetOf()
    private var searchQuery: String = ""
    private var selectedTab: Int = 0

    private val importTrainedDataLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            Toast.makeText(this@LanguageManagerActivity, getString(R.string.toast_importing_ocr), Toast.LENGTH_SHORT).show()
            val result = TesseractOcrManager.importFromUri(this@LanguageManagerActivity, uri)
            result.onSuccess { code ->
                preferences.activeTesseractLang = code
                preferences.useTesseractOcr = true
                Toast.makeText(
                    this@LanguageManagerActivity,
                    getString(R.string.toast_imported_ocr_ok, code),
                    Toast.LENGTH_LONG
                ).show()
                renderCurrentTab()
            }.onFailure { err ->
                Toast.makeText(
                    this@LanguageManagerActivity,
                    err.message ?: "Import failed.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        com.bubble.screentools.util.CompatibilityHelper.applySystemBarAppearance(window, this)

        preferences = BubblePreferences(this)
        translationManager = TranslationManager(LanguageDetectionManager())
        selectedTab = intent.getIntExtra(EXTRA_INITIAL_TAB, 0)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.lang_manager_title)
            setNavigationIcon(android.R.drawable.ic_menu_revert)
            setNavigationOnClickListener { finish() }
        }
        root.addView(toolbar)

        tabLayout = TabLayout(this).apply {
            addTab(newTab().setText(getString(R.string.tab_offline_translation)))
            addTab(newTab().setText(getString(R.string.tab_custom_ocr)))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    selectedTab = tab.position
                    renderCurrentTab()
                }
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }
        root.addView(tabLayout)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
        }
        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, dp(12), pad, dp(24))
        }
        scroll.addView(contentContainer)
        root.addView(scroll)

        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        tabLayout.getTabAt(selectedTab)?.select()
        refreshMlKitModels()
    }

    override fun onDestroy() {
        translationManager.release()
        super.onDestroy()
    }

    private fun refreshMlKitModels() {
        lifecycleScope.launch {
            downloadedMlKitModels = translationManager.getDownloadedModels()
            renderCurrentTab()
        }
    }

    private fun renderCurrentTab() {
        contentContainer.removeAllViews()
        if (selectedTab == 0) {
            renderMlKitLanguagesTab()
        } else {
            renderTesseractModelsTab()
        }
    }

    private fun renderMlKitLanguagesTab() {
        val info = TextView(this).apply {
            text = getString(R.string.lang_manager_info)
            textSize = 13f
            alpha = 0.78f
            setPadding(0, 0, 0, dp(12))
        }
        contentContainer.addView(info)

        val searchBox = EditText(this).apply {
            hint = getString(R.string.lang_search_hint)
            setText(searchQuery)
            setSelection(searchQuery.length)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val next = s?.toString()?.trim().orEmpty()
                    if (next != searchQuery) {
                        searchQuery = next
                        renderCurrentTab()
                    }
                }
            })
        }
        contentContainer.addView(searchBox)

        val filtered = SupportedLanguages.TARGET_LANGUAGES.filter {
            searchQuery.isBlank() ||
                it.displayName.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true)
        }

        for (lang in filtered) {
            val isDownloaded = downloadedMlKitModels.contains(lang.code)
            val isBusy = busyCodes.contains(lang.code)

            val card = createCard()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = lang.displayName
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
            })
            textCol.addView(TextView(this).apply {
                text = when {
                    isBusy -> getString(R.string.status_processing)
                    isDownloaded -> getString(R.string.status_downloaded_offline)
                    else -> getString(R.string.status_not_downloaded_fmt, lang.shortLabel)
                }
                textSize = 12f
                alpha = 0.72f
            })
            row.addView(textCol)

            val actionBtn = if (isDownloaded) {
                MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = if (isBusy) "..." else getString(R.string.btn_delete)
                    isEnabled = !isBusy
                    setOnClickListener {
                        busyCodes.add(lang.code)
                        renderCurrentTab()
                        lifecycleScope.launch {
                            val ok = translationManager.deleteLanguageModel(lang.code)
                            busyCodes.remove(lang.code)
                            Toast.makeText(
                                this@LanguageManagerActivity,
                                if (ok) getString(R.string.toast_lang_deleted, lang.displayName)
                                else getString(R.string.toast_lang_delete_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                            refreshMlKitModels()
                        }
                    }
                }
            } else {
                MaterialButton(this).apply {
                    text = if (isBusy) getString(R.string.btn_downloading) else getString(R.string.btn_download)
                    isEnabled = !isBusy
                    setOnClickListener {
                        busyCodes.add(lang.code)
                        renderCurrentTab()
                        lifecycleScope.launch {
                            val ok = translationManager.downloadLanguageModel(lang.code)
                            busyCodes.remove(lang.code)
                            Toast.makeText(
                                this@LanguageManagerActivity,
                                if (ok) getString(R.string.toast_lang_downloaded, lang.displayName)
                                else getString(R.string.toast_lang_download_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                            refreshMlKitModels()
                        }
                    }
                }
            }
            row.addView(actionBtn)
            card.addView(row)
            contentContainer.addView(card)
        }
    }

    private fun renderTesseractModelsTab() {
        val installed = TesseractOcrManager.listInstalledModels(this)

        val headerCard = createCard()
        val headerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        headerBox.addView(TextView(this).apply {
            text = getString(R.string.ocr_card_title)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        })
        headerBox.addView(TextView(this).apply {
            text = getString(R.string.ocr_card_desc)
            textSize = 12.5f
            alpha = 0.78f
            setPadding(0, dp(4), 0, dp(10))
        })

        val swEnable = MaterialSwitch(this).apply {
            text = getString(R.string.switch_use_tesseract)
            isChecked = preferences.useTesseractOcr
            setOnCheckedChangeListener { _, checked ->
                if (checked && installed.isEmpty()) {
                    isChecked = false
                    Toast.makeText(
                        this@LanguageManagerActivity,
                        getString(R.string.toast_need_one_ocr_model),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    preferences.useTesseractOcr = checked
                    if (checked && preferences.activeTesseractLang.isBlank() && installed.isNotEmpty()) {
                        preferences.activeTesseractLang = installed.first().code
                    }
                    renderCurrentTab()
                }
            }
        }
        headerBox.addView(swEnable)

        val importBtn = MaterialButton(this).apply {
            text = getString(R.string.btn_import_traineddata)
            setOnClickListener {
                importTrainedDataLauncher.launch(arrayOf("*/*"))
            }
        }
        headerBox.addView(importBtn)
        headerCard.addView(headerBox)
        contentContainer.addView(headerCard)

        contentContainer.addView(TextView(this).apply {
            text = getString(R.string.ocr_installed_header_fmt, installed.size)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(8))
        })

        if (installed.isEmpty()) {
            contentContainer.addView(TextView(this).apply {
                text = getString(R.string.ocr_none_installed)
                textSize = 13f
                alpha = 0.7f
                setPadding(0, 0, 0, dp(12))
            })
        } else {
            for (model in installed) {
                val isActive = preferences.activeTesseractLang == model.code && preferences.useTesseractOcr
                val sizeMb = String.format("%.1f MB", model.sizeBytes / (1024.0 * 1024.0))

                val card = createCard()
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                }
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                col.addView(TextView(this).apply {
                    text = if (isActive) getString(R.string.ocr_active_label_fmt, model.code) else model.code
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                })
                col.addView(TextView(this).apply {
                    text = "${model.fileName} • $sizeMb"
                    textSize = 12f
                    alpha = 0.72f
                })
                row.addView(col)

                if (!isActive) {
                    row.addView(MaterialButton(this).apply {
                        text = getString(R.string.btn_use)
                        setOnClickListener {
                            preferences.activeTesseractLang = model.code
                            preferences.useTesseractOcr = true
                            Toast.makeText(
                                this@LanguageManagerActivity,
                                getString(R.string.toast_ocr_active_fmt, model.code),
                                Toast.LENGTH_SHORT
                            ).show()
                            renderCurrentTab()
                        }
                    })
                }
                row.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = getString(R.string.btn_delete)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dp(8) }
                    setOnClickListener {
                        AlertDialog.Builder(this@LanguageManagerActivity)
                            .setTitle(getString(R.string.dialog_delete_ocr_title))
                            .setMessage(getString(R.string.dialog_delete_ocr_msg, model.fileName))
                            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                                TesseractOcrManager.deleteModel(this@LanguageManagerActivity, model.code)
                                if (preferences.activeTesseractLang == model.code) {
                                    val remaining = TesseractOcrManager.listInstalledModels(this@LanguageManagerActivity)
                                    preferences.activeTesseractLang = remaining.firstOrNull()?.code.orEmpty()
                                    if (remaining.isEmpty()) preferences.useTesseractOcr = false
                                }
                                renderCurrentTab()
                            }
                            .setNegativeButton(getString(R.string.btn_cancel), null)
                            .show()
                    }
                })
                card.addView(row)
                contentContainer.addView(card)
            }
        }

        contentContainer.addView(TextView(this).apply {
            text = getString(R.string.ocr_quick_download_header)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(8))
        })

        val installedCodes = installed.map { it.code }.toSet()
        for (item in TesseractOcrManager.QUICK_DOWNLOAD_MODELS) {
            val already = installedCodes.contains(item.code)
            val isBusy = busyCodes.contains("tess_${item.code}")
            val card = createCard()
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
            }
            row.addView(TextView(this).apply {
                text = item.label
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = when {
                    isBusy -> getString(R.string.btn_downloading)
                    already -> getString(R.string.btn_installed)
                    else -> getString(R.string.btn_download)
                }
                isEnabled = !already && !isBusy
                setOnClickListener {
                    busyCodes.add("tess_${item.code}")
                    renderCurrentTab()
                    lifecycleScope.launch {
                        val res = TesseractOcrManager.downloadOfficialModel(this@LanguageManagerActivity, item.code)
                        busyCodes.remove("tess_${item.code}")
                        res.onSuccess { code ->
                            if (preferences.activeTesseractLang.isBlank()) {
                                preferences.activeTesseractLang = code
                                preferences.useTesseractOcr = true
                            }
                            Toast.makeText(
                                this@LanguageManagerActivity,
                                getString(R.string.toast_ocr_download_ok, code),
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure { err ->
                            Toast.makeText(
                                this@LanguageManagerActivity,
                                err.message ?: getString(R.string.toast_lang_download_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        renderCurrentTab()
                    }
                }
            })
            card.addView(row)
            contentContainer.addView(card)
        }
    }

    private fun createCard(): MaterialCardView {
        return MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            strokeWidth = dp(1)
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"
    }
}
