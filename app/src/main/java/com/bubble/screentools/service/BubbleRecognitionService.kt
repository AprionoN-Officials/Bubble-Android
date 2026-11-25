package com.bubble.screentools.service

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Lightweight RecognitionService required by Android's RoleManager (android.app.role.ASSISTANT)
 * and VoiceInteractionServiceInfo parser so Bubble appears in Settings -> Default Digital Assistant App.
 * Bubble focuses on screen translation & visual search rather than microphone speech recognition.
 */
class BubbleRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}
