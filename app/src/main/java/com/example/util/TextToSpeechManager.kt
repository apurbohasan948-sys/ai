package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Build
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
                        tts?.setSpeechRate(0.85f) // Relaxed, sweet and extremely clear pace
                        tts?.setPitch(1.25f)      // Soft, sweet raised feminine tone pitch
                        
                        // Select best female Voice object dynamically
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            try {
                                val voiceList = tts?.voices
                                if (!voiceList.isNullOrEmpty()) {
                                    val preferredVoice = voiceList.firstOrNull { voice ->
                                        voice.locale.language == "bn" && (voice.name.lowercase().contains("female") || voice.name.lowercase().contains("f-") || voice.name.lowercase().contains("local"))
                                    } ?: voiceList.firstOrNull { voice ->
                                        voice.locale.language == "en" && (voice.name.lowercase().contains("female") || voice.name.lowercase().contains("f-"))
                                    }
                                    if (preferredVoice != null) {
                                        tts?.voice = preferredVoice
                                        Log.d("TTS", "Successfully configured premium female Voice: ${preferredVoice.name}")
                                    }
                                }
                            } catch (ve: Exception) {
                                Log.e("TTS", "Voice object assignment error skipped", ve)
                            }
                        }
                        
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
                tts?.setSpeechRate(0.85f) 
                tts?.setPitch(1.25f)
                
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
