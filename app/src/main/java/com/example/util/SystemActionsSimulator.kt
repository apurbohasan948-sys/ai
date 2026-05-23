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
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true to "Successfully launched $appName."
            } else {
                // General implicit intent fallback for known services if package launcher is missing
                val fallbackIntent = when (appName.lowercase()) {
                    "youtube" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    "facebook" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    "whatsapp" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    else -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(appName)}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                }
                context.startActivity(fallbackIntent)
                true to "Opening web preview of $appName."
            }
        } catch (e: Throwable) {
            false to "Could not launch $appName. Simulating launch state internally."
        }
    }

    fun openSystemSettings(): String {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Android system settings..."
        } catch (e: Throwable) {
            "Opening Settings simulation active."
        }
    }

    fun makePhoneCall(name: String, phoneNumber: String): String {
        return try {
            // Check if input is a valid digits number, otherwise clean it
            val cleanNum = phoneNumber.replace(Regex("[^0-9+#]"), "")
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNum")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Rerouting voice dialer to $name ($cleanNum)..."
        } catch (e: Throwable) {
            "Simulating dial to $name..."
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
        } catch (e: Throwable) {
            "Simulating SMS to $name: \"$body\""
        }
    }

    fun sendWhatsAppMessage(phoneNumber: String, body: String): String {
        return try {
            val cleanNum = phoneNumber.replace(Regex("[^0-9]"), "")
            // For contacts in Bangladesh, prepending the default country code 88 if input is a 11-digit local number
            val finalNum = if (cleanNum.length == 11 && cleanNum.startsWith("0")) "88$cleanNum" else cleanNum
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$finalNum&text=${Uri.encode(body)}")
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening WhatsApp chat with +$finalNum to send your message."
        } catch (e: Throwable) {
            // Fallback web url if package is missing
            try {
                val cleanNum = phoneNumber.replace(Regex("[^0-9]"), "")
                val fallbackUri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNum&text=${Uri.encode(body)}")
                val intentFallback = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intentFallback)
                "Routing WhatsApp dispatch via browser fallback."
            } catch (ex: Throwable) {
                "Simulating WhatsApp message: \"$body\""
            }
        }
    }

    fun sendEmail(emailAddress: String, subject: String, body: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$emailAddress")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening standard Email client to send message to $emailAddress..."
        } catch (e: Exception) {
            "Simulated pre-filled email window for $emailAddress."
        }
    }

    fun postToFacebook(text: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage("com.facebook.katana")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Launching Facebook post composer..."
        } catch (e: Exception) {
            try {
                val intentShare = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intentShare, "Post to Social").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                "Launching standard social share options..."
            } catch (ex: Exception) {
                "Simulating Facebook post deployment text: \"$text\""
            }
        }
    }

    fun postToInstagram(text: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage("com.instagram.android")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Launching Instagram post composer..."
        } catch (e: Exception) {
            try {
                val intentShare = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intentShare, "Share visual content").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                "Opening media sharing composer..."
            } catch (ex: Exception) {
                "Simulating Instagram post..."
            }
        }
    }

    fun openWebSearch(query: String): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Google web index for \"$query\"."
        } catch (e: Exception) {
            "Simulating web search for \"$query\""
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
        } catch (e: Throwable) {
            "System volume updated ${if (increase) "up" else "down"}."
        }
    }

    fun toggleWifi(turnOn: Boolean): String {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening WiFi Panel to allow toggle ${if (turnOn) "ON" else "OFF"}."
        } catch (e: Throwable) {
            "WiFi simulation updated to: ${if (turnOn) "ON" else "OFF"}"
        }
    }

    fun toggleBluetooth(turnOn: Boolean): String {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Launching Bluetooth preferences Panel for standard state toggle."
        } catch (e: Throwable) {
            "Bluetooth state simulation updated to: ${if (turnOn) "ON" else "OFF"}"
        }
    }

    fun triggerBrightnessAdjustment(increase: Boolean): String {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening Display Panel to let you set brightness ${if (increase) "higher" else "lower"}."
        } catch (e: Throwable) {
            "Simulated screen brightness ${if (increase) "increased" else "decreased"}."
        }
    }
}
