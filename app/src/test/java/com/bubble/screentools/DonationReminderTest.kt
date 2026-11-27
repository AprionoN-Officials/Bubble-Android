package com.bubble.screentools

import com.bubble.screentools.donation.DonationConfig
import com.bubble.screentools.donation.DonationReminderManager
import com.bubble.screentools.donation.DonationReminderManager.AccessDecision
import com.bubble.screentools.donation.DonationReminderManager.GateDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive unit verification of Donation Reminder policy:
 * - Shows ONCE on fresh install when entering Search / Selection / Panel / Appearance tabs
 * - Global 72-hour cooldown across ALL eligible tabs after Continue (never repeats per tab!)
 * - X / Back / Donate never reset cooldown
 * - Core menus, app launch, and overlay triggers (gesture / Accessibility / shortcut) are NEVER gated
 */
class DonationReminderTest {

    private class FakeStorage : DonationReminderManager.DonationStorage {
        override var firstUsageTimestampMs: Long = 0L
        override var lastAcceptedTimestampMs: Long = 0L
    }

    private val hourMs = 60L * 60L * 1000L

    @Test
    fun configMatchesRequiredConstantsAndTrakteerUrl() {
        assertTrue(DonationConfig.ENABLED)
        assertEquals(72L, DonationConfig.REMINDER_INTERVAL_HOURS)
        assertEquals(5L, DonationConfig.CONTINUE_DELAY_SECONDS)
        assertFalse(DonationConfig.SHOW_COUNTDOWN)
        assertTrue(DonationConfig.SHOW_ON_FIRST_USE)
        assertFalse(DonationConfig.RESET_COOLDOWN_ON_DISMISS)
        assertFalse(DonationConfig.RESET_COOLDOWN_ON_DONATE)
        assertTrue(DonationConfig.RESET_COOLDOWN_ON_CONTINUE)
        assertTrue(DonationConfig.DONATION_URL.startsWith("https://"))
    }

    @Test
    fun firstInstallShowsReminderOnceInCustomizationTabEvenAfterAppLaunchRecordedTimestamp() {
        var now = 1_000_000L
        val storage = FakeStorage()
        val manager = DonationReminderManager(storage, clockMs = { now })

        // MainActivity.onCreate records first usage timestamp on app launch
        manager.recordFirstUsageIfNeeded()
        assertEquals(1_000_000L, storage.firstUsageTimestampMs)

        // App launch & General tab never trigger popup
        assertEquals(AccessDecision.AllowImmediately, manager.evaluateAccess(GateDestination.APP_LAUNCH))
        assertEquals(AccessDecision.AllowImmediately, manager.evaluateAccess(GateDestination.GENERAL_DASHBOARD))

        // First time user enters Search / Selection / Panel / Appearance -> shows popup ONCE
        now += 3_000L
        val decision = manager.evaluateAccess(GateDestination.SEARCH_SETTINGS_MAIN)
        assertTrue(decision is AccessDecision.ShowReminder)
        assertEquals(GateDestination.SEARCH_SETTINGS_MAIN, (decision as AccessDecision.ShowReminder).destination)
    }

    @Test
    fun afterContinueAllCustomizationTabsShareGlobal72HourCooldown() {
        var now = 1_000_000L
        val storage = FakeStorage()
        val manager = DonationReminderManager(storage, clockMs = { now })
        manager.recordFirstUsageIfNeeded()

        // First entry on Search triggers popup
        assertTrue(manager.evaluateAccess(GateDestination.SEARCH_SETTINGS_MAIN) is AccessDecision.ShowReminder)

        // User waits 5s and taps Continue
        manager.onContinueAccepted()
        assertEquals(now, storage.lastAcceptedTimestampMs)

        // Switching across ALL customization tabs must NOT trigger popup again within 72h!
        assertEquals(AccessDecision.AllowImmediately, manager.evaluateAccess(GateDestination.SEARCH_SETTINGS_MAIN))
        assertEquals(AccessDecision.AllowImmediately, manager.evaluateAccess(GateDestination.SELECTION_CUSTOMIZATION))
        assertEquals(AccessDecision.AllowImmediately, manager.evaluateAccess(GateDestination.ACTION_PANEL_CUSTOMIZATION))
        assertEquals(AccessDecision.AllowImmediately, manager.evaluateAccess(GateDestination.APPEARANCE_THEME_CUSTOMIZATION))
        assertEquals(AccessDecision.AllowImmediately, manager.evaluateAccess(GateDestination.CUSTOM_SEARCH_ENGINE_EDITOR))

        // Still allowed at +71h 59m
        now += 71 * hourMs + 59 * 60 * 1000L
        assertEquals(AccessDecision.AllowImmediately, manager.evaluateAccess(GateDestination.ACTION_PANEL_CUSTOMIZATION))
        assertEquals(AccessDecision.AllowImmediately, manager.evaluateAccess(GateDestination.APPEARANCE_THEME_CUSTOMIZATION))

        // Re-triggers once after 72 hours have elapsed
        now += 1 * 60 * 1000L
        val afterCooldown = manager.evaluateAccess(GateDestination.SELECTION_CUSTOMIZATION)
        assertTrue(afterCooldown is AccessDecision.ShowReminder)
    }

    @Test
    fun coreFeaturesAndOverlayTriggersNeverTriggerReminder() {
        var now = 1_000_000L
        val storage = FakeStorage()
        val manager = DonationReminderManager(storage, clockMs = { now })
        manager.recordFirstUsageIfNeeded()

        // Check both at fresh install and after 1000 hours
        val nonGated = listOf(
            GateDestination.APP_LAUNCH,
            GateDestination.GENERAL_DASHBOARD,
            GateDestination.BASIC_TRANSLATION_SETTINGS,
            GateDestination.SAFE_MODE_AND_PRIVACY,
            GateDestination.PERMISSIONS_SETUP,
            GateDestination.UPDATE_CHECKER,
            GateDestination.OVERLAY_CORE_FEATURES
        )
        for (dest in nonGated) {
            assertEquals("Core destination $dest must never be gated on fresh install", AccessDecision.AllowImmediately, manager.evaluateAccess(dest))
        }
        now += 1000 * hourMs
        for (dest in nonGated) {
            assertEquals("Core destination $dest must never be gated after 1000h", AccessDecision.AllowImmediately, manager.evaluateAccess(dest))
        }
    }

    @Test
    fun dismissAndBackDoNotResetCooldown() {
        val now = 1_000_000L
        val storage = FakeStorage()
        val manager = DonationReminderManager(storage, clockMs = { now })
        manager.recordFirstUsageIfNeeded()

        assertTrue(manager.evaluateAccess(GateDestination.ACTION_PANEL_CUSTOMIZATION) is AccessDecision.ShowReminder)

        // User taps X or presses Android Back
        manager.onDismissedOrBack()
        assertEquals(0L, storage.lastAcceptedTimestampMs)

        // Trying to open customization again still shows dialog
        assertTrue(manager.evaluateAccess(GateDestination.ACTION_PANEL_CUSTOMIZATION) is AccessDecision.ShowReminder)
    }

    @Test
    fun donateClickDoesNotResetCooldown() {
        val now = 1_000_000L
        val storage = FakeStorage()
        val manager = DonationReminderManager(storage, clockMs = { now })
        manager.recordFirstUsageIfNeeded()

        assertTrue(manager.evaluateAccess(GateDestination.APPEARANCE_THEME_CUSTOMIZATION) is AccessDecision.ShowReminder)

        // User clicks Donate -> opens Trakteer in browser, returns to Bubble
        manager.onDonateClicked()
        assertEquals(0L, storage.lastAcceptedTimestampMs)
        assertTrue(manager.evaluateAccess(GateDestination.APPEARANCE_THEME_CUSTOMIZATION) is AccessDecision.ShowReminder)
    }

    @Test
    fun continueCooldownPersistsAcrossAppRestart() {
        var now = 1_000_000L
        val storage = FakeStorage()
        val manager = DonationReminderManager(storage, clockMs = { now })
        manager.recordFirstUsageIfNeeded()

        assertTrue(manager.evaluateAccess(GateDestination.SELECTION_CUSTOMIZATION) is AccessDecision.ShowReminder)
        manager.onContinueAccepted()

        // Simulate app restart reading the same persistent storage
        val restartedManager = DonationReminderManager(storage, clockMs = { now })
        assertEquals(AccessDecision.AllowImmediately, restartedManager.evaluateAccess(GateDestination.SELECTION_CUSTOMIZATION))
        assertEquals(AccessDecision.AllowImmediately, restartedManager.evaluateAccess(GateDestination.SEARCH_SETTINGS_MAIN))

        // Triggers again at +72h
        now += 72 * hourMs
        assertTrue(restartedManager.evaluateAccess(GateDestination.APPEARANCE_THEME_CUSTOMIZATION) is AccessDecision.ShowReminder)
    }
}
