package com.bubble.screentools.donation

import com.bubble.screentools.data.BubblePreferences

/**
 * Centralized gate & persistence manager for the voluntary Donation Reminder.
 *
 * Policy:
 * - Strictly limited to in-app customization menus (Search, Selection, Panel, Appearance);
 *   NEVER triggered on app launch, General/Translation/More tabs, Safe Mode, updates,
 *   or when summoning Bubble via gesture, Accessibility button, or Quick Settings shortcut.
 * - On fresh install, displays ONCE the first time the user enters any eligible customization tab.
 * - Clicking Continue (after the 5s delay) starts a global 72-hour cooldown shared across ALL
 *   eligible tabs so switching tabs never repeats the popup.
 * - Clicking X, pressing Android Back, or clicking Donate never resets the cooldown.
 */
class DonationReminderManager(
    private val storage: DonationStorage,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val enabled: Boolean = DonationConfig.ENABLED,
    private val intervalMs: Long = DonationConfig.REMINDER_INTERVAL_MS
) {

    interface DonationStorage {
        var firstUsageTimestampMs: Long
        var lastAcceptedTimestampMs: Long
    }

    /**
     * Centralized classification of app entry points.
     * ONLY in-app customization menus are eligible for the Donation Reminder.
     * Core functionality, overlay triggers, basic translation, Safe Mode, permissions, and update checks are NEVER gated.
     */
    enum class GateDestination(val isPersonalizationEligible: Boolean) {
        // Eligible in-app customization menus (shared global 72h cooldown after Continue)
        SEARCH_SETTINGS_MAIN(true),
        SELECTION_CUSTOMIZATION(true),
        ACTION_PANEL_CUSTOMIZATION(true),
        APPEARANCE_THEME_CUSTOMIZATION(true),
        CUSTOM_SEARCH_ENGINE_EDITOR(true),

        // Always-free, non-gated core destinations & overlay triggers
        APP_LAUNCH(false),
        GENERAL_DASHBOARD(false),
        BASIC_TRANSLATION_SETTINGS(false),
        SAFE_MODE_AND_PRIVACY(false),
        PERMISSIONS_SETUP(false),
        UPDATE_CHECKER(false),
        OVERLAY_CORE_FEATURES(false)
    }

    sealed class AccessDecision {
        data object AllowImmediately : AccessDecision()
        data class ShowReminder(val destination: GateDestination) : AccessDecision()
    }

    fun recordFirstUsageIfNeeded(): Long {
        val existing = storage.firstUsageTimestampMs
        if (existing > 0L) return existing
        val now = clockMs()
        storage.firstUsageTimestampMs = now
        return now
    }

    /**
     * Evaluates whether entering [destination] should proceed directly or display the
     * voluntary Donation Reminder dialog.
     */
    fun evaluateAccess(destination: GateDestination): AccessDecision {
        if (!enabled || !destination.isPersonalizationEligible) {
            return AccessDecision.AllowImmediately
        }

        val now = clockMs()
        if (storage.firstUsageTimestampMs <= 0L) {
            storage.firstUsageTimestampMs = now
        }

        val lastAccepted = storage.lastAcceptedTimestampMs
        if (lastAccepted <= 0L) {
            // User has not accepted Continue yet since install
            return if (DonationConfig.SHOW_ON_FIRST_USE) {
                AccessDecision.ShowReminder(destination)
            } else {
                val elapsedSinceFirst = now - storage.firstUsageTimestampMs
                if (elapsedSinceFirst >= intervalMs) {
                    AccessDecision.ShowReminder(destination)
                } else {
                    AccessDecision.AllowImmediately
                }
            }
        }

        val elapsedSinceAccepted = now - lastAccepted
        return if (elapsedSinceAccepted >= intervalMs) {
            AccessDecision.ShowReminder(destination)
        } else {
            AccessDecision.AllowImmediately
        }
    }

    /** Called ONLY when the user clicks the enabled Continue / Lanjut button. */
    fun onContinueAccepted() {
        if (DonationConfig.RESET_COOLDOWN_ON_CONTINUE) {
            storage.lastAcceptedTimestampMs = clockMs()
        }
    }

    /** Called when user clicks X or presses Android Back button. Never resets cooldown. */
    fun onDismissedOrBack() {
        if (DonationConfig.RESET_COOLDOWN_ON_DISMISS) {
            storage.lastAcceptedTimestampMs = clockMs()
        }
    }

    /** Called when user clicks Donate / Donasi button. Never resets cooldown. */
    fun onDonateClicked() {
        if (DonationConfig.RESET_COOLDOWN_ON_DONATE) {
            storage.lastAcceptedTimestampMs = clockMs()
        }
    }

    fun getRemainingCooldownMs(): Long {
        val lastAccepted = storage.lastAcceptedTimestampMs
        if (lastAccepted <= 0L) {
            return if (DonationConfig.SHOW_ON_FIRST_USE) 0L else intervalMs
        }
        val elapsed = (clockMs() - lastAccepted).coerceAtLeast(0L)
        return (intervalMs - elapsed).coerceIn(0L, intervalMs)
    }

    companion object {
        fun fromPreferences(preferences: BubblePreferences): DonationReminderManager {
            return DonationReminderManager(
                storage = object : DonationStorage {
                    override var firstUsageTimestampMs: Long
                        get() = preferences.donationFirstUseTimestampMs
                        set(value) {
                            preferences.donationFirstUseTimestampMs = value
                        }
                    override var lastAcceptedTimestampMs: Long
                        get() = preferences.donationLastAcceptedTimestampMs
                        set(value) {
                            preferences.donationLastAcceptedTimestampMs = value
                        }
                }
            )
        }
    }
}
