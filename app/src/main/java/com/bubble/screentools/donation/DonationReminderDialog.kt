package com.bubble.screentools.donation

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.bubble.screentools.R
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.lens.SearchEngineRegistry
import com.bubble.screentools.theme.ThemeManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive voluntary Donation Reminder Dialog.
 * - Heart icon, localized Title/Description/Supporting text
 * - Top-right X Close button (active immediately; cancels navigation without resetting cooldown)
 * - Bottom-left Continue / Lanjut button (disabled for 5 seconds via lifecycle-aware coroutine,
 *   NO countdown, NO progress indicator; resets 72h cooldown & navigates on click)
 * - Bottom-right Donate / Donasi button (active immediately; opens configured donation URL without resetting cooldown)
 * - Android Back button matches X behavior
 * - Single-dialog tag guard prevents duplicates across Activity recreation & orientation changes
 */
class DonationReminderDialog : DialogFragment() {

    interface HostCallback {
        fun onDonationContinueAccepted(destinationKey: String)
        fun onDonationCancelled(destinationKey: String)
    }

    private var btnContinue: MaterialButton? = null
    private var unlockAtElapsedRealtimeMs: Long = 0L
    private var wasExplicitlyContinued: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unlockAtElapsedRealtimeMs = savedInstanceState?.getLong(STATE_UNLOCK_AT_ELAPSED, 0L)
            ?.takeIf { it > 0L }
            ?: (SystemClock.elapsedRealtime() + DonationConfig.CONTINUE_DELAY_MS)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_UNLOCK_AT_ELAPSED, unlockAtElapsedRealtimeMs)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val prefs = BubblePreferences(ctx)
        val palette = ThemeManager.resolvePalette(ctx, prefs.themeConfig)
        val destinationKey = arguments?.getString(ARG_DESTINATION_KEY).orEmpty()
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val card = MaterialCardView(ctx).apply {
            radius = dp(28).toFloat()
            cardElevation = dp(6).toFloat()
            strokeWidth = dp(1)
            setCardBackgroundColor(palette.surfaceContainer)
            strokeColor = palette.outlineVariant
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(20))
        }

        // Top row: Heart icon centered-left + top-right X close button
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val heartIcon = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_heart)
            contentDescription = null
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
        }
        topRow.addView(heartIcon)

        val spacer = android.view.View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        topRow.addView(spacer)

        val btnClose = ImageButton(ctx).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(palette.onSurfaceVariant)
            contentDescription = getString(R.string.btn_close)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener {
                // X button: dismiss without resetting cooldown and cancel navigation
                dismissAllowingStateLoss()
            }
        }
        topRow.addView(btnClose)
        root.addView(topRow)

        // Title
        val tvTitle = TextView(ctx).apply {
            text = getString(R.string.donation_title)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.onSurface)
            setPadding(0, dp(10), 0, dp(8))
        }
        root.addView(tvTitle)

        // Description
        val tvDesc = TextView(ctx).apply {
            text = getString(R.string.donation_description)
            textSize = 14.5f
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextColor(palette.onSurfaceVariant)
            setPadding(0, 0, 0, dp(10))
        }
        root.addView(tvDesc)

        // Supporting text
        val tvSupport = TextView(ctx).apply {
            text = getString(R.string.donation_supporting_text)
            textSize = 12.5f
            setTypeface(typeface, Typeface.ITALIC)
            setTextColor(palette.primary)
            setPadding(0, 0, 0, dp(18))
        }
        root.addView(tvSupport)

        // Bottom buttons row: Continue (left) + Donate (right)
        val buttonRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val continueBtn = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.donation_btn_continue)
            isAllCaps = false
            cornerRadius = dp(24)
            minHeight = dp(44)
            isEnabled = false // Disabled initially; enabled silently after 5s with NO countdown/progress
            alpha = 0.45f
            strokeColor = android.content.res.ColorStateList.valueOf(palette.outline)
            setTextColor(palette.onSurface)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                wasExplicitlyContinued = true
                DonationReminderManager.fromPreferences(prefs).onContinueAccepted()
                (activity as? HostCallback)?.onDonationContinueAccepted(destinationKey)
                dismissAllowingStateLoss()
            }
        }
        btnContinue = continueBtn
        buttonRow.addView(continueBtn)

        val donateBtn = MaterialButton(ctx).apply {
            text = getString(R.string.donation_btn_donate)
            isAllCaps = false
            cornerRadius = dp(24)
            minHeight = dp(44)
            isEnabled = true // Active from the start
            backgroundTintList = android.content.res.ColorStateList.valueOf(palette.primary)
            setTextColor(palette.onPrimary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
            setOnClickListener {
                // Donate opens configured URL and NEVER resets cooldown
                DonationReminderManager.fromPreferences(prefs).onDonateClicked()
                val url = DonationConfig.DONATION_URL
                if (SearchEngineRegistry.isValidHttpUrl(url)) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { startActivity(intent) }.onFailure {
                        Toast.makeText(ctx, getString(R.string.donation_open_browser_error), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(ctx, getString(R.string.donation_url_not_configured), Toast.LENGTH_LONG).show()
                }
            }
        }
        buttonRow.addView(donateBtn)
        root.addView(buttonRow)

        card.addView(root)

        val dialog = Dialog(ctx)
        dialog.setContentView(card)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.90f).toInt().coerceAtMost(dp(420)),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // Lifecycle-aware 5-second delay without countdown or progress indicators
        lifecycleScope.launch {
            val remaining = unlockAtElapsedRealtimeMs - SystemClock.elapsedRealtime()
            if (remaining > 0L) {
                delay(remaining)
            }
            btnContinue?.apply {
                isEnabled = true
                alpha = 1f
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!wasExplicitlyContinued) {
            val ctx = context
            if (ctx != null) {
                DonationReminderManager.fromPreferences(BubblePreferences(ctx)).onDismissedOrBack()
            }
            val destinationKey = arguments?.getString(ARG_DESTINATION_KEY).orEmpty()
            (activity as? HostCallback)?.onDonationCancelled(destinationKey)
        }
    }

    companion object {
        const val TAG = "bubble_donation_reminder"
        private const val ARG_DESTINATION_KEY = "arg_destination_key"
        private const val STATE_UNLOCK_AT_ELAPSED = "state_unlock_at_elapsed"

        /**
         * Shows the dialog safely with a single-instance guard preventing duplicate dialogs.
         */
        fun showIfNotAlreadyShowing(
            fragmentManager: FragmentManager,
            destination: DonationReminderManager.GateDestination
        ) {
            if (fragmentManager.isStateSaved) return
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            val dialog = DonationReminderDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_DESTINATION_KEY, destination.name)
                }
            }
            dialog.show(fragmentManager, TAG)
        }
    }
}
