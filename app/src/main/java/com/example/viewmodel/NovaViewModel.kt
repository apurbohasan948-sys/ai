package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import com.example.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class NovaViewModel(
    application: Application,
    private val repository: AssistantRepository
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val simulator = SystemActionsSimulator(context)
    private val ttsManager = TextToSpeechManager(context)

    // State flows
    val logs: StateFlow<List<AssistantLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<Reminder>> = repository.allReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routines: StateFlow<List<Routine>> = repository.allRoutines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts: StateFlow<List<Contact>> = repository.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Assistant Visual and Engine States
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

    init {
        // Welcome feedback log is pre-seeded in database AppDatabase callback.
    }

    fun toggleModelMode() {
        _modelMode.value = if (_modelMode.value == "Offline Local") "Online Hybrid" else "Offline Local"
    }

    fun toggleWakeWord() {
        _wakeWordAlwaysOn.value = !_wakeWordAlwaysOn.value
    }

    fun setListening(listening: Boolean) {
        _isListening.value = listening
        if (listening) {
            _isProcessing.value = false
            _isSpeaking.value = false
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            // insert fresh welcome
            repository.insertLog(
                AssistantLog(
                    sender = "nova",
                    message = "System buffer reset. Hello! I'm Nova. I am ready to receive your commands."
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

    fun addManualContact(name: String, phoneNumber: String, email: String) {
        viewModelScope.launch {
            repository.insertContact(Contact(name = name, phoneNumber = phoneNumber, email = email))
        }
    }

    fun deleteManualContact(contact: Contact) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }

    fun submitCommand(inputCommand: String) {
        if (inputCommand.trim().isEmpty()) return

        viewModelScope.launch {
            // Write user text log to Room
            repository.insertLog(AssistantLog(sender = "user", message = inputCommand))

            _isListening.value = false
            _isProcessing.value = true
            delay(400) // Aesthetic delay for processing simulation

            val contactList = contacts.value
            val parsed = CommandParser.parse(inputCommand, contactList)

            // Evaluate wake word requirements
            if (_wakeWordAlwaysOn.value && !parsed.hasWakeWord) {
                // Technically user typed/said command without wake words, but wake words are required
                val responseMsg = "Hint: Say \"Hey Nova\" or \"Nova\" at the beginning to activate me! (e.g., \"Hey Nova, open YouTube\" )"
                repository.insertLog(AssistantLog(sender = "nova", message = responseMsg))
                ttsSpeak(responseMsg)
                _isProcessing.value = false
                return@launch
            }

            var customResponseText = parsed.responseText
            val cleanTask = parsed.cleanCommand

            // Execute local simulated controls
            when (parsed.actionType) {
                ActionType.OPEN_APP -> {
                    val appName = parsed.args["appName"] as? String ?: "Chrome"
                    val pkgName = parsed.args["packageName"] as? String ?: "com.android.chrome"
                    _currentActiveApp.value = appName
                    val (success, logText) = simulator.openApp(pkgName, appName)
                    customResponseText = if (success) {
                        "Opening $appName. $logText"
                    } else {
                        "Opening $appName simulator internally."
                    }
                }
                ActionType.CALL -> {
                    val cName = parsed.args["contactName"] as? String ?: "Contact"
                    val num = parsed.args["phoneNumber"] as? String ?: "911"
                    _currentActiveApp.value = "Phone Dial"
                    customResponseText = simulator.makePhoneCall(cName, num)
                }
                ActionType.SEND_MESSAGE -> {
                    val cName = parsed.args["contactName"] as? String ?: "Recipient"
                    val num = parsed.args["phoneNumber"] as? String ?: "+1000"
                    val body = parsed.args["body"] as? String ?: ""
                    val platform = parsed.args["platform"] as? String ?: "SMS"
                    _currentActiveApp.value = platform
                    customResponseText = simulator.sendSmsMessage(cName, num, body)
                }
                ActionType.TOGGLE_WIFI -> {
                    val switchVal = parsed.args["on"] as? Boolean ?: true
                    _wifiActive.value = switchVal
                    customResponseText = simulator.toggleWifi(switchVal)
                }
                ActionType.TOGGLE_BLUETOOTH -> {
                    val switchVal = parsed.args["on"] as? Boolean ?: false
                    _bluetoothActive.value = switchVal
                    customResponseText = simulator.toggleBluetooth(switchVal)
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
                    customResponseText = "Simulating accessibility scroll gesture ${if (down) "DOWN" else "UP"}."
                }
                ActionType.TAKE_SCREENSHOT -> {
                    customResponseText = simulator.captureScreenshotSimulation()
                }
                ActionType.LIST_REMINDERS -> {
                    val activeList = reminders.value.filter { !it.isCompleted }
                    customResponseText = if (activeList.isEmpty()) {
                        "You don't have any active reminders currently. Set some from the reminders manager!"
                    } else {
                        "Here are your offline tasks: " + activeList.joinToString(", ") { "${it.title} at ${it.timeLabel}" }
                    }
                }
                ActionType.GENERAL_AI -> {
                    // Try fetch from online Gemini if config is set is Online Hybrid
                    if (_modelMode.value == "Online Hybrid") {
                        val key = BuildConfig.GEMINI_API_KEY
                        if (key.isNotEmpty() && !key.contains("MY_GEMINI_API_KEY")) {
                            customResponseText = fetchGeminiAiResponse(cleanTask, key)
                        } else {
                            // Empty or default config template
                            customResponseText = "[Local Mode]: Gemini API core offline or key unset. Processing locally: I am happy to address \"$cleanTask\"! Since internet is currently simulating offline, I've loaded standard offline summaries."
                        }
                    } else {
                        // Local Mode fallback summaries
                        customResponseText = "offline query result: I've processed \"$cleanTask\" via local language module. I can manage system calls, schedule your routines, or chat locally."
                    }
                }
                else -> {
                    // Normal companion dialogues, jokes, greeting
                }
            }

            // Insert Nova reply log
            repository.insertLog(AssistantLog(sender = "nova", message = customResponseText))
            _isProcessing.value = false

            // Trigger actual audio speak
            ttsSpeak(customResponseText)
        }
    }

    private suspend fun fetchGeminiAiResponse(prompt: String, apiKey: String): String {
        return try {
            val systemInstruction = "You are Google AI Studio's Offline personal assistant 'Nova'. You are calm, caring, human-like, helpful. Keep answers highly conversational, clear, and action-oriented."
            val service = GeminiRetrofitClient.service
            val req = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))),
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(systemInstruction)))
            )
            val response = service.generateContent(apiKey, req)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "No response content received."
        } catch (e: Exception) {
            "Simulating offline fallback. Web request failed: ${e.localizedMessage ?: "timeout"}."
        }
    }

    fun triggerRoutineSequence(routine: Routine) {
        viewModelScope.launch {
            repository.insertLog(AssistantLog(sender = "user", message = "Triggering routine: ${routine.name}"))
            _isListening.value = false
            _isProcessing.value = true
            delay(500)

            val actions = routine.actionsList.split(",")
            val logsSummary = ArrayList<String>()

            for (act in actions) {
                _isProcessing.value = true
                delay(600) // Beautiful incremental delay showing sequence execution
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

            val resultReport = "Routine \"${routine.name}\" executed successfully.\n" + logsSummary.joinToString("\n")
            repository.insertLog(AssistantLog(sender = "nova", message = resultReport))
            ttsSpeak("Routine ${routine.name} sequence complete.")
        }
    }

    private suspend fun ttsSpeak(text: String) {
        _isSpeaking.value = true
        ttsManager.speak(text)
        // Clean speaking status after dynamic delay proportionate to length of text
        val duration = (text.length * 50L).coerceIn(1500L, 5000L)
        delay(duration)
        _isSpeaking.value = false
    }

    override fun onCleared() {
        super.onCleared()
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
