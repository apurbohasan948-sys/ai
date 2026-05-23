package com.example.util

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TextToSpeechManager(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isInitializing = false

    private fun isTtsEngineAvailable(ctx: Context): Boolean {
        return try {
            val pm = ctx.packageManager
            val intent = Intent("android.intent.action.TTS_SERVICE")
            val list = pm.queryIntentServices(intent, 0)
            !list.isNullOrEmpty()
        } catch (e: Throwable) {
            false
        }
    }

    private fun initTtsIfNeeded() {
        if (tts != null || isInitializing) return
        
        if (!isTtsEngineAvailable(context)) {
            Log.w("TTS", "No TTS engine found on this device. Muting audio speech outputs.")
            return
        }

        isInitializing = true
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                try {
                    if (status == TextToSpeech.SUCCESS) {
                        // Detect if Bengali local is supported, otherwise fallback to standard default Locale
                        val bngLocale = Locale("bn", "BD")
                        val langResult = tts?.setLanguage(bngLocale)
                        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts?.setLanguage(Locale.US)
                        }
                        
                        // Sweet human-like girlfriend voice tuning adjustments
                        tts?.setSpeechRate(0.82f) // Slower, relaxed, extremely clear and easily understandable pace
                        tts?.setPitch(1.23f)      // Gently raised warm, soft female tone pitch
                        
                        isInitialized = true
                        Log.d("TTS", "Sweet Assistant TTS system online and configured.")
                    } else {
                        Log.e("TTS", "Failed to initialize TTS engine with status: $status")
                    }
                } catch (t: Throwable) {
                    Log.e("TTS", "Exception during tts callback initialization", t)
                    isInitialized = false
                } finally {
                    isInitializing = false
                }
            }
        } catch (e: Throwable) {
            Log.e("TTS", "Could not instantiate TextToSpeech; device might not support TTS engine.", e)
            isInitialized = false
            isInitializing = false
        }
    }

    fun speak(text: String) {
        try {
            initTtsIfNeeded()
            if (isInitialized && tts != null) {
                // Dynamically select locale based on presence of Bengali characters
                val hasBengali = text.any { it in '\u0980'..'\u09FF' }
                if (hasBengali) {
                    tts?.setLanguage(Locale("bn", "BD"))
                } else {
                    tts?.setLanguage(Locale.US)
                }
                
                // Re-enforce optimal gentle reading metrics
                tts?.setSpeechRate(0.82f) 
                tts?.setPitch(1.23f)
                
                // Clean speech format to remove tech/log tags
                val cleanedText = text
                    .replace(Regex("\\[[^\\]]*\\]"), "") // Remove [Local Engine] brackets for natural voice text
                    .replace(Regex("(?i)system alert:|error:"), "")
                    .trim()
                
                tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, "NovaTTS")
            } else {
                Log.d("TTS", "Locally muted speak (TTS offline): $text")
            }
        } catch (e: Throwable) {
            Log.e("TTS", "Error during tts.speak for: $text", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Throwable) {
            Log.e("TTS", "Error shutting down TextToSpeech", e)
        }
    }
}
