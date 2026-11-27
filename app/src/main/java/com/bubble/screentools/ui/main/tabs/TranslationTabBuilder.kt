package com.bubble.screentools.ui.main.tabs

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.model.TranslationContextProfile
import com.bubble.screentools.model.TranslationOverlayStyle
import com.bubble.screentools.model.TranslationQualityMode
import com.bubble.screentools.ocr.TesseractOcrManager
import com.bubble.screentools.theme.ThemePaletteGenerator
import com.bubble.screentools.translation.SupportedLanguages
import com.bubble.screentools.translation.TranslationContextProcessor
import com.bubble.screentools.translation.TranslationManager
import com.bubble.screentools.ui.main.UiKit
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Tab 1 — Translation / Terjemahan:
 * - Language pair picker, Vector Swap icon, Auto-Detect, Recent & Preferred Source & Fallback languages
 * - Translation Quality Modes (Fast / Accurate / Natural) & Context Profiles (General / Literal / Natural / Manga-Game)
 * - Separated Offline Models & Custom OCR (.traineddata) Imports + Unused Model Cleanup
 * - Honest Live Pipeline Preview (shows real Context Reconstruction preprocessing output)
 */
object TranslationTabBuilder {

    interface Callbacks {
        fun onChanged()
        fun onOpenOfflineLanguageManager(initialTab: Int)
        fun onDeleteUnusedOfflineModels()
    }

    fun render(
        context: Context,
        container: LinearLayout,
        preferences: BubblePreferences,
        palette: ThemePaletteGenerator.BubblePalette,
        offlineModels: Set<String>,
        callbacks: Callbacks
    ) {
        container.removeAllViews()
        val dp = { v: Float -> UiKit.dp(context, v) }

        // 1. Language Pair & Controls Card
        val (langCard, langBox) = UiKit.createExpressiveCard(context, palette)
        langBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_translation), palette))

        val effectiveSource = if (preferences.autoDetectLanguage) SupportedLanguages.AUTO_CODE else preferences.sourceLanguageCode
        val pairRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        pairRow.addView(
            UiKit.createPillButton(
                context,
                context.getString(R.string.btn_source_lang_fmt, SupportedLanguages.shortLabelFor(effectiveSource)),
                UiKit.ButtonVariant.OUTLINED,
                palette
            ) {
                val options = SupportedLanguages.SOURCE_LANGUAGES
                MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.dialog_pick_source_lang))
                    .setItems(options.map { it.displayName }.toTypedArray()) { _, idx ->
                        val chosen = options[idx].code
                        preferences.sourceLanguageCode = chosen
                        preferences.autoDetectLanguage = (chosen == SupportedLanguages.AUTO_CODE)
                        callbacks.onChanged()
                    }
                    .show()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        pairRow.addView(
            UiKit.createIconPillButton(
                context = context,
                iconRes = R.drawable.ic_swap_horiz,
                contentDescriptionText = context.getString(R.string.overlay_swap_cd),
                variant = UiKit.ButtonVariant.PRIMARY,
                palette = palette
            ) {
                val (newSrc, newTgt) = SupportedLanguages.swap(effectiveSource, preferences.targetLanguageCode)
                preferences.sourceLanguageCode = newSrc
                preferences.targetLanguageCode = newTgt
                preferences.autoDetectLanguage = (newSrc == SupportedLanguages.AUTO_CODE)
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.toast_swapped_fmt,
                        SupportedLanguages.shortLabelFor(newSrc),
                        SupportedLanguages.shortLabelFor(newTgt)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                callbacks.onChanged()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(8f)
                    marginEnd = dp(8f)
                }
            }
        )
        pairRow.addView(
            UiKit.createPillButton(
                context,
                context.getString(R.string.btn_target_lang_fmt, SupportedLanguages.shortLabelFor(preferences.targetLanguageCode)),
                UiKit.ButtonVariant.OUTLINED,
                palette
            ) {
                val options = SupportedLanguages.TARGET_LANGUAGES
                MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.dialog_pick_target_lang))
                    .setItems(options.map { it.displayName }.toTypedArray()) { _, idx ->
                        preferences.targetLanguageCode = options[idx].code
                        callbacks.onChanged()
                    }
                    .show()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        langBox.addView(pairRow)

        langBox.addView(TextView(context).apply {
            text = context.getString(
                R.string.active_lang_pair_fmt,
                SupportedLanguages.displayNameFor(effectiveSource),
                SupportedLanguages.displayNameFor(preferences.targetLanguageCode)
            )
            textSize = 12.5f
            setTextColor(palette.onSurfaceVariant)
            setPadding(0, dp(8f), 0, dp(4f))
        })

        // Recently Used Languages quick chips
        val recents = preferences.recentLanguageCodes
        if (recents.isNotEmpty()) {
            langBox.addView(UiKit.createLabel(context, context.getString(R.string.label_recent_langs), palette))
            val recentScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
            val recentGroup = ChipGroup(context).apply { isSingleLine = true }
            for (code in recents) {
                recentGroup.addView(Chip(context).apply {
                    text = "${SupportedLanguages.shortLabelFor(code)} • ${SupportedLanguages.displayNameFor(code)}"
                    setOnClickListener {
                        preferences.targetLanguageCode = code
                        callbacks.onChanged()
                    }
                })
            }
            recentScroll.addView(recentGroup)
            langBox.addView(recentScroll)
        }

        // Preferred Source Languages & Fallback Language
        langBox.addView(UiKit.createLabel(context, context.getString(R.string.label_preferred_sources), palette))
        val prefScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val prefGroup = ChipGroup(context).apply { isSingleLine = true }
        val currentPreferred = preferences.preferredSourceLanguages.toMutableSet()
        val quickCandidates = listOf("en", "ja", "ko", "zh", "id", "ar", "fr", "de", "es")
        for (code in quickCandidates) {
            prefGroup.addView(Chip(context).apply {
                text = SupportedLanguages.shortLabelFor(code)
                isCheckable = true
                isChecked = currentPreferred.contains(code)
                setOnCheckedChangeListener { _, checked ->
                    val set = preferences.preferredSourceLanguages.toMutableSet()
                    if (checked) set.add(code) else set.remove(code)
                    preferences.preferredSourceLanguages = set.toList()
                }
            })
        }
        prefScroll.addView(prefGroup)
        langBox.addView(prefScroll)

        langBox.addView(
            UiKit.createPillButton(
                context,
                context.getString(
                    R.string.btn_fallback_lang_fmt,
                    SupportedLanguages.displayNameFor(preferences.fallbackLanguageCode)
                ),
                UiKit.ButtonVariant.TEXT,
                palette
            ) {
                val options = SupportedLanguages.TARGET_LANGUAGES
                MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.dialog_pick_fallback_lang))
                    .setItems(options.map { it.displayName }.toTypedArray()) { _, idx ->
                        preferences.fallbackLanguageCode = options[idx].code
                        callbacks.onChanged()
                    }
                    .show()
            }
        )

        langBox.addView(UiKit.createDivider(context, palette))
        langBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_auto_detect), preferences.autoDetectLanguage, palette) { checked ->
            preferences.autoDetectLanguage = checked
            if (checked) {
                preferences.sourceLanguageCode = SupportedLanguages.AUTO_CODE
            } else if (preferences.sourceLanguageCode == SupportedLanguages.AUTO_CODE) {
                preferences.sourceLanguageCode = if (preferences.targetLanguageCode == "id") "en" else "id"
            }
            callbacks.onChanged()
        })
        langBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_auto_translate), preferences.autoTranslateSelection, palette) {
            preferences.autoTranslateSelection = it
        })
        langBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_translate_entire_screen), preferences.translateEntireScreen, palette) {
            preferences.translateEntireScreen = it
        })

        langBox.addView(UiKit.createLabel(context, context.getString(R.string.label_overlay_style), palette))
        langBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    TranslationOverlayStyle.ADAPTIVE_COVER to context.getString(R.string.overlay_style_adaptive),
                    TranslationOverlayStyle.SEMI_TRANSPARENT_BOX to context.getString(R.string.overlay_style_semi)
                ),
                selected = preferences.translationOverlayStyle,
                palette = palette
            ) {
                preferences.translationOverlayStyle = it
                callbacks.onChanged()
            }
        )
        container.addView(langCard)

        // 2. Quality Mode & Context Processing Card
        val (qualCard, qualBox) = UiKit.createExpressiveCard(context, palette)
        qualBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_translation_quality), palette))

        val qMode = preferences.translationQualityMode
        qualBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    TranslationQualityMode.FAST to context.getString(R.string.quality_fast),
                    TranslationQualityMode.ACCURATE to context.getString(R.string.quality_accurate),
                    TranslationQualityMode.NATURAL to context.getString(R.string.quality_natural)
                ),
                selected = qMode,
                palette = palette
            ) {
                preferences.translationQualityMode = it
                callbacks.onChanged()
            }
        )
        qualBox.addView(TextView(context).apply {
            text = when (qMode) {
                TranslationQualityMode.FAST -> context.getString(R.string.quality_desc_fast)
                TranslationQualityMode.ACCURATE -> context.getString(R.string.quality_desc_accurate)
                TranslationQualityMode.NATURAL -> context.getString(R.string.quality_desc_natural)
            }
            textSize = 12.5f
            setTextColor(palette.onSurfaceVariant)
            setPadding(0, dp(6f), 0, dp(8f))
        })

        qualBox.addView(UiKit.createLabel(context, context.getString(R.string.label_context_profile), palette))
        qualBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    TranslationContextProfile.GENERAL to context.getString(R.string.context_general),
                    TranslationContextProfile.LITERAL to context.getString(R.string.context_literal),
                    TranslationContextProfile.NATURAL to context.getString(R.string.context_natural),
                    TranslationContextProfile.MANGA_GAME to context.getString(R.string.context_manga_game)
                ),
                selected = preferences.translationContextProfile,
                palette = palette,
                scrollable = true
            ) {
                preferences.translationContextProfile = it
                callbacks.onChanged()
            }
        )
        qualBox.addView(TextView(context).apply {
            text = context.getString(R.string.translation_engine_limitation_note)
            textSize = 11.5f
            setTextColor(palette.onSurfaceVariant)
            setPadding(0, dp(8f), 0, 0)
        })
        container.addView(qualCard)

        // 3. Offline Models & Custom OCR Imports
        val (modelCard, modelBox) = UiKit.createExpressiveCard(context, palette)
        modelBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_models_imports), palette))
        modelBox.addView(UiKit.createSubtitle(context, context.getString(R.string.models_separation_note), palette))

        val tessModels = TesseractOcrManager.listInstalledModels(context)
        val ocrStatusText = if (preferences.useTesseractOcr && preferences.activeTesseractLang.isNotBlank()) {
            context.getString(R.string.ocr_engine_tess_fmt, preferences.activeTesseractLang, tessModels.size)
        } else {
            context.getString(R.string.ocr_engine_mlkit_fmt, tessModels.size)
        }
        modelBox.addView(TextView(context).apply {
            text = "$ocrStatusText\n${context.getString(R.string.offline_storage_summary_fmt, offlineModels.size, offlineModels.size * TranslationManager.ESTIMATED_MLKIT_MODEL_SIZE_MB)}"
            textSize = 12.5f
            setTextColor(palette.onSurface)
            setPadding(0, 0, 0, dp(10f))
        })

        val modelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(8f) }
        }
        modelRow.addView(
            UiKit.createPillButton(context, context.getString(R.string.btn_download_languages), UiKit.ButtonVariant.TONAL, palette) {
                callbacks.onOpenOfflineLanguageManager(0)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6f) }
            }
        )
        modelRow.addView(
            UiKit.createPillButton(context, context.getString(R.string.btn_import_ocr), UiKit.ButtonVariant.OUTLINED, palette) {
                callbacks.onOpenOfflineLanguageManager(1)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6f) }
            }
        )
        modelBox.addView(modelRow)

        modelBox.addView(
            UiKit.createPillButton(context, context.getString(R.string.btn_delete_unused_models), UiKit.ButtonVariant.TEXT, palette) {
                callbacks.onDeleteUnusedOfflineModels()
            }
        )
        container.addView(modelCard)

        // 4. Honest Live Pipeline Preview Card
        val (prevCard, prevBox) = UiKit.createExpressiveCard(context, palette)
        prevBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_translation_preview), palette))
        prevBox.addView(UiKit.createSubtitle(context, context.getString(R.string.translation_preview_badge), palette))

        val sampleBlocks = listOf(
            TranslationContextProcessor.SpatialTextBlock("Critica| Damage increases your", 10f, 10f, 260f, 34f),
            TranslationContextProcessor.SpatialTextBlock("Elemental Mastery by +25% in", 10f, 40f, 260f, 64f),
            TranslationContextProcessor.SpatialTextBlock("multi-line battle scenes.", 10f, 70f, 220f, 94f)
        )
        val processed = TranslationContextProcessor.processBlocks(
            sampleBlocks,
            preferences.translationQualityMode,
            preferences.translationContextProfile
        )
        val previewOut = processed.joinToString("\n---\n") { it.text }

        prevBox.addView(TextView(context).apply {
            text = "${context.getString(R.string.translation_preview_input_label)}\n\"Critica| Damage increases your\\nElemental Mastery by +25% in\\nmulti-line battle scenes.\""
            textSize = 12f
            setTextColor(palette.onSurfaceVariant)
            setPadding(0, 0, 0, dp(8f))
        })
        prevBox.addView(TextView(context).apply {
            text = "${context.getString(R.string.translation_preview_output_label)}\n$previewOut"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.onSurface)
        })
        container.addView(prevCard)
    }
}
