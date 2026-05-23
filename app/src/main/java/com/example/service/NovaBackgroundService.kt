package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import java.util.*

class NovaBackgroundService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val channelId = "nova_background_channel"
    private val notificationId = 1845

    override fun onCreate() {
        super.onCreate()
        Log.d("NovaBgService", "Background listener service created")
        createNotificationChannel()
        startForeground(notificationId, createNotification())
        initRecognizer()
        startListeningLoop()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Nova Background Listening Channel"
            val descriptionText = "Keeps Nova Companion listening offline for wake commands"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("নোভা সোনা ব্যাকগ্রাউন্ডে সক্রিয়")
            .setContentText("অন্য অ্যাপে থাকলেও \"হেই নোভা\" বা \"Hey Nova\" বললে সাড়া দেব সোনা!")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun initRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {
                        // Completely mute platform start chime/bip sounds
                        silenceSpeechBeep(true)
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        silenceSpeechBeep(false)
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        silenceSpeechBeep(false)
                        Log.w("NovaBgService", "Background listener error code: $error")
                        restartListeningDeferred()
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        silenceSpeechBeep(false)
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spoken = matches?.firstOrNull()?.lowercase(Locale.ROOT) ?: ""
                        Log.d("NovaBgService", "Background spoken match: $spoken")
                        
                        val isTriggered = spoken.contains("hey nova") || 
                                          spoken.contains("hi nova") || 
                                          spoken.contains("nova") || 
                                          spoken.contains("নোভা") || 
                                          spoken.contains("হেই নোভা") || 
                                          spoken.contains("হাই নোভা") || 
                                          spoken.contains("হে নোভা")

                        if (isTriggered) {
                            Log.d("NovaBgService", "Background wake word matching hit!")
                            wakeUpAndLaunchApp()
                        } else {
                            restartListeningDeferred()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun silenceSpeechBeep(mute: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (mute) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true)
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false)
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, false)
                }
            }
        } catch (e: Exception) {
            Log.e("NovaBgService", "Error toggling beep silence", e)
        }
    }

    private fun startListeningLoop() {
        if (speechRecognizer == null) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "bn-BD")
                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("bn-BD", "en-US"))
            }
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            Log.e("NovaBgService", "Failed starting bg speech listener", e)
            restartListeningDeferred()
        }
    }

    private fun restartListeningDeferred() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isListening) {
                startListeningLoop()
            }
        }, 1200)
    }

    private fun wakeUpAndLaunchApp() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("WAKE_UP_TRIGGERED", true)
            }
            startActivity(intent)
            Log.d("NovaBgService", "MainActivity brought to foreground from background trigger!")
        } catch (e: Exception) {
            Log.e("NovaBgService", "Error launching activity from backdrop", e)
        }
        restartListeningDeferred()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        Log.d("NovaBgService", "Background listener service destroyed")
    }
}
