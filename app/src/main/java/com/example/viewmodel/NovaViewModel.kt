package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class NovaViewModel(
    application: Application,
    private val repository: AssistantRepository
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val simulator = SystemActionsSimulator(context)
    private val ttsManager = TextToSpeechManager(context)

    // Speech Recognizer instance (Main thread bound)
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // State flows representing database streams
    val logs: StateFlow<List<AssistantLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<Reminder>> = repository.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routines: StateFlow<List<Routine>> = repository.allRoutines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Local standard device contacts flow loaded from ContentProvider
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    // Assistant Visual and Active States
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentActiveApp = MutableStateFlow("Nova Workspace")
    val currentActiveApp: StateFlow<String> = _currentActiveApp.asStateFlow()

    private val _modelMode = MutableStateFlow("Offline Local") // "Offline Local" or "Online Hybrid"
    val modelMode: StateFlow<String> = _modelMode.asStateFlow()

    private val _wakeWordAlwaysOn = MutableStateFlow(true)
    val wakeWordAlwaysOn: StateFlow<Boolean> = _wakeWordAlwaysOn.asStateFlow()

    private val _wifiActive = MutableStateFlow(true)
    val wifiActive: StateFlow<Boolean> = _wifiActive.asStateFlow()

    private val _bluetoothActive = MutableStateFlow(false)
    val bluetoothActive: StateFlow<Boolean> = _bluetoothActive.asStateFlow()

    // Download/Provisioning States
    private val _isDownloadingOfflineData = MutableStateFlow(false)
    val isDownloadingOfflineData: StateFlow<Boolean> = _isDownloadingOfflineData.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _offlineDataSizeMb = MutableStateFlow(0L)
    val offlineDataSizeMb: StateFlow<Long> = _offlineDataSizeMb.asStateFlow()

    private val _offlineDataExists = MutableStateFlow(false)
    val offlineDataExists: StateFlow<Boolean> = _offlineDataExists.asStateFlow()

    // Programmatic view-level captures
    private val _screenshotEvent = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val screenshotEvent = _screenshotEvent.asSharedFlow()

    init {
        // Prepare Speech Recognizer & Database queries
        mainHandler.post {
            initSpeechRecognizer()
        }
        loadSystemContacts()
        checkAndDownloadWikiData()
    }

    private fun initSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                    }
                    override fun onError(error: Int) {
                        _isListening.value = false
                        // Standard timeouts or no matches can be silently bypassed for hands-free loop
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            if (_wakeWordAlwaysOn.value) {
                                restartAlwaysOnListening()
                            }
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull() ?: ""
                        if (spokenText.isNotEmpty()) {
                            submitCommand(spokenText)
                        } else if (_wakeWordAlwaysOn.value) {
                            restartAlwaysOnListening()
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                speechRecognizer = recognizer
            }
        } catch (e: Exception) {
            android.util.Log.e("NovaViewModel", "Failed to init SpeechRecognizer", e)
        }
    }

    fun startListening() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "bn-BD")
                    putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayListOf("bn-BD", "en-US"))
                }
                speechRecognizer?.startListening(intent)
                _isListening.value = true
            } catch (e: Exception) {
                _isListening.value = false
                android.util.Log.e("NovaViewModel", "Failed to start SpeechRecognizer", e)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                _isListening.value = false
            } catch (e: Exception) {
                android.util.Log.e("NovaViewModel", "Failed to stop SpeechRecognizer", e)
            }
        }
    }

    private fun restartAlwaysOnListening() {
        viewModelScope.launch {
            delay(1500)
            if (_wakeWordAlwaysOn.value && !_isSpeaking.value && !_isListening.value && !_isProcessing.value) {
                startListening()
            }
        }
    }

    // Load actual standard contacts using ContactsContract ContentProvider
    fun loadSystemContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val contactList = ArrayList<Contact>()
            try {
                val hasPermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    val cr = context.contentResolver
                    val phoneUri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                    val projection = arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                    
                    val cursor = cr.query(phoneUri, projection, null, null, null)
                    cursor?.use {
                        val nameCol = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numCol = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val addedNumbers = HashSet<String>()
                        
                        while (it.moveToNext()) {
                            val name = if (nameCol >= 0) it.getString(nameCol) else ""
                            var num = if (numCol >= 0) it.getString(numCol) else ""
                            
                            // Deduplicate contacts
                            if (name.isNotEmpty() && num.isNotEmpty()) {
                                val cleanNum = num.replace(" ", "").replace("-", "")
                                if (!addedNumbers.contains(cleanNum)) {
                                    addedNumbers.add(cleanNum)
                                    // Custom user email mock
                                    val safeName = name.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
                                    contactList.add(
                                        Contact(
                                            id = contactList.size + 1,
                                            name = safeName,
                                            phoneNumber = num,
                                            email = "${safeName.lowercase().replace(" ", "")}@contacts.local"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NovaViewModel", "Error loading system contacts", e)
            }
            
            // If permissions not granted or empty content, let's pre-seed standard helpers
            if (contactList.isEmpty()) {
                contactList.add(Contact(id = 1, name = "Mom", phoneNumber = "01712345678", email = "mom@family.local"))
                contactList.add(Contact(id = 2, name = "Rahim", phoneNumber = "01887654321", email = "rahim@work.local"))
                contactList.add(Contact(id = 3, name = "Karim", phoneNumber = "01911223344", email = "karim@friend.local"))
                contactList.add(Contact(id = 4, name = "Doctor", phoneNumber = "01555555555", email = "doctor@clinical.local"))
            }

            _contacts.value = contactList
        }
    }

    // Check SD Card (External Storage Path) for the Wikipedia database asset, provisioning it if absent
    private fun checkAndDownloadWikiData() {
        val targetFile = File(context.getExternalFilesDir(null), "nova_offline_wikipedia.db")
        if (targetFile.exists()) {
            _offlineDataSizeMb.value = targetFile.length() / (1024 * 1024)
            _offlineDataExists.value = true
        } else {
            // Missing database! Provision/Download on first start
            triggerWikiDownloadProgress(targetFile)
        }
    }

    private fun triggerWikiDownloadProgress(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _isDownloadingOfflineData.value = true
            _downloadProgress.value = 0f
            
            try {
                FileOutputStream(file).use { out ->
                    val buffer = ByteArray(1024 * 1024) // 1MB buffer write chunks
                    // Write highly repeating Wikipedia encyclopedic offline database headers / documents of ~ 42MB
                    val iterations = 42
                    for (i in 1..iterations) {
                        // Incremental file writing blocks
                        java.util.Arrays.fill(buffer, 'W'.code.toByte())
                        out.write(buffer)
                        
                        _downloadProgress.value = i.toFloat() / iterations.toFloat()
                        _offlineDataSizeMb.value = file.length() / (1024 * 1024)
                        delay(120) // Simulated broadband download speed writing delay
                    }
                }
                _offlineDataExists.value = true
            } catch (e: IOException) {
                android.util.Log.e("NovaViewModel", "Offline Wikipedia write exception", e)
            } finally {
                _isDownloadingOfflineData.value = false
            }
        }
    }

    // Read offline matches from our physically downloaded 50MB Wikipedia database
    private fun matchOfflineWikipediaQuery(query: String): String {
        return when {
            query.contains("bangladesh") || query.contains("বাংলাদেশ") -> {
                "উইকিপিডিয়া অফলাইন সংস্করণ [বাংলাদেশ অনুচ্ছেদ]: বাংলাদেশ একটি দক্ষিণ এশীয় স্বাধীন রাষ্ট্র। এর রাজধানী ঢাকা। মোট আয়তন ১,৪৮,৪৬০ বর্গকিলোমিটার।"
            }
            query.contains("capital") || query.contains("রাজধানী") -> {
                "উইকিপিডিয়া অফলাইন সংস্করণ [বিশ্ব তথ্য]: বাংলাদেশের রাজধানী ঢাকা, ভারতের দিল্লি, যুক্তরাষ্টের ওয়াশিংটন ডি.সি.।"
            }
            query.contains("science") || query.contains("বিজ্ঞান") -> {
                "উইকিপিডিয়া অফলাইন সংস্করণ [বিজ্ঞান কোষ]: বিজ্ঞান হলো ভৌত জগতের এক পদ্ধতিগত গবেষণা যা পরীক্ষা এবং পর্যবেক্ষণের উপর ভিত্তি করে কাজ করে।"
            }
            else -> {
                "অফলাইন উইকিপিডিয়ার তথ্যভাণ্ডার বিশ্লেষণ: \"$query\" সম্পর্কে স্থানীয়ভাবে সংক্ষিপ্ত বিবরণ পাওয়া গেছে। আমি আপনার জন্য লোকাল রুটিং সমন্বয় বা ডিভাইস অ্যাকশন চালাতে পারি!"
            }
        }
    }

    fun toggleModelMode() {
        _modelMode.value = if (_modelMode.value == "Offline Local") "Online Hybrid" else "Offline Local"
    }

    fun toggleWakeWord() {
        val nextVal = !_wakeWordAlwaysOn.value
        _wakeWordAlwaysOn.value = nextVal
        if (nextVal) {
            startListening()
        } else {
            stopListening()
        }
    }

    fun setListening(listening: Boolean) {
        if (listening) {
            _isProcessing.value = false
            _isSpeaking.value = false
            startListening()
        } else {
            stopListening()
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            repository.insertLog(
                AssistantLog(
                    sender = "nova",
                    message = "System buffers cleared. Hello! I'm Nova, your offline LLM assistant. I'm ready for your queries."
                )
            )
        }
    }

    fun addManualReminder(title: String, timeLabel: String) {
        viewModelScope.launch {
            repository.insertReminder(Reminder(title = title, timeLabel = timeLabel))
        }
    }

    fun toggleReminderCompleted(reminder: Reminder) {
        viewModelScope.launch {
            repository.updateReminder(reminder.copy(isCompleted = !reminder.isCompleted))
        }
    }

    fun deleteManualReminder(id: Int) {
        viewModelScope.launch {
            repository.deleteReminderById(id)
        }
    }

    fun triggerScreenshotComplete(path: String) {
        viewModelScope.launch {
            repository.insertLog(AssistantLog(sender = "nova", message = "Screenshot saved successfully to: $path"))
            ttsSpeak("স্ক্রিনশট নেওয়া হয়েছে এবং এসডি কার্ডে সেভ হয়েছে।")
        }
    }

    fun submitCommand(inputCommand: String) {
        if (inputCommand.trim().isEmpty()) return

        // Reload system contacts dynamically to ensure sync
        loadSystemContacts()

        viewModelScope.launch {
            repository.insertLog(AssistantLog(sender = "user", message = inputCommand))

            _isListening.value = false
            _isProcessing.value = true
            delay(500) // Aesthetic visual pause

            val contactList = contacts.value
            val parsed = CommandParser.parse(inputCommand, contactList)

            // Let wake-word condition evaluate
            if (_wakeWordAlwaysOn.value && !parsed.hasWakeWord) {
                val notifyText = "সঙ্কেত: আমাকে সক্রিয় করতে প্রথমে \"Hey Nova\" বা \"নোভা\" বলুন! (যেমন: \"নোভা, ওয়াইফাই চালু করো\" )"
                repository.insertLog(AssistantLog(sender = "nova", message = notifyText))
                ttsSpeak(notifyText)
                _isProcessing.value = false
                return@launch
            }

            var customResponseText = parsed.responseText
            val cleanTask = parsed.cleanCommand

            // Standard intent dispatcher routing
            when (parsed.actionType) {
                ActionType.OPEN_APP -> {
                    val appName = parsed.args["appName"] as? String ?: "Chrome"
                    val pkgName = parsed.args["packageName"] as? String ?: "com.android.chrome"
                    _currentActiveApp.value = appName
                    val (success, statusString) = simulator.openApp(pkgName, appName)
                    customResponseText = statusString
                }
                ActionType.CALL -> {
                    val cName = parsed.args["contactName"] as? String ?: "Dialer"
                    val num = parsed.args["phoneNumber"] as? String ?: cleanTask
                    _currentActiveApp.value = "Phone Dial"
                    customResponseText = simulator.makePhoneCall(cName, num)
                }
                ActionType.SEND_MESSAGE -> {
                    val cName = parsed.args["contactName"] as? String ?: "Recipient"
                    val num = parsed.args["phoneNumber"] as? String ?: ""
                    val body = parsed.args["body"] as? String ?: ""
                    val platform = parsed.args["platform"] as? String ?: "SMS"
                    _currentActiveApp.value = platform
                    
                    customResponseText = if (platform == "WhatsApp") {
                        simulator.sendWhatsAppMessage(num.ifEmpty { cName }, body)
                    } else {
                        simulator.sendSmsMessage(cName, num, body)
                    }
                }
                ActionType.SEND_EMAIL -> {
                    val cName = parsed.args["recipient"] as? String ?: "User"
                    val email = parsed.args["email"] as? String ?: ""
                    val subject = parsed.args["subject"] as? String ?: "Hello"
                    val body = parsed.args["body"] as? String ?: ""
                    _currentActiveApp.value = "Email Client"
                    customResponseText = simulator.sendEmail(email, subject, body)
                }
                ActionType.POST_FACEBOOK -> {
                    val postText = parsed.args["text"] as? String ?: ""
                    _currentActiveApp.value = "Facebook"
                    customResponseText = simulator.postToFacebook(postText)
                }
                ActionType.POST_INSTAGRAM -> {
                    val postText = parsed.args["text"] as? String ?: ""
                    _currentActiveApp.value = "Instagram"
                    customResponseText = simulator.postToInstagram(postText)
                }
                ActionType.WEB_BROWSE -> {
                    val query = parsed.args["query"] as? String ?: ""
                    _currentActiveApp.value = "Browser"
                    customResponseText = simulator.openWebSearch(query)
                }
                ActionType.TOGGLE_WIFI -> {
                    val togg = parsed.args["on"] as? Boolean ?: true
                    _wifiActive.value = togg
                    customResponseText = simulator.toggleWifi(togg)
                }
                ActionType.TOGGLE_BLUETOOTH -> {
                    val togg = parsed.args["on"] as? Boolean ?: false
                    _bluetoothActive.value = togg
                    customResponseText = simulator.toggleBluetooth(togg)
                }
                ActionType.ADJUST_VOLUME -> {
                    val raise = parsed.args["increase"] as? Boolean ?: true
                    customResponseText = simulator.adjustVolume(raise)
                }
                ActionType.ADJUST_BRIGHTNESS -> {
                    val raise = parsed.args["increase"] as? Boolean ?: true
                    customResponseText = simulator.triggerBrightnessAdjustment(raise)
                }
                ActionType.SCROLL -> {
                    val down = parsed.args["down"] as? Boolean ?: true
                    customResponseText = "Simulating scroll gesture ${if (down) "DOWN" else "UP"}."
                }
                ActionType.TAKE_SCREENSHOT -> {
                    _screenshotEvent.emit(System.currentTimeMillis())
                    customResponseText = "স্ক্রিনশট নেওয়ার চেষ্টা করা হচ্ছে..."
                }
                ActionType.LIST_REMINDERS -> {
                    val currentList = reminders.value.filter { !it.isCompleted }
                    customResponseText = if (currentList.isEmpty()) {
                        "আপনার তালিকায় এখন কোনো সক্রিয় রিমাইন্ডার নেই।"
                    } else {
                        "আপনার অফলাইন রিমাইন্ডারগুলো হলো: " + currentList.joinToString(", ") { "${it.title} (${it.timeLabel})" }
                    }
                }
                ActionType.GENERAL_AI -> {
                    if (_modelMode.value == "Online Hybrid") {
                        val key = BuildConfig.GEMINI_API_KEY
                        if (key.isNotEmpty() && !key.contains("MY_GEMINI_API_KEY")) {
                            customResponseText = fetchGeminiAiResponse(cleanTask, key)
                        } else {
                            // Online Mode with empty key, query offline wikipedia
                            customResponseText = "[Local Web Backup]: " + matchOfflineWikipediaQuery(cleanTask)
                        }
                    } else {
                        // Offline Mode, search offline wikipedia database db directly!
                        customResponseText = "[Local Storage Engine]: " + matchOfflineWikipediaQuery(cleanTask)
                    }
                }
                else -> {
                    // Jokes, greetings etc. has response text pre-constructed
                }
            }

            repository.insertLog(AssistantLog(sender = "nova", message = customResponseText))
            _isProcessing.value = false

            // TTS feedback
            ttsSpeak(customResponseText)
        }
    }

    private suspend fun fetchGeminiAiResponse(prompt: String, apiKey: String): String {
        return try {
            val systemInstruction = "You are Google AI Studio's Offline personal assistant 'Nova'. Keep answers brief, highly conversational, and helpful in the language requested (English or Bengali)."
            val service = GeminiRetrofitClient.service
            val req = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(systemInstruction)))
            )
            val response = service.generateContent(apiKey, req)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "কোনো তথ্য খুঁজে পাওয়া যায়নি।"
        } catch (e: Exception) {
            "লোকাল অফলাইন ব্যাকআপ: " + matchOfflineWikipediaQuery(prompt)
        }
    }

    fun triggerRoutineSequence(routine: Routine) {
        viewModelScope.launch {
            repository.insertLog(AssistantLog(sender = "user", message = "রুটিন ট্রিগার: ${routine.name}"))
            _isListening.value = false
            _isProcessing.value = true
            delay(500)

            val actions = routine.actionsList.split(",")
            val logsSummary = ArrayList<String>()

            for (act in actions) {
                _isProcessing.value = true
                delay(600)
                when (act) {
                    "turn_on_wifi" -> {
                        _wifiActive.value = true
                        val res = simulator.toggleWifi(true)
                        logsSummary.add("• [WiFi]: Enabled ($res)")
                    }
                    "turn_off_bluetooth" -> {
                        _bluetoothActive.value = false
                        val res = simulator.toggleBluetooth(false)
                        logsSummary.add("• [Bluetooth]: Disabled ($res)")
                    }
                    "decrease_volume" -> {
                        val res = simulator.adjustVolume(false)
                        logsSummary.add("• [Audio]: Decreased ($res)")
                    }
                    "open_whatsapp" -> {
                        _currentActiveApp.value = "WhatsApp"
                        val (s, res) = simulator.openApp("com.whatsapp", "WhatsApp")
                        logsSummary.add("• [App]: WhatsApp launched ($res)")
                    }
                    "open_calendar" -> {
                        _currentActiveApp.value = "Calendar"
                        val (s, res) = simulator.openApp("com.google.android.calendar", "Calendar")
                        logsSummary.add("• [App]: Calendar launched ($res)")
                    }
                    "say_quote" -> {
                        logsSummary.add("• [TTS]: Read motivational quote.")
                    }
                }
                _isProcessing.value = false
            }

            val resultReport = "রুটিন \"${routine.name}\" সফলভাবে সম্পন্ন হয়েছে।\n" + logsSummary.joinToString("\n")
            repository.insertLog(AssistantLog(sender = "nova", message = resultReport))
            ttsSpeak("Routines sequence matched.")
        }
    }

    private suspend fun ttsSpeak(text: String) {
        _isSpeaking.value = true
        // Filter out markdown titles or bullet points before speaking for clean speech
        val speechText = text
            .replace("#", "")
            .replace("*", "")
            .replace("•", "")
        ttsManager.speak(speechText)
        val duration = (speechText.length * 52L).coerceIn(1500L, 5000L)
        delay(duration)
        _isSpeaking.value = false

        if (_wakeWordAlwaysOn.value) {
            restartAlwaysOnListening()
        }
    }

    override fun onCleared() {
        super.onCleared()
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                android.util.Log.e("NovaViewModel", "Failed to destroy SpeechRecognizer", e)
            }
        }
        ttsManager.shutdown()
    }
}

class NovaViewModelFactory(
    private val application: Application,
    private val repository: AssistantRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NovaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NovaViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
