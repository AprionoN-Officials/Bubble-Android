package com.bubble.screentools.donation

import com.bubble.screentools.BuildConfig

/**
 * Centralized Donation Reminder configuration.
 * Voluntary support only: zero paywalls, zero locked features, zero redeem codes, zero payment verification.
 */
object DonationConfig {
    const val ENABLED = true
    const val REMINDER_INTERVAL_HOURS = 72L
    const val CONTINUE_DELAY_SECONDS = 5L
    const val SHOW_COUNTDOWN = false
    const val SHOW_ON_FIRST_USE = true
    const val RESET_COOLDOWN_ON_DISMISS = false
    const val RESET_COOLDOWN_ON_DONATE = false
    const val RESET_COOLDOWN_ON_CONTINUE = true

    const val REMINDER_INTERVAL_MS = REMINDER_INTERVAL_HOURS * 60L * 60L * 1000L
    const val CONTINUE_DELAY_MS = CONTINUE_DELAY_SECONDS * 1000L

    /**
     * Developer donation URL sourced from build configuration (`BUBBLE_DONATION_URL`,
     * defaulting to https://trakteer.id/apriononurhakim).
     */
    val DONATION_URL: String
        get() = BuildConfig.DONATION_URL.trim()
}
