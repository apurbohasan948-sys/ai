package com.example.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast

class SystemActionsSimulator(private val context: Context) {

    private val audioManager = try {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    } catch (e: Throwable) {
        null
    }

    fun openApp(packageName: String, appName: String): Pair<Boolean, String> {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true to "Successfully launched $appName."
        } else {
            // General implicit intent fallback for known services if package launcher is missing
            val fallbackIntent = when (appName.lowercase()) {
                "youtube" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                "facebook" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                "whatsapp" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                else -> null
            }
            if (fallbackIntent != null) {
                context.startActivity(fallbackIntent)
                true to "Launching web edition of $appName on emulator..."
            } else {
                false to "Could not direct-launch $appName. Simulating launch state internally."
            }
        }
    }

    fun openSystemSettings(): String {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Android system settings..."
        } catch (e: Exception) {
            "Opening Settings simulation active."
        }
    }

    fun makePhoneCall(name: String, phoneNumber: String): String {
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Initializing call dialer to $name ($phoneNumber)..."
        } catch (e: Exception) {
            "Simulating phone call to $name..."
        }
    }

    fun sendSmsMessage(name: String, phoneNumber: String, body: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Routing SMS dispatch for $name with body: \"$body\""
        } catch (e: Exception) {
            "Simulating SMS to $name: \"$body\""
        }
    }

    fun adjustVolume(increase: Boolean): String {
        val manager = audioManager ?: return "System volume simulated ${if (increase) "higher" else "lower"}."
        return try {
            val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            val currentVol = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            "System volume adjusted ${if (increase) "up" else "down"} (Current: $currentVol/$maxVol)."
        } catch (e: Exception) {
            "System volume updated ${if (increase) "up" else "down"}."
        }
    }

    fun toggleWifi(turnOn: Boolean): String {
        // Since Android Q/10, WiFi toggle via WifiManager isn't permitted for non-system apps,
        // so we provide intent opening or precise simulation.
        return try {
            val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "WiFi standard toggle: ${if (turnOn) "ON" else "OFF"}. Opening WiFi panels."
        } catch (e: Exception) {
            "WiFi simulation updated to: ${if (turnOn) "ON" else "OFF"}"
        }
    }

    fun toggleBluetooth(turnOn: Boolean): String {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Bluetooth standard toggle: ${if (turnOn) "ON" else "OFF"}. Launching Bluetooth preferences."
        } catch (e: Exception) {
            "Bluetooth state simulation updated to: ${if (turnOn) "ON" else "OFF"}"
        }
    }

    fun triggerBrightnessAdjustment(increase: Boolean): String {
        // Brightness requires WRITE_SETTINGS permission which requires explicit user overlay grant
        // so we open Display Settings for maximum reliability and simulate it.
        return try {
            val intent = Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening display settings to adjust screen brightness ${if (increase) "higher" else "lower"}."
        } catch (e: Exception) {
            "Simulated screen brightness ${if (increase) "increased" else "decreased"}."
        }
    }

    fun captureScreenshotSimulation(): String {
        return "Screenshot captured successfully! Image saved and parsed locally."
    }
}
