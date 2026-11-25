package com.bubble.screentools.ui.overlay

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.FrameLayout

/**
 * Root container for the Bubble overlay that reliably intercepts the Back key
 * regardless of which child view currently holds focus.
 */
class OverlayRootFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onBackPressedListener: (() -> Unit)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            onBackPressedListener?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
