package com.bubble.screentools.ui.main.tabs

import android.content.Context
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import com.bubble.screentools.BuildConfig
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.model.UpdateCheckInterval
import com.bubble.screentools.theme.ThemePaletteGenerator
import com.bubble.screentools.ui.main.UiKit
import com.bubble.screentools.update.UpdateManager
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Tab 6 — More / Lainnya (NEVER gated by Donation Reminder):
 * - GitHub Automatic Update Checker (Check Now, Changelog, Open GitHub Releases in browser, Schedule Settings)
 * - Privacy & Safe Mode (Toggle, Manage Excluded Apps, Clear Cache)
 * - About Bubble (Free, No Ads, No Paywalls, Privacy-first)
 */
object MoreTabBuilder {

    data class UpdateUiState(
        val isChecking: Boolean = false,
        val statusMessage: String? = null,
        val availableUpdate: UpdateManager.CheckResult.UpdateAvailable? = null
    )

    interface Callbacks {
        fun onChanged()
        fun onCheckForUpdatesNow()
        fun onOpenUpdateRelease(update: UpdateManager.CheckResult.UpdateAvailable)
        fun onOpenUrl(url: String)
        fun onUpdateScheduleChanged()
        fun onOpenExcludedApps()
        fun onClearCache()
    }

    fun render(
        context: Context,
        container: LinearLayout,
        preferences: BubblePreferences,
        palette: ThemePaletteGenerator.BubblePalette,
        updateUiState: UpdateUiState,
        callbacks: Callbacks
    ) {
        container.removeAllViews()
        val dp = { v: Float -> UiKit.dp(context, v) }

        // 1. GitHub Automatic Update Checker Card
        val (updCard, updBox) = UiKit.createExpressiveCard(context, palette)
        updBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_updates), palette))

        val lastCheckedStr = if (preferences.lastUpdateCheckTimestampMs > 0L) {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(preferences.lastUpdateCheckTimestampMs))
        } else {
            context.getString(R.string.update_never_checked)
        }
        val latestKnown = preferences.lastKnownLatestVersion.ifBlank { "—" }

        val metaText = buildString {
            append(context.getString(R.string.update_current_version_fmt, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
            append("\n")
            append(context.getString(R.string.update_latest_version_fmt, latestKnown))
            append("\n")
            append(context.getString(R.string.update_last_checked_fmt, lastCheckedStr))
        }
        updBox.addView(TextView(context).apply {
            text = metaText
            textSize = 13f
            setLineSpacing(dp(3f).toFloat(), 1f)
            setTextColor(palette.onSurface)
            setPadding(0, 0, 0, dp(8f))
        })

        if (!updateUiState.statusMessage.isNullOrBlank()) {
            updBox.addView(TextView(context).apply {
                text = updateUiState.statusMessage
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.primary)
                setPadding(0, 0, 0, dp(8f))
            })
        }

        val available = updateUiState.availableUpdate
        if (available != null) {
            val apkSizeMb = available.matchingApk?.sizeBytes?.takeIf { it > 0 }?.let {
                String.format(Locale.US, " • %.1f MB", it / (1024.0 * 1024.0))
            }.orEmpty()
            val releaseDate = available.release.publishedAt.take(10)
            updBox.addView(TextView(context).apply {
                text = "${available.release.title} ($releaseDate$apkSizeMb)"
                textSize = 13.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(palette.onSurface)
            })
            if (available.release.changelog.isNotBlank()) {
                updBox.addView(TextView(context).apply {
                    text = available.release.changelog.take(800)
                    textSize = 12f
                    setTextColor(palette.onSurfaceVariant)
                    setPadding(0, dp(4f), 0, dp(10f))
                })
            }
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(
                UiKit.createPillButton(context, context.getString(R.string.btn_download_update), UiKit.ButtonVariant.PRIMARY, palette) {
                    callbacks.onOpenUpdateRelease(available)
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6f) }
                }
            )
            row.addView(
                UiKit.createPillButton(context, context.getString(R.string.btn_view_github), UiKit.ButtonVariant.OUTLINED, palette) {
                    callbacks.onOpenUrl(available.release.htmlUrl.ifBlank { UpdateManager.RELEASES_WEB_URL })
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6f) }
                }
            )
            updBox.addView(row)
        } else {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val checkBtn = UiKit.createPillButton(
                context,
                if (updateUiState.isChecking) context.getString(R.string.update_status_checking) else context.getString(R.string.btn_check_updates),
                UiKit.ButtonVariant.PRIMARY,
                palette
            ) { callbacks.onCheckForUpdatesNow() }.apply {
                isEnabled = !updateUiState.isChecking
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6f) }
            }
            row.addView(checkBtn)
            row.addView(
                UiKit.createPillButton(context, context.getString(R.string.btn_view_github), UiKit.ButtonVariant.OUTLINED, palette) {
                    callbacks.onOpenUrl(UpdateManager.RELEASES_WEB_URL)
                }.apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6f) }
                }
            )
            updBox.addView(row)
        }

        updBox.addView(UiKit.createDivider(context, palette))
        updBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_auto_update), preferences.autoUpdateCheckEnabled, palette) {
            preferences.autoUpdateCheckEnabled = it
            callbacks.onUpdateScheduleChanged()
        })
        updBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_update_on_launch), preferences.checkUpdateOnLaunch, palette) {
            preferences.checkUpdateOnLaunch = it
        })
        updBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_update_wifi_only), preferences.updateWifiOnly, palette) {
            preferences.updateWifiOnly = it
            callbacks.onUpdateScheduleChanged()
        })
        updBox.addView(UiKit.createSwitchRow(context, context.getString(R.string.switch_update_prerelease), preferences.includePrereleaseUpdates, palette) {
            preferences.includePrereleaseUpdates = it
        })

        updBox.addView(UiKit.createLabel(context, context.getString(R.string.label_update_interval), palette))
        updBox.addView(
            UiKit.createChoiceChips(
                context = context,
                items = listOf(
                    UpdateCheckInterval.HOURS_6 to context.getString(R.string.interval_6h),
                    UpdateCheckInterval.HOURS_12 to context.getString(R.string.interval_12h),
                    UpdateCheckInterval.HOURS_24 to context.getString(R.string.interval_24h),
                    UpdateCheckInterval.DAYS_7 to context.getString(R.string.interval_7d)
                ),
                selected = preferences.updateCheckInterval,
                palette = palette
            ) { interval ->
                preferences.updateCheckInterval = interval
                callbacks.onUpdateScheduleChanged()
            }
        )
        container.addView(updCard)

        // 2. Privacy & Safe Mode Card
        val (privCard, privBox) = UiKit.createExpressiveCard(context, palette)
        privBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_privacy), palette))
        privBox.addView(
            UiKit.createSwitchRow(
                context,
                context.getString(R.string.switch_safe_mode),
                preferences.safeModeEnabled,
                palette,
                subtitle = context.getString(R.string.safe_mode_subtitle)
            ) {
                preferences.safeModeEnabled = it
                callbacks.onChanged()
            }
        )
        val privRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10f) }
        }
        privRow.addView(
            UiKit.createPillButton(
                context,
                context.getString(R.string.btn_excluded_apps_fmt, preferences.excludedPackages.size),
                UiKit.ButtonVariant.OUTLINED,
                palette,
                callbacks::onOpenExcludedApps
            ).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(6f) }
            }
        )
        privRow.addView(
            UiKit.createPillButton(
                context,
                context.getString(R.string.btn_clear_cache),
                UiKit.ButtonVariant.OUTLINED,
                palette,
                callbacks::onClearCache
            ).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(6f) }
            }
        )
        privBox.addView(privRow)
        container.addView(privCard)

        // 4. About Bubble Card
        val (aboutCard, aboutBox) = UiKit.createExpressiveCard(context, palette)
        aboutBox.addView(UiKit.createSectionTitle(context, context.getString(R.string.section_about), palette))
        aboutBox.addView(TextView(context).apply {
            text = context.getString(R.string.about_summary)
            textSize = 13f
            setLineSpacing(dp(3f).toFloat(), 1f)
            setTextColor(palette.onSurfaceVariant)
            setPadding(0, 0, 0, dp(10f))
        })
        aboutBox.addView(
            UiKit.createPillButton(context, context.getString(R.string.btn_view_github), UiKit.ButtonVariant.TONAL, palette) {
                callbacks.onOpenUrl(UpdateManager.REPO_URL)
            }
        )
        container.addView(aboutCard)
    }
}
