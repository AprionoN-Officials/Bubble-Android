package com.bubble.screentools.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.bubble.screentools.BuildConfig
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.donation.DonationReminderDialog
import com.bubble.screentools.donation.DonationReminderManager
import com.bubble.screentools.service.BubbleAccessibilityService
import com.bubble.screentools.service.MediaProjectionService
import com.bubble.screentools.theme.ThemeManager
import com.bubble.screentools.theme.ThemePaletteGenerator
import com.bubble.screentools.translation.LanguageDetectionManager
import com.bubble.screentools.translation.TranslationManager
import com.bubble.screentools.ui.main.tabs.AppearanceTabBuilder
import com.bubble.screentools.ui.main.tabs.GeneralTabBuilder
import com.bubble.screentools.ui.main.tabs.MoreTabBuilder
import com.bubble.screentools.ui.main.tabs.PanelTabBuilder
import com.bubble.screentools.ui.main.tabs.SearchTabBuilder
import com.bubble.screentools.ui.main.tabs.SelectionTabBuilder
import com.bubble.screentools.ui.main.tabs.TranslationTabBuilder
import com.bubble.screentools.update.UpdateManager
import com.bubble.screentools.util.CompatibilityHelper
import com.bubble.screentools.util.LocaleHelper
import com.bubble.screentools.util.PerformanceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

/**
 * Mode A — Material 3 Expressive 7-Segment Personalization & Control Center.
 * Uses Top Scrollable Tabs (General, Translation, Search, Selection, Panel, Appearance, More)
 * with preserved per-tab scroll state, plain Material Surface background, Material You Dynamic Color,
 * real GitHub Auto-Update Checker, and voluntary 72h Donation Reminder gated strictly on personalization.
 */
class MainActivity : AppCompatActivity(), DonationReminderDialog.HostCallback {

    private lateinit var preferences: BubblePreferences
    private lateinit var donationManager: DonationReminderManager
    private lateinit var updateManager: UpdateManager
    private lateinit var palette: ThemePaletteGenerator.BubblePalette

    private lateinit var mainContainer: View
    private lateinit var tvHeaderVersionBadge: TextView
    private lateinit var topNavigationTabs: TabLayout
    private lateinit var tabContentHost: FrameLayout

    private val tabScrollViews = arrayOfNulls<NestedScrollView>(TAB_COUNT)
    private val tabContainers = arrayOfNulls<LinearLayout>(TAB_COUNT)

    private var activeTabIndex: Int = TAB_GENERAL
    private var suppressTabSelectionCallback = false
    private var offlineModels: Set<String> = emptySet()
    private var updateUiState = MoreTabBuilder.UpdateUiState()
    private var pendingGatedAction: (() -> Unit)? = null

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionService.startWithGrant(this, result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, getString(R.string.overlay_capture_denied), Toast.LENGTH_SHORT).show()
        }
        renderActiveAndGeneralTabs()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = BubblePreferences(this)
        ThemeManager.applyToActivityBeforeContent(this, preferences)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        CompatibilityHelper.applySystemBarAppearance(window, this)
        setContentView(R.layout.activity_main)

        donationManager = DonationReminderManager.fromPreferences(preferences)
        donationManager.recordFirstUsageIfNeeded()
        updateManager = UpdateManager(this, preferences)
        updateManager.syncBackgroundSchedule()

        activeTabIndex = savedInstanceState?.getInt(STATE_ACTIVE_TAB, TAB_GENERAL) ?: TAB_GENERAL

        mainContainer = findViewById(R.id.mainContainer)
        tvHeaderVersionBadge = findViewById(R.id.tvHeaderVersionBadge)
        topNavigationTabs = findViewById(R.id.topNavigationTabs)
        tabContentHost = findViewById(R.id.tabContentHost)

        val baseLeft = mainContainer.paddingLeft
        val baseTop = mainContainer.paddingTop
        val baseRight = mainContainer.paddingRight
        val baseBottom = mainContainer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                baseLeft + bars.left,
                baseTop + bars.top,
                baseRight + bars.right,
                baseBottom + bars.bottom
            )
            insets
        }

        setupTopTabs()
        setupTabPages()
        applyThemeAndHeader()
        selectTabInternal(activeTabIndex, animate = false)
        refreshOfflineModelsAsync()

        // Optional silent launch check if enabled and interval has elapsed
        if (updateManager.shouldRunLaunchCheck()) {
            triggerUpdateCheck(manual = false)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_ACTIVE_TAB, activeTabIndex)
    }

    override fun onResume() {
        super.onResume()
        refreshOfflineModelsAsync()
        renderTab(TAB_GENERAL)
        if (activeTabIndex != TAB_GENERAL) {
            renderTab(activeTabIndex)
        }
    }

    private fun setupTopTabs() {
        val titles = listOf(
            getString(R.string.tab_general),
            getString(R.string.tab_translation),
            getString(R.string.tab_search),
            getString(R.string.tab_selection),
            getString(R.string.tab_panel),
            getString(R.string.tab_appearance),
            getString(R.string.tab_more)
        )
        topNavigationTabs.removeAllTabs()
        for (title in titles) {
            topNavigationTabs.addTab(topNavigationTabs.newTab().setText(title))
        }
        topNavigationTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (suppressTabSelectionCallback) return
                val targetIndex = tab.position
                val gateDest = tabIndexToGateDestination(targetIndex)
                if (gateDest.isPersonalizationEligible) {
                    when (donationManager.evaluateAccess(gateDest)) {
                        DonationReminderManager.AccessDecision.AllowImmediately -> {
                            selectTabInternal(targetIndex, animate = true)
                        }
                        is DonationReminderManager.AccessDecision.ShowReminder -> {
                            // Revert visual tab until user taps Continue (X/Back/Donate keeps current tab)
                            revertTabVisualTo(activeTabIndex)
                            pendingGatedAction = { selectTabInternal(targetIndex, animate = true) }
                            DonationReminderDialog.showIfNotAlreadyShowing(supportFragmentManager, gateDest)
                        }
                    }
                } else {
                    selectTabInternal(targetIndex, animate = true)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) {
                tabScrollViews[tab.position]?.smoothScrollTo(0, 0)
            }
        })
    }

    private fun setupTabPages() {
        tabContentHost.removeAllViews()
        for (i in 0 until TAB_COUNT) {
            val (scroll, content) = UiKit.createTabScrollPage(this)
            scroll.visibility = if (i == activeTabIndex) View.VISIBLE else View.GONE
            tabScrollViews[i] = scroll
            tabContainers[i] = content
            tabContentHost.addView(scroll)
        }
    }

    private fun applyThemeAndHeader() {
        palette = ThemeManager.resolvePalette(this, preferences.themeConfig)
        ThemeManager.applyPaletteToHierarchy(mainContainer, palette)

        // Update version badge strictly from BuildConfig (never hardcoded!)
        val variantDesc = ThemeManager.describeActiveVariant(this, preferences.themeConfig, BuildConfig.DEBUG)
        tvHeaderVersionBadge.text = "v${BuildConfig.VERSION_NAME} • $variantDesc"
        tvHeaderVersionBadge.setTextColor(palette.onPrimaryContainer)
        tvHeaderVersionBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = UiKit.dp(this@MainActivity, 12f).toFloat()
            setColor(palette.primaryContainer)
        }
    }

    private fun revertTabVisualTo(index: Int) {
        suppressTabSelectionCallback = true
        try {
            topNavigationTabs.getTabAt(index)?.select()
        } finally {
            suppressTabSelectionCallback = false
        }
    }

    private fun selectTabInternal(index: Int, animate: Boolean) {
        val safeIndex = index.coerceIn(0, TAB_COUNT - 1)
        activeTabIndex = safeIndex
        revertTabVisualTo(safeIndex)

        val allowAnim = animate && ThemeManager.areAnimationsAllowed(this, preferences.animationsEnabled)
        for (i in 0 until TAB_COUNT) {
            val view = tabScrollViews[i] ?: continue
            if (i == safeIndex) {
                if (view.visibility != View.VISIBLE) {
                    view.visibility = View.VISIBLE
                    if (allowAnim) {
                        view.alpha = 0f
                        view.animate().alpha(1f).setDuration(130L).start()
                    } else {
                        view.alpha = 1f
                    }
                }
            } else {
                view.visibility = View.GONE
            }
        }
        renderTab(safeIndex)
    }

    private fun navigateWithOptionalGate(tabIndex: Int) {
        val gateDest = tabIndexToGateDestination(tabIndex)
        if (gateDest.isPersonalizationEligible) {
            when (donationManager.evaluateAccess(gateDest)) {
                DonationReminderManager.AccessDecision.AllowImmediately -> selectTabInternal(tabIndex, animate = true)
                is DonationReminderManager.AccessDecision.ShowReminder -> {
                    pendingGatedAction = { selectTabInternal(tabIndex, animate = true) }
                    DonationReminderDialog.showIfNotAlreadyShowing(supportFragmentManager, gateDest)
                }
            }
        } else {
            selectTabInternal(tabIndex, animate = true)
        }
    }

    private fun runGatedPersonalizationAction(
        destination: DonationReminderManager.GateDestination,
        action: () -> Unit
    ) {
        when (donationManager.evaluateAccess(destination)) {
            DonationReminderManager.AccessDecision.AllowImmediately -> action()
            is DonationReminderManager.AccessDecision.ShowReminder -> {
                pendingGatedAction = action
                DonationReminderDialog.showIfNotAlreadyShowing(supportFragmentManager, destination)
            }
        }
    }

    override fun onDonationContinueAccepted(destinationKey: String) {
        val action = pendingGatedAction
        pendingGatedAction = null
        if (action != null) {
            action.invoke()
            return
        }
        // Fallback after Activity recreation: map destinationKey back to tab
        val dest = runCatching { DonationReminderManager.GateDestination.valueOf(destinationKey) }.getOrNull()
        when (dest) {
            DonationReminderManager.GateDestination.SEARCH_SETTINGS_MAIN -> selectTabInternal(TAB_SEARCH, animate = true)
            DonationReminderManager.GateDestination.SELECTION_CUSTOMIZATION -> selectTabInternal(TAB_SELECTION, animate = true)
            DonationReminderManager.GateDestination.ACTION_PANEL_CUSTOMIZATION -> selectTabInternal(TAB_PANEL, animate = true)
            DonationReminderManager.GateDestination.APPEARANCE_THEME_CUSTOMIZATION -> selectTabInternal(TAB_APPEARANCE, animate = true)
            else -> Unit
        }
    }

    override fun onDonationCancelled(destinationKey: String) {
        pendingGatedAction = null
        revertTabVisualTo(activeTabIndex)
    }

    private fun renderActiveAndGeneralTabs() {
        renderTab(TAB_GENERAL)
        if (activeTabIndex != TAB_GENERAL) renderTab(activeTabIndex)
    }

    private fun renderAllPopulatedTabs() {
        applyThemeAndHeader()
        for (i in 0 until TAB_COUNT) {
            if (i == activeTabIndex || (tabContainers[i]?.childCount ?: 0) > 0) {
                renderTab(i)
            }
        }
    }

    private fun renderTab(index: Int) {
        val container = tabContainers[index] ?: return
        when (index) {
            TAB_GENERAL -> GeneralTabBuilder.render(
                context = this,
                container = container,
                preferences = preferences,
                palette = palette,
                offlineModels = offlineModels,
                callbacks = object : GeneralTabBuilder.Callbacks {
                    override fun onTryBubbleNow() = handleTryBubbleNow()
                    override fun onOpenDefaultAssistant() = openDefaultAssistantSettings()
                    override fun onOpenAccessibility() = startActivity(CompatibilityHelper.createAccessibilitySettingsIntent())
                    override fun onOpenOverlayPermission() = startActivity(CompatibilityHelper.createOverlayPermissionIntent(this@MainActivity))
                    override fun onRequestLegacyCapture() {
                        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        projectionLauncher.launch(mpm.createScreenCaptureIntent())
                    }
                    override fun onNavigateToTab(tabIndex: Int) = navigateWithOptionalGate(tabIndex)
                    override fun onSafeModeToggled(enabled: Boolean) {
                        preferences.safeModeEnabled = enabled
                        renderTab(TAB_MORE)
                    }
                    override fun onOpenExcludedApps() = showExcludedAppsDialog()
                    override fun onAppLanguageChanged(langCode: String) {
                        if (langCode != preferences.appLanguageCode) {
                            preferences.appLanguageCode = langCode
                            LocaleHelper.applyAppLocale(langCode)
                        }
                    }
                    override fun onGeneralSettingsChanged() {
                        renderTab(TAB_GENERAL)
                    }
                }
            )

            TAB_TRANSLATION -> TranslationTabBuilder.render(
                context = this,
                container = container,
                preferences = preferences,
                palette = palette,
                offlineModels = offlineModels,
                callbacks = object : TranslationTabBuilder.Callbacks {
                    override fun onChanged() = renderTab(TAB_TRANSLATION)
                    override fun onOpenOfflineLanguageManager(initialTab: Int) {
                        startActivity(
                            Intent(this@MainActivity, LanguageManagerActivity::class.java)
                                .putExtra(LanguageManagerActivity.EXTRA_INITIAL_TAB, initialTab)
                        )
                    }
                    override fun onDeleteUnusedOfflineModels() = deleteUnusedOfflineModels()
                }
            )

            TAB_SEARCH -> SearchTabBuilder.render(
                context = this,
                container = container,
                preferences = preferences,
                palette = palette,
                callbacks = object : SearchTabBuilder.Callbacks {
                    override fun onChanged() {
                        renderTab(TAB_SEARCH)
                        renderTab(TAB_GENERAL)
                    }
                    override fun onRequestCustomEngineGate(action: () -> Unit) {
                        runGatedPersonalizationAction(
                            DonationReminderManager.GateDestination.CUSTOM_SEARCH_ENGINE_EDITOR,
                            action
                        )
                    }
                }
            )

            TAB_SELECTION -> SelectionTabBuilder.render(
                context = this,
                container = container,
                preferences = preferences,
                palette = palette,
                onFullRebindRequested = { renderTab(TAB_SELECTION) }
            )

            TAB_PANEL -> PanelTabBuilder.render(
                context = this,
                container = container,
                preferences = preferences,
                palette = palette,
                onFullRebindRequested = {
                    renderTab(TAB_PANEL)
                    renderTab(TAB_GENERAL)
                }
            )

            TAB_APPEARANCE -> AppearanceTabBuilder.render(
                context = this,
                container = container,
                preferences = preferences,
                palette = palette,
                callbacks = object : AppearanceTabBuilder.Callbacks {
                    override fun onThemeChanged(recreateRequired: Boolean) {
                        if (recreateRequired) {
                            recreate()
                        } else {
                            renderAllPopulatedTabs()
                        }
                    }
                }
            )

            TAB_MORE -> MoreTabBuilder.render(
                context = this,
                container = container,
                preferences = preferences,
                palette = palette,
                updateUiState = updateUiState,
                callbacks = object : MoreTabBuilder.Callbacks {
                    override fun onChanged() {
                        renderTab(TAB_MORE)
                        renderTab(TAB_GENERAL)
                    }
                    override fun onCheckForUpdatesNow() = triggerUpdateCheck(manual = true)
                    override fun onOpenUpdateRelease(update: UpdateManager.CheckResult.UpdateAvailable) {
                        val targetUrl = update.matchingApk?.downloadUrl
                            ?.takeIf { it.startsWith("https://") }
                            ?: update.release.htmlUrl.ifBlank { UpdateManager.RELEASES_WEB_URL }
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))) }
                    }
                    override fun onOpenUrl(url: String) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }
                    override fun onUpdateScheduleChanged() {
                        updateManager.syncBackgroundSchedule()
                    }
                    override fun onOpenExcludedApps() = showExcludedAppsDialog()
                    override fun onClearCache() {
                        PerformanceManager.clearVolatileCaches(this@MainActivity)
                        Toast.makeText(this@MainActivity, getString(R.string.toast_cache_cleared), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    private fun handleTryBubbleNow() {
        val svc = BubbleAccessibilityService.instance
        if (svc != null) {
            moveTaskToBack(true)
            svc.triggerOverlayWithDelay(380L)
            return
        }
        if (CompatibilityHelper.isDefaultDigitalAssistant(this)) {
            Toast.makeText(this, getString(R.string.toast_assistant_ready_hint), Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
            return
        }
        Toast.makeText(this, getString(R.string.toast_enable_a11y_or_assistant), Toast.LENGTH_LONG).show()
        startActivity(CompatibilityHelper.createAccessibilitySettingsIntent())
    }

    private fun openDefaultAssistantSettings() {
        runCatching {
            startActivity(CompatibilityHelper.createVoiceInputSettingsIntent())
        }.onFailure {
            Toast.makeText(this, getString(R.string.toast_open_assistant_manual), Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshOfflineModelsAsync() {
        lifecycleScope.launch {
            val tm = TranslationManager(LanguageDetectionManager())
            val models = tm.getDownloadedModels()
            tm.release()
            offlineModels = models
            renderTab(TAB_GENERAL)
            if (activeTabIndex == TAB_TRANSLATION) {
                renderTab(TAB_TRANSLATION)
            }
        }
    }

    private fun deleteUnusedOfflineModels() {
        lifecycleScope.launch {
            val keep = buildSet {
                add(preferences.targetLanguageCode)
                if (!preferences.autoDetectLanguage) add(preferences.sourceLanguageCode)
                add(preferences.fallbackLanguageCode)
                addAll(preferences.preferredSourceLanguages)
            }
            val tm = TranslationManager(LanguageDetectionManager())
            val removed = tm.deleteUnusedModels(keep)
            offlineModels = tm.getDownloadedModels()
            tm.release()
            Toast.makeText(
                this@MainActivity,
                if (removed > 0) getString(R.string.toast_unused_models_deleted_fmt, removed)
                else getString(R.string.toast_no_unused_models),
                Toast.LENGTH_SHORT
            ).show()
            renderTab(TAB_TRANSLATION)
            renderTab(TAB_GENERAL)
        }
    }

    private fun triggerUpdateCheck(manual: Boolean) {
        updateUiState = updateUiState.copy(
            isChecking = true,
            statusMessage = if (manual) getString(R.string.update_status_checking) else updateUiState.statusMessage
        )
        if (activeTabIndex == TAB_MORE) renderTab(TAB_MORE)

        lifecycleScope.launch {
            val result = updateManager.checkForUpdates(manual = manual)
            updateUiState = when (result) {
                is UpdateManager.CheckResult.UpdateAvailable -> {
                    val date = result.release.publishedAt.take(10)
                    MoreTabBuilder.UpdateUiState(
                        isChecking = false,
                        statusMessage = getString(R.string.update_status_available_fmt, result.release.tagName, date),
                        availableUpdate = result
                    )
                }
                is UpdateManager.CheckResult.UpToDate -> MoreTabBuilder.UpdateUiState(
                    isChecking = false,
                    statusMessage = if (manual) getString(R.string.update_status_up_to_date, result.currentVersion) else null,
                    availableUpdate = null
                )
                is UpdateManager.CheckResult.RateLimited -> MoreTabBuilder.UpdateUiState(
                    isChecking = false,
                    statusMessage = if (manual) getString(R.string.update_status_rate_limited) else null
                )
                is UpdateManager.CheckResult.Skipped -> MoreTabBuilder.UpdateUiState(
                    isChecking = false,
                    statusMessage = if (manual) result.reason else null
                )
                is UpdateManager.CheckResult.Error -> MoreTabBuilder.UpdateUiState(
                    isChecking = false,
                    statusMessage = if (manual) result.message else null
                )
            }
            if (activeTabIndex == TAB_MORE) renderTab(TAB_MORE)
        }
    }

    private fun showExcludedAppsDialog() {
        val current = preferences.excludedPackages.sorted()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_excluded_title))
            .setItems(current.toTypedArray()) { _, which ->
                val pkg = current[which]
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.dialog_remove_excluded_title))
                    .setMessage(getString(R.string.dialog_remove_excluded_msg, pkg))
                    .setPositiveButton(getString(R.string.btn_remove)) { _, _ ->
                        preferences.removeExcludedPackage(pkg)
                        Toast.makeText(this, getString(R.string.toast_pkg_removed, pkg), Toast.LENGTH_SHORT).show()
                        renderActiveAndGeneralTabs()
                    }
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show()
            }
            .setPositiveButton(getString(R.string.btn_add_package)) { _, _ -> showAddExcludedPackageDialog() }
            .setNegativeButton(getString(R.string.btn_close), null)
            .show()
    }

    private fun showAddExcludedPackageDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.hint_package_name)
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.dialog_add_excluded_title))
            .setView(input)
            .setPositiveButton(getString(R.string.btn_add)) { _, _ ->
                val pkg = input.text.toString().trim()
                if (pkg.isNotEmpty()) {
                    preferences.addExcludedPackage(pkg)
                    Toast.makeText(this, getString(R.string.toast_pkg_added, pkg), Toast.LENGTH_SHORT).show()
                    renderActiveAndGeneralTabs()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun tabIndexToGateDestination(index: Int): DonationReminderManager.GateDestination = when (index) {
        TAB_SELECTION -> DonationReminderManager.GateDestination.SELECTION_CUSTOMIZATION
        TAB_PANEL -> DonationReminderManager.GateDestination.ACTION_PANEL_CUSTOMIZATION
        TAB_APPEARANCE -> DonationReminderManager.GateDestination.APPEARANCE_THEME_CUSTOMIZATION
        TAB_TRANSLATION -> DonationReminderManager.GateDestination.BASIC_TRANSLATION_SETTINGS
        TAB_SEARCH -> DonationReminderManager.GateDestination.SEARCH_SETTINGS_MAIN
        TAB_MORE -> DonationReminderManager.GateDestination.SAFE_MODE_AND_PRIVACY
        else -> DonationReminderManager.GateDestination.GENERAL_DASHBOARD
    }

    companion object {
        private const val TAB_GENERAL = 0
        private const val TAB_TRANSLATION = 1
        private const val TAB_SEARCH = 2
        private const val TAB_SELECTION = 3
        private const val TAB_PANEL = 4
        private const val TAB_APPEARANCE = 5
        private const val TAB_MORE = 6
        private const val TAB_COUNT = 7
        private const val STATE_ACTIVE_TAB = "state_active_tab"
    }
}
