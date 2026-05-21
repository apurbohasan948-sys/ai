package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TextToSpeechManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Language is not supported or missing data")
                } else {
                    isInitialized = true
                    Log.d("TTS", "TTS system online and initialized.")
                }
            } else {
                Log.e("TTS", "Failed to initialize TTS engine")
            }
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NovaTTS")
        } else {
            Log.d("TTS", "Muted speak (TTS uninitialized): $text")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
