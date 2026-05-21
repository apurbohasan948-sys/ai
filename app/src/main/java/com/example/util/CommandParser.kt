package com.example.util

import com.example.data.Contact
import java.util.Locale

enum class ActionType {
    NONE,
    OPEN_APP,
    CALL,
    SEND_MESSAGE,
    ADJUST_VOLUME,
    ADJUST_BRIGHTNESS,
    TOGGLE_WIFI,
    TOGGLE_BLUETOOTH,
    SCROLL,
    LIST_REMINDERS,
    TAKE_SCREENSHOT,
    COMPANION_TALK,
    GENERAL_AI
}

data class ParsedCommand(
    val hasWakeWord: Boolean,
    val matchedWakeWord: String,
    val cleanCommand: String,
    val actionType: ActionType,
    val args: Map<String, Any> = emptyMap(),
    val responseText: String
)

object CommandParser {

    private val WAKE_WORDS = listOf("hey nova", "hi nova", "nova", "hey assistant")

    private val JOKES = listOf(
        "Why don't scientists trust atoms? Because they make up everything!",
        "Why did the smartphone go to school? To improve its smarts and get a smart degree!",
        "What do you call a computer that sings? A Dell!",
        "How many programmers does it take to change a light bulb? None, that's a hardware problem!",
        "Why do programmers prefer dark mode? Because light attracts bugs!"
    )

    private val MOTIVATIONS = listOf(
        "Believe you can and you're halfway there. Keep pushing!",
        "The only way to do great work is to love what you do.",
        "Success is not final, failure is not fatal: it is the courage to continue that counts.",
        "Your future is created by what you do today, not tomorrow.",
        "You are capable of doing amazing things!"
    )

    fun parse(input: String, contacts: List<Contact>): ParsedCommand {
        val lowerInput = input.trim().lowercase(Locale.ROOT)
        
        // 1. Detect wake words
        var matchedWakeWord = ""
        var cleanText = lowerInput
        var hasWakeOn = false

        for (wake in WAKE_WORDS) {
            if (lowerInput.startsWith(wake)) {
                matchedWakeWord = wake
                hasWakeOn = true
                cleanText = lowerInput.drop(wake.length).trim()
                // If it was just "Hey Nova" or "Nova"
                if (cleanText.startsWith(",") || cleanText.startsWith("?")) {
                    cleanText = cleanText.drop(1).trim()
                }
                break
            }
        }

        if (cleanText.isEmpty()) {
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = "",
                actionType = ActionType.COMPANION_TALK,
                args = mapOf("sub" to "greet"),
                responseText = "Yes? I’m listening. Tell me what to do!"
            )
        }

        // 2. Classify actions
        // Open Apps
        if (cleanText.startsWith("open ")) {
            val appTarget = cleanText.drop(5).trim()
            val (pkg, name) = getAppDetails(appTarget)
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.OPEN_APP,
                args = mapOf("appName" to name, "packageName" to pkg),
                responseText = "Opening $name."
            )
        }

        // Call contact
        if (cleanText.startsWith("call ")) {
            val contactTarget = cleanText.drop(5).trim()
            val matchedContact = findContact(contactTarget, contacts)
            return if (matchedContact != null) {
                ParsedCommand(
                    hasWakeWord = hasWakeOn,
                    matchedWakeWord = matchedWakeWord,
                    cleanCommand = cleanText,
                    actionType = ActionType.CALL,
                    args = mapOf("contactName" to matchedContact.name, "phoneNumber" to matchedContact.phoneNumber),
                    responseText = "Calling ${matchedContact.name}..."
                )
            } else {
                ParsedCommand(
                    hasWakeWord = hasWakeOn,
                    matchedWakeWord = matchedWakeWord,
                    cleanCommand = cleanText,
                    actionType = ActionType.CALL,
                    args = mapOf("contactName" to contactTarget, "phoneNumber" to "simulated"),
                    responseText = "Calling $contactTarget (Simulating local dialer contact)..."
                )
            }
        }

        // Send messages (WhatsApp / SMS)
        if (cleanText.contains("message ") || cleanText.contains("whatsapp ") || cleanText.contains("sms ")) {
            val isWhatsApp = cleanText.contains("whatsapp")
            val isSms = cleanText.contains("sms")
            
            // Try extracting contact name
            var contactName = "Mom"
            var messageBody = "Hello from Nova assistant!"

            // Regex or keyword search
            val messageToRegex = Regex("(?:send a )?(?:whatsapp|sms|message)?(?: message)? to (\\w+)(?: with)?(?: body)? ?(.*)")
            val match = messageToRegex.find(cleanText)
            
            if (match != null) {
                contactName = match.groupValues.getOrNull(1) ?: "Mom"
                val bodyText = match.groupValues.getOrNull(2) ?: ""
                if (bodyText.isNotEmpty()) messageBody = bodyText
            } else {
                // simple fallback
                for (c in contacts) {
                    if (cleanText.contains(c.name.lowercase())) {
                        contactName = c.name
                        break
                    }
                }
            }

            val targetContact = findContact(contactName, contacts)
            val number = targetContact?.phoneNumber ?: "+1-555-0100"
            val actualContactName = targetContact?.name ?: contactName

            val destination = if (isWhatsApp) "WhatsApp" else "SMS"
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.SEND_MESSAGE,
                args = mapOf(
                    "contactName" to actualContactName,
                    "phoneNumber" to number,
                    "body" to messageBody,
                    "platform" to destination
                ),
                responseText = "Sending $destination message to $actualContactName: \"$messageBody\""
            )
        }

        // WiFi
        if (cleanText.contains("wifi") || cleanText.contains("wi-fi")) {
            val turnOn = !cleanText.contains("off")
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.TOGGLE_WIFI,
                args = mapOf("on" to turnOn),
                responseText = "Turning ${if (turnOn) "on" else "off"} WiFi."
            )
        }

        // Bluetooth
        if (cleanText.contains("bluetooth")) {
            val turnOn = !cleanText.contains("off")
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.TOGGLE_BLUETOOTH,
                args = mapOf("on" to turnOn),
                responseText = "Turning ${if (turnOn) "on" else "off"} Bluetooth."
            )
        }

        // Volume adjustments
        if (cleanText.contains("volume")) {
            val raise = cleanText.contains("up") || cleanText.contains("increase") || cleanText.contains("raise") || cleanText.contains("higher")
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.ADJUST_VOLUME,
                args = mapOf("increase" to raise),
                responseText = "Adjusting media volume ${if (raise) "up" else "down"}."
            )
        }

        // Brightness adjustments
        if (cleanText.contains("brightness") || cleanText.contains("screen")) {
            val raise = cleanText.contains("up") || cleanText.contains("increase") || cleanText.contains("brighter") || cleanText.contains("higher")
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.ADJUST_BRIGHTNESS,
                args = mapOf("increase" to raise),
                responseText = "Adjusting screen brightness ${if (raise) "higher" else "lower"}."
            )
        }

        // Scroll
        if (cleanText.startsWith("scroll")) {
            val down = !cleanText.contains("up")
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.SCROLL,
                args = mapOf("down" to down),
                responseText = "Performing accessibility scroll ${if (down) "down" else "up"}."
            )
        }

        // List reminders
        if (cleanText.contains("reminder") || cleanText.contains("schedule") || cleanText.contains("todo")) {
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.LIST_REMINDERS,
                responseText = "Here are your active offline reminders."
            )
        }

        // Screenshot
        if (cleanText.contains("screenshot") || cleanText.contains("capture screen")) {
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.TAKE_SCREENSHOT,
                responseText = "Capturing screen display state."
            )
        }

        // Joke companion
        if (cleanText.contains("joke") || cleanText.contains("funny") || cleanText.contains("laugh")) {
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.COMPANION_TALK,
                args = mapOf("sub" to "joke"),
                responseText = JOKES.random()
            )
        }

        // Motivation companion
        if (cleanText.contains("motivate") || cleanText.contains("quote") || cleanText.contains("inspiration")) {
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.COMPANION_TALK,
                args = mapOf("sub" to "motivation"),
                responseText = MOTIVATIONS.random()
            )
        }

        // Short casual greetings
        if (cleanText.contains("hello") || cleanText.contains("hi nova") || cleanText.contains("how are you")) {
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.COMPANION_TALK,
                args = mapOf("sub" to "casual"),
                responseText = "I'm doing stellar! It's super pleasant to spend time with you offline today. How can I assist you with offline routines or voice commands?"
            )
        }

        // General AI / search simulation fallback
        return ParsedCommand(
            hasWakeWord = hasWakeOn,
            matchedWakeWord = matchedWakeWord,
            cleanCommand = cleanText,
            actionType = ActionType.GENERAL_AI,
            responseText = "Processing command in hybrid AI core: $cleanText..."
        )
    }

    private fun findContact(query: String, contacts: List<Contact>): Contact? {
        val lowerQuery = query.trim().lowercase()
        return contacts.firstOrNull { it.name.lowercase().contains(lowerQuery) }
    }

    private fun getAppDetails(appName: String): Pair<String, String> {
        return when (appName.lowercase(Locale.ROOT)) {
            "youtube" -> "com.google.android.youtube" to "YouTube"
            "whatsapp", "whats app" -> "com.whatsapp" to "WhatsApp"
            "facebook", "fb" -> "com.facebook.katana" to "Facebook"
            "settings" -> "com.android.settings" to "Settings"
            "calendar" -> "com.google.android.calendar" to "Calendar"
            "messenger" -> "com.facebook.orca" to "Facebook Messenger"
            "wikipedia" -> "org.wikipedia" to "Wikipedia"
            "chrome" -> "com.android.chrome" to "Google Chrome"
            else -> "com.android.chrome" to appName.replaceFirstChar { it.uppercase() }
        }
    }
}
