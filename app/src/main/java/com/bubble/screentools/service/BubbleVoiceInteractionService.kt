package com.bubble.screentools.service

import android.service.voice.VoiceInteractionService

/**
 * Allows Bubble to be set as the Android Default Digital Assistant
 * (similar to CircleToSearch v0.5), enabling instant screenshot capture
 * via Home long-press or corner swipe with ZERO screen-recording prompt
 * and without requiring AccessibilityService.
 */
class BubbleVoiceInteractionService : VoiceInteractionService()
