package com.bubble.screentools.ui.main.tabs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.lens.LensIntegrationManager
import com.bubble.screentools.lens.SearchEngineRegistry
import com.bubble.screentools.model.ActionMode
import com.bubble.screentools.model.ActionPanelPosition
import com.bubble.screentools.service.BubbleAccessibilityService
import com.bubble.screentools.theme.ThemeManager
import com.bubble.screentools.theme.ThemePaletteGenerator
import com.bubble.screentools.ui.main.UiKit
import com.bubble.screentools.ui.overlay.ActionPanelView
import com.bubble.screentools.util.CompatibilityHelper
import com.bubble.screentools.util.LocaleHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Tab 0 — General / Umum:
 * - Safe Mode Notice Banner (when triggered)
 * - Status & Activation Card (100% real OS checks, Try Bubble Now, Default Assistant, Accessibility, Overlay Permission)
 * - Quick Settings & Core Preferences Card (App Interface Language, Default Action Mode, Auto-Scan QR,
 *   Auto-Detect Links, Safe Mode toggle, Excluded Apps, Quick jumps, Usage Guide)
 * - Live Action Panel Preview Card reflecting the user's current saved Action Panel config
 */
object GeneralTabBuilder {

    interface Callbacks {
        fun onTryBubbleNow()
        fun onOpenDefaultAssistant()
        fun onOpenAccessibility()
        fun onOpenOverlayPermission()
        fun onRequestLegacyCapture()
        fun onNavigateToTab(tabIndex: Int)
        fun onSafeModeToggled(enabled: Boolean)
        fun onOpenExcludedApps()
        fun onAppLanguageChanged(langCode: String)
        fun onGeneralSettingsChanged()
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

        // 1. Safe Mode blocked notice card (shown only when a protected app was actually blocked)
        val lastBlocked = preferences.lastBlockedPackageName
        if (!lastBlocked.isNullOrBlank()) {
            val (noticeCard, noticeBox) = UiKit.createExpressiveCard(context, palette, tonal = true)
            noticeBox.addView(TextView(context).apply {
                text = "${context.getString(R.string.safe_mode_blocked_message)}\n${context.getString(R.string.status_last_blocked_app, lastBlocked)}"
                textSize = 13f
                setTextColor(palette.onPrimaryContainer)
            })
            container.addView(noticeCard)
        }

        // 2. Status & Activation Card (all real device state checks)
        val (statusCard, statusBox) = UiKit.createExpressiveCard(context, palette)
        statusBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_status_activation), palette))

        val isAssistant = CompatibilityHelper.isDefaultDigitalAssistant(context)
        val a11yActive = BubbleAccessibilityService.isRunning()
        val overlayGranted = CompatibilityHelper.isOverlayPermissionGranted(context)
        val silentCapture = CompatibilityHelper.supportsAccessibilityScreenshot()
        val captureText = when {
            isAssistant && a11yActive -> context.getString(R.string.status_capture_both)
            isAssistant -> context.getString(R.string.status_capture_assistant)
            silentCapture && a11yActive -> context.getString(R.string.status_capture_a11y)
            else -> context.getString(R.string.status_capture_enable_hint)
        }
        val lensManager = LensIntegrationManager(context)
        val lensStatus = when (lensManager.checkAvailability()) {
            LensIntegrationManager.LensAvailability.GOOGLE_LENS_APP -> context.getString(R.string.status_lens_ready)
            LensIntegrationManager.LensAvailability.GOOGLE_APP_SEARCH -> context.getString(R.string.status_google_app_ready)
            LensIntegrationManager.LensAvailability.SHARESHEET_FALLBACK -> context.getString(R.string.status_multi_engine_ready)
        }
        val activeEngine = SearchEngineRegistry(preferences).getActiveVisualEngine()
        val modelSummary = if (offlineModels.isEmpty()) {
            context.getString(R.string.status_models_auto)
        } else {
            context.getString(R.string.status_models_count, offlineModels.size, offlineModels.sorted().joinToString(", "))
        }

        val summary = buildString {
            append("${context.getString(R.string.status_assistant_label)}: ${if (isAssistant) context.getString(R.string.status_active_check) else context.getString(R.string.status_not_set)}\n")
            append("${context.getString(R.string.status_accessibility_label)}: ${if (a11yActive) context.getString(R.string.status_active_check) else context.getString(R.string.status_inactive)}\n")
            append("${context.getString(R.string.status_capture_label)}: $captureText\n")
            append("${context.getString(R.string.status_visual_search_label)}: $lensStatus\n")
            append("${context.getString(R.string.status_default_engine_label)}: ${activeEngine.name}\n")
            append("${context.getString(R.string.status_offline_langs_label)}: $modelSummary\n")
            append("${context.getString(R.string.status_overlay_perm_label)}: ${if (overlayGranted) context.getString(R.string.status_granted_check) else context.getString(R.string.status_not_granted)}")
        }
        statusBox.addView(TextView(context).apply {
            text = summary
            textSize = 13.5f
            setLineSpacing(dp(4f).toFloat(), 1f)
            setTextColor(palette.onSurface)
            setPadding(0, dp(2f), 0, dp(14f))
        })

        // Primary CTA: Try Bubble Now (full width pill)
        val btnTry = UiKit.createPillButton(
            context,
            context.getString(R.string.btn_try_bubble),
            UiKit.ButtonVariant.PRIMARY,
            palette,
            callbacks::onTryBubbleNow
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10f) }
        }
        statusBox.addView(btnTry)

        // Two-button activation row
        val actRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4f) }
        }
        actRow.addView(
            UiKit.createPillButton(
                context,
                context.getString(R.string.btn_default_assistant),
                UiKit.ButtonVariant.TONAL,
                palette,
                callbacks::onOpenDefaultAssistant
            ).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6f)
                }
            }
        )
        actRow.addView(
            UiKit.createPillButton(
                context,
                context.getString(R.string.btn_accessibility),
                UiKit.ButtonVariant.OUTLINED,
                palette,
                callbacks::onOpenAccessibility
            ).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(6f)
                }
            }
        )
        statusBox.addView(actRow)

        val overlayRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        overlayRow.addView(
            UiKit.createPillButton(
                context,
                context.getString(R.string.btn_overlay_optional),
                UiKit.ButtonVariant.TEXT,
                palette,
                callbacks::onOpenOverlayPermission
            )
        )
        statusBox.addView(overlayRow)

        if (!CompatibilityHelper.supportsAccessibilityScreenshot()) {
            statusBox.addView(
                UiKit.createPillButton(
                    context,
                    context.getString(R.string.btn_legacy_capture),
                    UiKit.ButtonVariant.OUTLINED,
                    palette,
                    callbacks::onRequestLegacyCapture
                ).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(6f) }
                }
            )
        }
        container.addView(statusCard)

        // 3. Quick Settings & Core Preferences Card
        val (quickCard, quickBox) = UiKit.createExpressiveCard(context, palette)
        quickBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_quick_settings), palette))

        // App Interface Language (moved from Appearance so changing language is always immediately accessible)
        quickBox.addView(UiKit.createLabel(context, context.getString(R.string.label_app_language), palette))
        quickBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    LocaleHelper.LANG_SYSTEM to context.getString(R.string.app_lang_system),
                    LocaleHelper.LANG_ENGLISH to context.getString(R.string.app_lang_english),
                    LocaleHelper.LANG_INDONESIAN to context.getString(R.string.app_lang_indonesian)
                ),
                selected = preferences.appLanguageCode,
                palette = palette
            ) { code ->
                callbacks.onAppLanguageChanged(code)
            }
        )

        // Default Action Mode (moved from More)
        quickBox.addView(UiKit.createLabel(context, context.getString(R.string.label_default_action), palette))
        quickBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    ActionMode.TRANSLATE to context.getString(R.string.action_translate),
                    ActionMode.VISUAL_SEARCH to context.getString(R.string.action_visual_search)
                ),
                selected = preferences.lastActionMode,
                palette = palette
            ) { mode ->
                preferences.lastActionMode = mode
                callbacks.onGeneralSettingsChanged()
            }
        )

        quickBox.addView(UiKit.createDivider(context, palette))

        // Auto-Scan QR & Auto-Detect Links (moved from More + new Auto-Link Detection)
        quickBox.addView(
            UiKit.createSwitchRow(
                context = context,
                label = context.getString(R.string.switch_auto_qr),
                checked = preferences.autoQrScanEnabled,
                palette = palette,
                subtitle = context.getString(R.string.auto_qr_subtitle)
            ) { preferences.autoQrScanEnabled = it }
        )
        quickBox.addView(
            UiKit.createSwitchRow(
                context = context,
                label = context.getString(R.string.switch_auto_link_detect),
                checked = preferences.autoLinkDetectEnabled,
                palette = palette,
                subtitle = context.getString(R.string.auto_link_detect_subtitle)
            ) { preferences.autoLinkDetectEnabled = it }
        )

        quickBox.addView(UiKit.createDivider(context, palette))

        // Safe Mode toggle & quick jumps
        quickBox.addView(
            UiKit.createSwitchRow(
                context = context,
                label = context.getString(R.string.switch_safe_mode),
                checked = preferences.safeModeEnabled,
                palette = palette,
                subtitle = context.getString(R.string.safe_mode_subtitle)
            ) { checked ->
                callbacks.onSafeModeToggled(checked)
            }
        )

        val quickRow1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6f); bottomMargin = dp(6f) }
        }
        quickRow1.addView(
            UiKit.createPillButton(
                context,
                context.getString(R.string.quick_translation_langs),
                UiKit.ButtonVariant.TONAL,
                palette
            ) { callbacks.onNavigateToTab(1) }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6f)
                }
            }
        )
        quickRow1.addView(
            UiKit.createPillButton(
                context,
                context.getString(R.string.quick_default_search),
                UiKit.ButtonVariant.TONAL,
                palette
            ) { callbacks.onNavigateToTab(2) }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(6f)
                }
            }
        )
        quickBox.addView(quickRow1)

        val quickRow2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        quickRow2.addView(
            UiKit.createPillButton(
                context,
                context.getString(R.string.btn_excluded_apps_fmt, preferences.excludedPackages.size),
                UiKit.ButtonVariant.OUTLINED,
                palette,
                callbacks::onOpenExcludedApps
            ).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(6f)
                }
            }
        )
        quickRow2.addView(
            UiKit.createPillButton(
                context,
                context.getString(R.string.quick_usage_guide),
                UiKit.ButtonVariant.OUTLINED,
                palette
            ) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.guide_title))
                    .setMessage(context.getString(R.string.guide_body))
                    .setPositiveButton(context.getString(R.string.btn_close), null)
                    .show()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(6f)
                }
            }
        )
        quickBox.addView(quickRow2)
        container.addView(quickCard)

        // 4. Live Action Panel Preview Card (renders the actual ActionPanelView with current saved config)
        val (previewCard, previewBox) = UiKit.createExpressiveCard(context, palette)
        previewBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_bubble_preview), palette))
        previewBox.addView(UiKit.createSubtitle(context, context.getString(R.string.bubble_preview_subtitle), palette))

        val stage = FrameLayout(context).apply {
            tag = "skip_theme_children"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16f).toFloat()
                setColor(if (palette.isDark) Color.parseColor("#181A1F") else Color.parseColor("#E9EEF5"))
                setStroke(dp(1f), palette.outlineVariant)
            }
            setPadding(dp(12f), dp(14f), dp(12f), dp(14f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val panelPreview = ActionPanelView(context).apply {
            val accent = ThemeManager.resolveOverlayAccentColor(context, preferences)
            render(
                config = preferences.actionPanelConfig.copy(
                    position = ActionPanelPosition.BOTTOM
                ),
                activeMode = preferences.lastActionMode,
                themeAccentColor = accent
            )
            onActionButtonClick = { callbacks.onNavigateToTab(4) }
        }
        stage.addView(
            panelPreview,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        previewBox.addView(stage)

        // Quick jump to Panel customization
        previewBox.addView(
            UiKit.createPillButton(
                context,
                context.getString(R.string.tab_panel),
                UiKit.ButtonVariant.TEXT,
                palette
            ) { callbacks.onNavigateToTab(4) }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.END
                    topMargin = dp(6f)
                }
            }
        )
        container.addView(previewCard)
    }
}
