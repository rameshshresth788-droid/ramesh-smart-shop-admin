package com.rameshai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Default [TextToSpeechEngine] using Android's built-in TTS. Kept behind the
 * interface so a future provider (e.g. a cloud TTS with more natural Hindi/
 * Hinglish voices) can be swapped in without touching [com.rameshai.core.AssistantOrchestrator]
 * or the UI.
 */
class AndroidTextToSpeechEngine(context: Context) : TextToSpeechEngine {

    private var ready = false
    private var pendingRate = 1.0f
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts.setSpeechRate(pendingRate)
        }
    }

    override fun speak(text: String, languageTag: String, onDone: () -> Unit) {
        if (!ready) {
            onDone()
            return
        }
        val requested = Locale.forLanguageTag(languageTag)
        val result = tts.setLanguage(requested)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = if (requested.language == "hi") Locale("hi", "IN") else Locale.US
            tts.setLanguage(fallback)
        }
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onDone() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { onDone() }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        if (::tts.isInitialized) tts.stop()
    }

    override fun setSpeechRate(rate: Float) {
        pendingRate = rate
        if (ready) tts.setSpeechRate(rate)
    }

    override fun release() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
    }
}
