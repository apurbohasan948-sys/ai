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
    POST_FACEBOOK,
    POST_INSTAGRAM,
    SEND_EMAIL,
    WEB_BROWSE,
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

    private val WAKE_WORDS = listOf(
        "hey nova", "hi nova", "nova", "hey assistant",
        "হে নোভা", "হাই নোভা", "নোভা", "হে অ্যাসিস্ট্যান্ট"
    )

    private val JOKES_EN = listOf(
        "Why don't scientists trust atoms? Because they make up everything!",
        "Why did the smartphone go to school? To get a smart degree!",
        "What do you call a computer that sings? A Dell!",
        "How many programmers does it take to change a light bulb? None, that's a hardware problem!",
        "Why do programmers prefer dark mode? Because light attracts bugs!"
    )

    private val JOKES_BN = listOf(
        "বল্টু হসপিটালে ডাক্তারকে বলল: ডক্টর, আমার দুটো চোখই একটু চেক করুন। ডাক্তার চোখ দেখে বললেন: আপনার বাম চোখটি ভালো, কিন্তু ডান চোখে একটু প্রবলেম হছে। বল্টু রেগে বলল: তা তো হবেই, ওটা দিয়ে শুধু ডানদিকের মানুষ দেখি!",
        "এক মাতাল আরেক মাতালকে বলছে: দোস্ত, দেখ আকাশে তিনটি চাঁদ! দ্বিতীয় মাতাল একটু ভেবে বলল: কোন লাইনের চাঁদ কথা বলছিস? বাম লাইনেরটা নাকি ডান লাইনেরটা?",
        "শিক্ষক: বল্টু বলতো, পৃথিবী কত বড়? বল্টু: স্যার, আমার বাবার গোল গোল আলুর সাইজের মতো গোলাকার আর অনেক বড়!"
    )

    private val MOTIVATIONS_EN = listOf(
        "Believe you can and you're halfway there. Keep pushing!",
        "The only way to do great work is to love what you do.",
        "Success is not final, failure is not fatal: it is the courage to continue that counts.",
        "Your future is created by what you do today, not tomorrow.",
        "You are capable of doing amazing things!"
    )

    private val MOTIVATIONS_BN = listOf(
        "তুমি পারবে, কারণ তোমার মধ্যে অসীম সম্ভাবনা রয়েছে। এগিয়ে যাও!",
        "সাফল্য কোনো শেষ নয়, ব্যর্থতা কোনো মৃত্যু নয়; এগিয়ে যাওয়ার সাহসই হলো আসল শক্তি।",
        "আজকের পরিশ্রমই তোমার আগামীকালের উজ্জ্বল ভবিষ্যত গড়ে তুলবে।"
    )

    fun parse(input: String, contacts: List<Contact>): ParsedCommand {
        val lowerInput = input.trim().lowercase(Locale.ROOT)
        
        // 1. Detect wake words (English & Bengali)
        var matchedWakeWord = ""
        var cleanText = lowerInput
        var hasWakeOn = false

        for (wake in WAKE_WORDS) {
            if (lowerInput.startsWith(wake)) {
                matchedWakeWord = wake
                hasWakeOn = true
                cleanText = lowerInput.drop(wake.length).trim()
                // Drop initial comma or question marks
                if (cleanText.startsWith(",") || cleanText.startsWith("?") || cleanText.startsWith("।")) {
                    cleanText = cleanText.drop(1).trim()
                }
                break
            }
        }

        if (cleanText.isEmpty()) {
            val isBengali = input.contains(Regex("[\\u0980-\\u09FF]")) || input.contains("নোভা")
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = "",
                actionType = ActionType.COMPANION_TALK,
                args = mapOf("sub" to "greet"),
                responseText = if (isBengali) "হাঁ, আমি শুনছি। বলুন কী করতে পারি?" else "Yes? I’m listening. Tell me what to do!"
            )
        }

        val isBengali = cleanText.contains(Regex("[\\u0980-\\u09FF]"))

        // --- Classify multilingual actions ---

        // A. OPEN APP (ওপেন করো, চালু করো, খুলো, open)
        if (cleanText.startsWith("open ") || cleanText.startsWith("চালু করো ") || cleanText.contains("ওপেন করো") || cleanText.contains("খুলো") || cleanText.contains("ওপেন কর")) {
            var appTarget = ""
            if (cleanText.startsWith("open ")) {
                appTarget = cleanText.drop(5).trim()
            } else {
                appTarget = cleanText
                    .replace("চালু করো", "")
                    .replace("ওপেন করো", "")
                    .replace("ওপেন কর", "")
                    .replace("খুলো", "")
                    .replace("খুলুন", "")
                    .trim()
            }
            val (pkg, name) = getAppDetails(appTarget)
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.OPEN_APP,
                args = mapOf("appName" to name, "packageName" to pkg),
                responseText = if (isBengali) "$name ওপেন করা হচ্ছে।" else "Opening $name."
            )
        }

        // B. CALL (কল করো, ফোন দাও, ফোন করো, call)
        if (cleanText.startsWith("call ") || cleanText.contains("কল করো") || cleanText.contains("ফোন করো") || cleanText.contains("ফোন দাও") || cleanText.contains("কল কর")) {
            var contactTarget = ""
            if (cleanText.startsWith("call ")) {
                contactTarget = cleanText.drop(5).trim()
            } else {
                contactTarget = cleanText
                    .replace("কল করো", "")
                    .replace("ফোন করো", "")
                    .replace("ফোন দাও", "")
                    .replace("কল কর", "")
                    .replace("কে", "")
                    .trim()
            }
            val matchedContact = findContact(contactTarget, contacts)
            return if (matchedContact != null) {
                ParsedCommand(
                    hasWakeWord = hasWakeOn,
                    matchedWakeWord = matchedWakeWord,
                    cleanCommand = cleanText,
                    actionType = ActionType.CALL,
                    args = mapOf("contactName" to matchedContact.name, "phoneNumber" to matchedContact.phoneNumber),
                    responseText = if (isBengali) "${matchedContact.name} কে কল করা হচ্ছে..." else "Calling ${matchedContact.name}..."
                )
            } else {
                ParsedCommand(
                    hasWakeWord = hasWakeOn,
                    matchedWakeWord = matchedWakeWord,
                    cleanCommand = cleanText,
                    actionType = ActionType.CALL,
                    args = mapOf("contactName" to contactTarget, "phoneNumber" to contactTarget),
                    responseText = if (isBengali) "$contactTarget নাম্বারে কল করা হচ্ছে..." else "Calling $contactTarget (Simulated dialer)..."
                )
            }
        }

        // C. SEND MESSAGE via WHATSAPP (হোয়াটসঅ্যাপ বার্তা, হোয়াটসঅ্যাপ বা মেসেজ করো, send whatsapp, send messaging)
        if (cleanText.contains("whatsapp") || cleanText.contains("হোয়াটসঅ্যাপ") || cleanText.contains("হোয়াটস অ্যাপ") || cleanText.contains("ওয়াটসাপ")) {
            var contactName = "Mom"
            var messageBody = "Hello from Nova assistant!"

            // Regex or keyword search
            val matches = Regex("(?:send a )?(?:whatsapp|message)?(?: to )?(\\w+)(?: with)?(?: body)? ?(.*)").find(cleanText)
            if (matches != null) {
                val extracted = matches.groupValues.getOrNull(1) ?: "Mom"
                if (extracted.lowercase() != "whatsapp") {
                    contactName = extracted
                }
                val bodyText = matches.groupValues.getOrNull(2) ?: ""
                if (bodyText.isNotEmpty()) messageBody = bodyText
            } else {
                for (c in contacts) {
                    if (cleanText.contains(c.name.lowercase())) {
                        contactName = c.name
                        break
                    }
                }
            }

            // Extract Bengali message targets
            if (isBengali) {
                contactName = cleanText
                    .replace(Regex("(হোয়াটসঅ্যাপ|মেসেজ|পাঠাও|করো|কে|বডি|লিখে|পাঠা|কর)"), "")
                    .trim()
                if (contactName.isEmpty()) contactName = "Rahim"
                messageBody = "নোভা অ্যাসিস্ট্যান্ট এর পক্ষ থেকে শুভকামনা!"
            }

            val targetContact = findContact(contactName, contacts)
            val number = targetContact?.phoneNumber ?: contactName
            val actualContactName = targetContact?.name ?: contactName

            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.SEND_MESSAGE,
                args = mapOf(
                    "contactName" to actualContactName,
                    "phoneNumber" to number,
                    "body" to messageBody,
                    "platform" to "WhatsApp"
                ),
                responseText = if (isBengali) "$actualContactName কে হোয়াটসঅ্যাপ মেসেজ পাঠানো হচ্ছে: \"$messageBody\"" 
                               else "Sending WhatsApp message to $actualContactName: \"$messageBody\""
            )
        }

        // D. EMAIL (মেইল করো, ইমেল পাঠাও, ইমেইল করো, email, mail)
        if (cleanText.contains("email") || cleanText.contains("mail") || cleanText.contains("ইমেল") || cleanText.contains("মেইল")) {
            var contactName = ""
            var subject = "Nova Voice Dispatch"
            var body = "Sent via Nova Multilingual Voice Assistant."

            // extract contact name
            contactName = cleanText
                .replace(Regex("(email|mail|send|to|ইমেল|মেইল|করো|কর|পাঠাও|পাঠা|কে)"), "")
                .trim()
            if (contactName.isEmpty()) contactName = "Manager"

            val targetContact = findContact(contactName, contacts)
            val email = targetContact?.email?.ifEmpty { "default@example.com" } ?: "contact@example.com"
            val actualName = targetContact?.name ?: contactName

            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.SEND_EMAIL,
                args = mapOf(
                    "recipient" to actualName,
                    "email" to email,
                    "subject" to subject,
                    "body" to body
                ),
                responseText = if (isBengali) "$actualName কে মেইল পাঠানো হচ্ছে ($email)..." else "Preparing email dispatch to $actualName ($email)..."
            )
        }

        // E. SOCIAL MEDIA POSTING - Facebook & Instagram (ফেসবুক পোস্ট, ইন্সটাগ্রাম পোস্ট, post on facebook)
        if (cleanText.contains("facebook post") || cleanText.contains("পোস্ট করো ফেসবুকে") || cleanText.contains("ফেসবুকে পোস্ট")) {
            val postText = cleanText
                .replace(Regex("(facebook post|post on facebook|ফেসবুকে পোস্ট করো|পোস্ট করো ফেসবুকে|ফেসবুক পোস্ট)"), "")
                .trim()
                .ifEmpty { "Enjoying my day with Nova Voice Assistant! #AI" }

            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.POST_FACEBOOK,
                args = mapOf("text" to postText),
                responseText = if (isBengali) "ফেসবুকে পোস্ট করা হচ্ছে: \"$postText\"" else "Posting to Facebook: \"$postText\""
            )
        }
        if (cleanText.contains("instagram post") || cleanText.contains("পোস্ট করো ইন্সটাগ্রামে") || cleanText.contains("ইন্সটাগ্রামে পোস্ট")) {
            val postText = cleanText
                .replace(Regex("(instagram post|post on instagram|ইন্সটাগ্রামে পোস্ট করো|পোস্ট করো ইন্সটাগ্রামে|ইন্সটাগ্রাম পোস্ট)"), "")
                .trim()
                .ifEmpty { "Visual moments powered by Nova Assistance #Inspire" }

            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.POST_INSTAGRAM,
                args = mapOf("text" to postText),
                responseText = if (isBengali) "ইন্সটাগ্রামে ছবি/ভিডিও পোস্ট করার উইন্ডো খোলা হচ্ছে..." else "Opening Instagram posting assistant..."
            )
        }

        // F. WEB BROWSE / SEARCH (ব্রাউজ করো, সার্চ করো, গুগল করো, search, browse, google)
        if (cleanText.contains("search") || cleanText.contains("browse") || cleanText.contains("google") ||
            cleanText.contains("সার্চ") || cleanText.contains("গুগল") || cleanText.contains("ব্রাউজ") || cleanText.contains("উইকিপিডিয়া")) {
            val query = cleanText
                .replace(Regex("(search google for|search for|google|browse|goggles|সার্চ করো|গুগল করো|সার্চ কর|ব্রাউজ করো|সম্পর্কে বলুন|সম্পর্কে জানাও)"), "")
                .trim()
                .ifEmpty { "Nova voice control" }

            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.WEB_BROWSE,
                args = mapOf("query" to query),
                responseText = if (isBengali) "\"$query\" লিখে গুগল সার্চ খোলা হচ্ছে..." else "Searching web for \"$query\"..."
            )
        }

        // G. WRITE SMS (মেসেজ করো, sms করো, send normal sms, send text)
        if (cleanText.contains("sms") || cleanText.contains("ছমছ") || cleanText.contains("মেসেজ পাঠাও") || cleanText.contains("মেসেজ কর")) {
            var contactName = "Friend"
            var messageBody = "Hello from Nova!"

            contactName = cleanText
                .replace(Regex("(sms|send|message|to|মেসেজ|পাঠাও|করো|কর|কে)"), "")
                .trim()
                if (contactName.isEmpty()) contactName = "Friend"

            val targetContact = findContact(contactName, contacts)
            val number = targetContact?.phoneNumber ?: contactName
            val actualName = targetContact?.name ?: contactName

            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.SEND_MESSAGE,
                args = mapOf(
                    "contactName" to actualName,
                    "phoneNumber" to number,
                    "body" to messageBody,
                    "platform" to "SMS"
                ),
                responseText = if (isBengali) "$actualName কে এসএমএস পাঠানো হচ্ছে: \"$messageBody\"" else "Sending SMS message to $actualName: \"$messageBody\""
            )
        }

        // H. WIFI Toggle (ওয়াইফাই চালু করো, ওয়াইফাই বন্ধ করো)
        if (cleanText.contains("wifi") || cleanText.contains("wi-fi") || cleanText.contains("ওয়াইফাই") || cleanText.contains("ওয়াইফাই")) {
            val turnOn = !cleanText.contains("off") && !cleanText.contains("বন্ধ") && !cleanText.contains("অফ")
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.TOGGLE_WIFI,
                args = mapOf("on" to turnOn),
                responseText = if (isBengali) "ওয়াইফাই ${if (turnOn) "চালু" else "বন্ধ"} করা হচ্ছে।" else "Turning ${if (turnOn) "on" else "off"} WiFi."
            )
        }

        // I. BLUETOOTH Toggle (ব্লুটুথ চালু করো, ব্লুটুথ বন্ধ করো)
        if (cleanText.contains("bluetooth") || cleanText.contains("ব্লুটুথ") || cleanText.contains("ব্লুটুত")) {
            val turnOn = !cleanText.contains("off") && !cleanText.contains("বন্ধ") && !cleanText.contains("অফ")
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.TOGGLE_BLUETOOTH,
                args = mapOf("on" to turnOn),
                responseText = if (isBengali) "ব্লুটুথ ${if (turnOn) "চালু" else "বন্ধ"} করা হচ্ছে।" else "Turning ${if (turnOn) "on" else "off"} Bluetooth."
            )
        }

        // J. VOLUME Adjustments (ভলিউম বাড়াও, ভলিউম কমাও, সাউন্ড)
        if (cleanText.contains("volume") || cleanText.contains("ভলিউম") || cleanText.contains("সাউন্ড") || cleanText.contains("শব্দ")) {
            val raise = cleanText.contains("up") || cleanText.contains("increase") || cleanText.contains("বাড়াও") || cleanText.contains("বাড়িয়ে") || cleanText.contains("বেশি")
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.ADJUST_VOLUME,
                args = mapOf("increase" to raise),
                responseText = if (isBengali) "ভলিউম ${if (raise) "বাড়ানো" else "কমানো"} হচ্ছে।" else "Adjusting media volume ${if (raise) "up" else "down"}."
            )
        }

        // K. BRIGHTNESS Adjustments (ব্রাইটনেস বাড়াও, আলো বাড়াও/কমাও)
        if (cleanText.contains("brightness") || cleanText.contains("screen") || cleanText.contains("ব্রাইটনেস") || cleanText.contains("আলো")) {
            val raise = cleanText.contains("up") || cleanText.contains("brighter") || cleanText.contains("বাড়াও") || cleanText.contains("বাড়িয়ে") || cleanText.contains("বেশি")
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.ADJUST_BRIGHTNESS,
                args = mapOf("increase" to raise),
                responseText = if (isBengali) "ডিসপ্লের আলো ${if (raise) "বাড়ানো" else "কমানো"} হচ্ছে।" else "Adjusting screen brightness ${if (raise) "higher" else "lower"}."
            )
        }

        // L. SCREENSHOT (স্ক্রিনশট নাও, স্ক্রিনশট)
        if (cleanText.contains("screenshot") || cleanText.contains("capture screen") || cleanText.contains("স্ক্রিনশট") || cleanText.contains("ছবি তোলো স্ক্রিন")) {
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.TAKE_SCREENSHOT,
                responseText = if (isBengali) "ডিসপ্লের স্ক্রিনশট নেওয়া হচ্ছে..." else "Capturing active screen layout..."
            )
        }

        // M. SCROLL
        if (cleanText.startsWith("scroll") || cleanText.contains("স্ক্রল") || cleanText.contains("টানো")) {
            val down = !cleanText.contains("up") && !cleanText.contains("উপরে")
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.SCROLL,
                args = mapOf("down" to down),
                responseText = if (isBengali) "স্ক্রিন ${if (down) "নিচে" else "উপরে"} স্ক্রল করা হচ্ছে।" else "Performing accessibility scroll ${if (down) "down" else "up"}."
            )
        }

        // N. REMINDERS list
        if (cleanText.contains("reminder") || cleanText.contains("schedule") || cleanText.contains("রিমাইন্ডার") || cleanText.contains("রিমাইন্ড")) {
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.LIST_REMINDERS,
                responseText = if (isBengali) "আপনার সক্রিয় অফলাইন রিমাইন্ডারগুলো লোড করা হচ্ছে..." else "Listing active offline tasks..."
            )
        }

        // O. JOKES / MOTIVATIONS
        if (cleanText.contains("joke") || cleanText.contains("funny") || cleanText.contains("কৌতুক") || cleanText.contains("হাসাও") || cleanText.contains("মজা করো")) {
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.COMPANION_TALK,
                args = mapOf("sub" to "joke"),
                responseText = if (isBengali) JOKES_BN.random() else JOKES_EN.random()
            )
        }
        if (cleanText.contains("motivate") || cleanText.contains("quote") || cleanText.contains("উক্তি") || cleanText.contains("অনুপ্রেরণা")) {
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.COMPANION_TALK,
                args = mapOf("sub" to "motivation"),
                responseText = if (isBengali) MOTIVATIONS_BN.random() else MOTIVATIONS_EN.random()
            )
        }

        // P. GREETINGS
        if (cleanText.contains("hello") || cleanText.contains("hi") || cleanText.contains("কেমন আছো") || cleanText.contains("হাই") || cleanText.contains("হ্যালো")) {
            return ParsedCommand(
                hasWakeWord = hasWakeOn,
                matchedWakeWord = matchedWakeWord,
                cleanCommand = cleanText,
                actionType = ActionType.COMPANION_TALK,
                args = mapOf("sub" to "casual"),
                responseText = if (isBengali) "আমি খুব ভালো আছি! আপনার সাথে অফলাইনে কাজ করতে পেরে চমৎকার লাগছে। বলুন, আজ কীভাবে সাহায্য করতে পারি?"
                               else "I'm doing stellar! It's super pleasant to spend time with you offline today. How can I assist you with offline routines or voice commands?"
            )
        }

        // Q. General AI matching fallback
        return ParsedCommand(
            hasWakeWord = hasWakeOn,
            matchedWakeWord = matchedWakeWord,
            cleanCommand = cleanText,
            actionType = ActionType.GENERAL_AI,
            responseText = if (isBengali) "ভয়েস সার্চ ইঞ্জিনে জিজ্ঞাসা করা হচ্ছে: $cleanText..." else "Processing command in hybrid AI core: $cleanText..."
        )
    }

    private fun findContact(query: String, contacts: List<Contact>): Contact? {
        val lowerQuery = query.trim().lowercase()
        if (lowerQuery.isEmpty()) return null
        return contacts.firstOrNull { 
            it.name.lowercase().contains(lowerQuery) || lowerQuery.contains(it.name.lowercase()) 
        }
    }

    private fun getAppDetails(appName: String): Pair<String, String> {
        return when (appName.lowercase(Locale.ROOT)) {
            "youtube", "ইউটিউব", "ইউটিউট" -> "com.google.android.youtube" to "YouTube"
            "whatsapp", "whats app", "হোয়াটসঅ্যাপ", "হোয়াটস অ্যাপ", "ওয়াটসাপ" -> "com.whatsapp" to "WhatsApp"
            "facebook", "fb", "ফেসবুক", "ফেইসবুক" -> "com.facebook.katana" to "Facebook"
            "settings", "সেটিংস Settings", "সেটিংস" -> "com.android.settings" to "Settings"
            "calendar", "ক্যালেন্ডার" -> "com.google.android.calendar" to "Calendar"
            "messenger", "মেসেঞ্জার" -> "com.facebook.orca" to "Facebook Messenger"
            "wikipedia", "উইকিপিডিয়া", "উইকি" -> "org.wikipedia" to "Wikipedia"
            "chrome", "ক্রোম" -> "com.android.chrome" to "Google Chrome"
            else -> "com.android.chrome" to appName.replaceFirstChar { it.uppercase() }
        }
    }
}
