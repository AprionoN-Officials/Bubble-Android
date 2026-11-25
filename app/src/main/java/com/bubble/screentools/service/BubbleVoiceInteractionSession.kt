package com.bubble.screentools.service

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionSession
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.bubble.screentools.capture.SessionSnapshotStore
import com.bubble.screentools.data.BubblePreferences
import com.bubble.screentools.model.TextBlockResult
import com.bubble.screentools.security.SafeModeManager
import com.bubble.screentools.ui.overlay.BubbleAssistantActivity

/**
 * Digital Assistant session receiving the OS-supplied screenshot and AssistStructure
 * directly — no MediaProjection "Start recording" prompt and no AccessibilityService required.
 */
class BubbleVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences by lazy { BubblePreferences(context) }
    private val safeModeManager by lazy { SafeModeManager(preferences) }

    private var capturedScreenshot: Bitmap? = null
    private var assistTextBlocks: List<TextBlockResult> = emptyList()
    private var foregroundPackage: String? = null
    private var assistReceived = false
    private var screenshotReceived = false
    private var dispatched = false

    private val fallbackDispatchRunnable = Runnable {
        dispatchSessionIfReady(force = true)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        // Reset session state
        capturedScreenshot?.let { if (!it.isRecycled) it.recycle() }
        capturedScreenshot = null
        assistTextBlocks = emptyList()
        foregroundPackage = null
        assistReceived = false
        screenshotReceived = false
        dispatched = false

        // Keep session window invisible; BubbleAssistantActivity presents the full interactive UI
        window?.window?.decorView?.alpha = 0f

        mainHandler.removeCallbacks(fallbackDispatchRunnable)
        mainHandler.postDelayed(fallbackDispatchRunnable, 1200L)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        processAssistStructure(state.assistStructure)
        assistReceived = true
        dispatchSessionIfReady(force = false)
    }

    @Suppress("DEPRECATION")
    override fun onHandleAssist(
        data: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?
    ) {
        super.onHandleAssist(data, structure, content)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            processAssistStructure(structure)
            assistReceived = true
            dispatchSessionIfReady(force = false)
        }
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        capturedScreenshot = screenshot?.let { bmp ->
            runCatching {
                if (bmp.config == Bitmap.Config.HARDWARE) {
                    bmp.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bmp.copy(Bitmap.Config.ARGB_8888, false)
                }
            }.getOrNull()
        }
        screenshotReceived = true
        dispatchSessionIfReady(force = false)
    }

    private fun dispatchSessionIfReady(force: Boolean) {
        if (dispatched) return
        if (!force && !(assistReceived && screenshotReceived)) return

        dispatched = true
        mainHandler.removeCallbacks(fallbackDispatchRunnable)

        // Safe Mode check when foreground package is known
        val pkg = foregroundPackage
        if (!pkg.isNullOrBlank()) {
            safeModeManager.updateForegroundPackage(pkg)
            val decision = safeModeManager.evaluateActiveApp(pkg)
            if (decision is SafeModeManager.SafeModeDecision.Blocked) {
                capturedScreenshot?.let { if (!it.isRecycled) it.recycle() }
                capturedScreenshot = null
                Toast.makeText(context, "Safe Mode: ${decision.reason}", Toast.LENGTH_LONG).show()
                hide()
                finish()
                return
            }
        }

        if (capturedScreenshot == null && assistTextBlocks.isEmpty()) {
            val lang = context.resources.configuration.locales[0]?.language?.lowercase()
            val msg = if (lang == "id" || lang == "in") {
                "Tidak dapat mengambil tangkapan layar (layar mungkin dilindungi FLAG_SECURE atau izin 'Gunakan tangkapan layar' di pengaturan Asisten belum aktif)."
            } else {
                "Unable to capture screen (the app may be protected by FLAG_SECURE or 'Use screenshot' is disabled in Assistant settings)."
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            hide()
            finish()
            return
        }

        SessionSnapshotStore.put(
            SessionSnapshotStore.SnapshotPayload(
                screenshot = capturedScreenshot,
                assistTextBlocks = assistTextBlocks,
                foregroundPackage = pkg
            )
        )
        capturedScreenshot = null // Ownership transferred to SessionSnapshotStore

        val intent = Intent(context, BubbleAssistantActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        runCatching {
            startAssistantActivity(intent)
        }.onFailure {
            context.startActivity(intent)
        }

        hide()
        finish()
    }

    override fun onHide() {
        mainHandler.removeCallbacks(fallbackDispatchRunnable)
        super.onHide()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(fallbackDispatchRunnable)
        capturedScreenshot?.let { if (!it.isRecycled) it.recycle() }
        capturedScreenshot = null
        super.onDestroy()
    }

    private fun processAssistStructure(structure: AssistStructure?) {
        if (structure == null) return
        foregroundPackage = structure.activityComponent?.packageName
        val results = mutableListOf<TextBlockResult>()
        val seen = HashSet<String>()

        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val rootNode = windowNode.rootViewNode ?: continue
            traverseAssistNode(
                node = rootNode,
                offsetX = windowNode.left,
                offsetY = windowNode.top,
                out = results,
                seen = seen
            )
        }
        assistTextBlocks = results
    }

    private fun traverseAssistNode(
        node: AssistStructure.ViewNode,
        offsetX: Int,
        offsetY: Int,
        out: MutableList<TextBlockResult>,
        seen: MutableSet<String>
    ) {
        if (node.visibility != View.VISIBLE) return
        if (isPasswordInputType(node.inputType)) return

        val absLeft = offsetX + node.left
        val absTop = offsetY + node.top
        val width = node.width
        val height = node.height

        val rawText = (node.text ?: node.contentDescription)?.toString()?.trim()
        if (!rawText.isNullOrEmpty() && rawText.length <= 600 && width > 4 && height > 4) {
            val key = "$rawText@$absLeft,$absTop"
            if ((node.childCount == 0 || !node.text.isNullOrBlank()) && seen.add(key)) {
                out.add(
                    TextBlockResult(
                        text = rawText,
                        boundingBox = RectF(
                            absLeft.toFloat(),
                            absTop.toFloat(),
                            (absLeft + width).toFloat(),
                            (absTop + height).toFloat()
                        ),
                        fromAccessibility = true
                    )
                )
            }
        }

        val childOffsetX = absLeft - node.scrollX
        val childOffsetY = absTop - node.scrollY
        for (i in 0 until node.childCount) {
            traverseAssistNode(node.getChildAt(i), childOffsetX, childOffsetY, out, seen)
        }
    }

    private fun isPasswordInputType(inputType: Int): Boolean {
        val klass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (klass) {
            InputType.TYPE_CLASS_TEXT ->
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER ->
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }
}
