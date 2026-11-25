package com.bubble.screentools.capture

import android.graphics.Bitmap
import com.bubble.screentools.model.TextBlockResult

/**
 * Ephemeral in-memory transfer store between VoiceInteractionSession / AccessibilityService
 * and BubbleAssistantActivity. Never writes screenshots to disk and recycles stale bitmaps.
 */
object SessionSnapshotStore {

    data class SnapshotPayload(
        val screenshot: Bitmap?,
        val assistTextBlocks: List<TextBlockResult>,
        val foregroundPackage: String?
    )

    private val lock = Any()
    private var pendingPayload: SnapshotPayload? = null

    fun put(payload: SnapshotPayload) {
        synchronized(lock) {
            val previous = pendingPayload?.screenshot
            if (previous != null && previous !== payload.screenshot && !previous.isRecycled) {
                previous.recycle()
            }
            pendingPayload = payload
        }
    }

    fun consume(): SnapshotPayload? {
        synchronized(lock) {
            val payload = pendingPayload
            pendingPayload = null
            return payload
        }
    }

    fun clear() {
        synchronized(lock) {
            val bmp = pendingPayload?.screenshot
            if (bmp != null && !bmp.isRecycled) {
                bmp.recycle()
            }
            pendingPayload = null
        }
    }
}
