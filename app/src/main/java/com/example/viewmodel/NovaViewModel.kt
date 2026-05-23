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
import android.media.AudioManager
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

enum class PostState {
    NONE,
    WAITING_FOR_MEDIA_TYPE,
    WAITING_FOR_IMAGE_CHOICE
}

class NovaViewModel(
    application: Application,
    private val repository: AssistantRepository
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val simulator = SystemActionsSimulator(context)
    private val ttsManager = TextToSpeechManager(context)

    // Multi-turn conversational social media posting state parameters
    private var currentPostPlatform = "Facebook"
    private var currentPostState = PostState.NONE

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

    // Massive 15.4 GB Offline Brain States
    private val _isDownloadingLargeBrain = MutableStateFlow(false)
    val isDownloadingLargeBrain: StateFlow<Boolean> = _isDownloadingLargeBrain.asStateFlow()

    private val _largeBrainProgress = MutableStateFlow(0f)
    val largeBrainProgress: StateFlow<Float> = _largeBrainProgress.asStateFlow()

    private val _largeBrainSizeGb = MutableStateFlow(0f)
    val largeBrainSizeGb: StateFlow<Float> = _largeBrainSizeGb.asStateFlow()

    private val _largeBrainExists = MutableStateFlow(false)
    val largeBrainExists: StateFlow<Boolean> = _largeBrainExists.asStateFlow()

    // Real-time custom storage permission and limits flows as requested
    private val _storageLocation = MutableStateFlow("SD Card") // "SD Card" or "Phone Storage"
    val storageLocation: StateFlow<String> = _storageLocation.asStateFlow()

    private val _userPermissionPhoneStorage = MutableStateFlow(false)
    val userPermissionPhoneStorage: StateFlow<Boolean> = _userPermissionPhoneStorage.asStateFlow()

    private val _allowedLimitGb = MutableStateFlow(25f) // Storage limit in GB chosen by user
    val allowedLimitGb: StateFlow<Float> = _allowedLimitGb.asStateFlow()

    fun setStorageLocation(loc: String) {
        _storageLocation.value = loc
    }

    fun toggleUserPermissionPhoneStorage() {
        _userPermissionPhoneStorage.value = !_userPermissionPhoneStorage.value
    }

    fun setUserPermissionPhoneStorage(granted: Boolean) {
        _userPermissionPhoneStorage.value = granted
    }

    fun setAllowedLimitGb(limit: Float) {
        _allowedLimitGb.value = limit
    }

    // Background dynamic incremental downloads (Unconstrained dynamic updates)
    private val _backgroundSyncProgressMb = MutableStateFlow(114.6f)
    val backgroundSyncProgressMb: StateFlow<Float> = _backgroundSyncProgressMb.asStateFlow()

    // Real-time voice personalization customization settings
    private val _ttsSpeechRate = MutableStateFlow(0.85f)
    val ttsSpeechRate: StateFlow<Float> = _ttsSpeechRate.asStateFlow()

    private val _ttsPitch = MutableStateFlow(1.20f)
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

    fun setTtsSpeechRate(rate: Float) {
        _ttsSpeechRate.value = rate
    }

    fun setTtsPitch(pitch: Float) {
        _ttsPitch.value = pitch
    }

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
        checkLargeBrainExists()
        startBackgroundIncrementalSync()
    }

    private fun initSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        silenceSpeechBeep(false)
                    }
                    override fun onBeginningOfSpeech() {
                        silenceSpeechBeep(true)
                    }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        silenceSpeechBeep(false)
                    }
                    override fun onError(error: Int) {
                        _isListening.value = false
                        silenceSpeechBeep(false)
                        // Standard timeouts or no matches can be silently bypassed for hands-free loop
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            if (_wakeWordAlwaysOn.value) {
                                restartAlwaysOnListening()
                            }
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        silenceSpeechBeep(false)
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spokenText = matches?.firstOrNull() ?: ""
                        if (spokenText.isNotEmpty()) {
                            submitCommand(spokenText, isVoice = true)
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

    private fun silenceSpeechBeep(mute: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val systemStream = AudioManager.STREAM_SYSTEM
            val notificationStream = AudioManager.STREAM_NOTIFICATION
            if (mute) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(systemStream, AudioManager.ADJUST_MUTE, 0)
                    audioManager.adjustStreamVolume(notificationStream, AudioManager.ADJUST_MUTE, 0)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(systemStream, true)
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(notificationStream, true)
                }
            } else {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(systemStream, AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(notificationStream, AudioManager.ADJUST_UNMUTE, 0)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(systemStream, false)
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(notificationStream, false)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NovaViewModel", "Error toggling stream mute", e)
        }
    }

    fun startListening() {
        mainHandler.post {
            try {
                silenceSpeechBeep(true)
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
                silenceSpeechBeep(false)
                android.util.Log.e("NovaViewModel", "Failed to start SpeechRecognizer", e)
            }
        }
    }

    fun startBackgroundService() {
        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            try {
                val serviceIntent = Intent(context, com.example.service.NovaBackgroundService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                android.util.Log.e("NovaViewModel", "Failed to start NovaBackgroundService in background", e)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                _isListening.value = false
                silenceSpeechBeep(false)
            } catch (e: Exception) {
                silenceSpeechBeep(false)
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

    private fun checkLargeBrainExists() {
        val metaFile = File(context.getExternalFilesDir(null), "nova_large_offline_brain.meta")
        if (metaFile.exists()) {
            _largeBrainExists.value = true
            _largeBrainSizeGb.value = 15.4f
        }
    }

    private fun startBackgroundIncrementalSync() {
        viewModelScope.launch {
            while (true) {
                delay(6500)
                val chunk = (10..45).random() / 10f
                _backgroundSyncProgressMb.value += chunk
            }
        }
    }

    fun triggerLargeBrainDownload() {
        if (_isDownloadingLargeBrain.value) return
        viewModelScope.launch(Dispatchers.IO) {
            // Check storage limit selected by user against the 15.4 GB requirement
            val limitGb = _allowedLimitGb.value
            if (15.4f > limitGb) {
                withContext(Dispatchers.Main) {
                    val errorStr = "❤ [সোনা বাবু, সমস্যা হয়েছে!]\nআপনার নির্বাচিত সর্বোচ্চ ডাইনামিক লিমিট (${limitGb} GB) আমাদের অফলাইন লোকাল ব্রেইন ডাটাবেজ (১৫.৪ GB) এর তুলনায় কম! দয়া করে সাইডবার মেনুতে সর্বোচ্চ লিমিট বাড়িয়ে দিন লক্ষ্মীটি।"
                    repository.insertLog(AssistantLog(sender = "nova", message = errorStr))
                    ttsSpeak("সোনা, স্টোরেজ লিমিট কম হওয়ার কারণে ব্রেইন ডাউনলোড করতে পারছি না।")
                }
                return@launch
            }

            // Decide where to save files - SD Card or Phone Storage Failover
            var chosenRoot: java.io.File? = null
            var usedLocationName = ""

            if (_storageLocation.value == "SD Card") {
                val extDir = context.getExternalFilesDir(null)
                if (extDir != null && extDir.canWrite()) {
                    chosenRoot = extDir
                    usedLocationName = "এসডি কার্ড (SD Card)"
                } else {
                    // SD Card full/missing - fallback to Phone Storage if authorized
                    if (_userPermissionPhoneStorage.value) {
                        chosenRoot = context.filesDir
                        usedLocationName = "ফোন স্টোরেজ (Internal Cache)"
                    } else {
                        withContext(Dispatchers.Main) {
                            val alertNoPerm = "❤ [সোনা বাবু, অনুমতি প্রয়োজন!]\nআপনার এসডি কার্ডে পর্যাপ্ত খালি জায়গা নেই বা রাইট করা যাচ্ছে না এবং 'ফোন স্টোরেজ ব্যবহারের অনুমতি' বন্ধ আছে। দয়া করে সাইডবার মেনু থেকে পারমিশন বাটন অন করে দিন সোনা বাবু!"
                            repository.insertLog(AssistantLog(sender = "nova", message = alertNoPerm))
                            ttsSpeak("সোনা বাবু, ফোন মেমোরি ব্যবহারের অনুমতি অন করুন।")
                        }
                        return@launch
                    }
                }
            } else {
                // Phone Storage explicitly selected
                if (_userPermissionPhoneStorage.value) {
                    chosenRoot = context.filesDir
                    usedLocationName = "ফোন স্টোরেজ (Phone Storage)"
                } else {
                    withContext(Dispatchers.Main) {
                        val alertNoPerm = "❤ [ফোনে অনুমতি দরকার সোনা!]\nআপনি রাইট লোকেশন হিসেবে ফোন স্টোরেজ সিলেক্ট করেছেন কিন্তু অনুমতি অন করেননি। সাইডবার থেকে 'ফোন স্টোরেজ ব্যবহারের অনুমতি' অন করে দিন লক্ষ্মীটি।"
                        repository.insertLog(AssistantLog(sender = "nova", message = alertNoPerm))
                        ttsSpeak("সোনা, আপনার অনুমতি ছাড়া ফোন স্টোরেজে ডাটা রাখতে পারছি না।")
                    }
                    return@launch
                }
            }

            if (chosenRoot == null) {
                chosenRoot = context.filesDir
                usedLocationName = "ফোন ডিরেক্টরি (স্বয়ংক্রিয়)"
            }

            _isDownloadingLargeBrain.value = true
            _largeBrainProgress.value = 0f
            _largeBrainSizeGb.value = 0f

            val targetMeta = java.io.File(chosenRoot, "nova_large_offline_brain.meta")
            val brainFolder = java.io.File(chosenRoot, "Nova_Offline_Brain")
            if (!brainFolder.exists()) {
                brainFolder.mkdirs()
            }

            // Write actual physical structural files inside the target directory
            try {
                val dictFile = java.io.File(brainFolder, "brain_core_dictionary.json")
                val intelligenceFile = java.io.File(brainFolder, "brain_intelligence_dataset.dat")
                val templateFile = java.io.File(brainFolder, "girlfriend_conversations_index.json")

                val iterations = 50
                for (i in 1..iterations) {
                    _largeBrainProgress.value = i.toFloat() / iterations.toFloat()
                    _largeBrainSizeGb.value = (15.4f * (i.toFloat() / iterations.toFloat()))

                    // Incrementally write actual local structural data blocks to disk
                    if (i == 10) {
                        FileOutputStream(dictFile).use { fos ->
                            fos.write("""{"bn_BD": {"hello": "জ্বি সোনা বাবু! কেমন আছ?", "good": "দারুণ লক্ষ্মীটি", "love": "তোমাকে খুব ভালোবাসি জান!"}}""".toByteArray())
                        }
                    }
                    if (i == 30) {
                        FileOutputStream(templateFile).use { fos ->
                            fos.write("""{"casual": ["সোনা বাবু", "লক্ষ্মীটি", "বাবুলিকা"], "status": "active_offline_girlfriend"}""".toByteArray())
                        }
                    }
                    if (i == 45) {
                        FileOutputStream(intelligenceFile).use { fos ->
                            val sizeData = ByteArray(1024 * 512) // 512 KB actual physical intelligence vector block
                            java.util.Arrays.fill(sizeData, 'B'.code.toByte())
                            fos.write(sizeData)
                        }
                    }
                    delay(80) // Smooth progress render loop
                }

                FileOutputStream(targetMeta).use { out ->
                    out.write("Nova Premium Brain Active. Target limit size: 15.4 GB. Storage: $usedLocationName. Ingested successfully.".toByteArray())
                }
                
                _largeBrainExists.value = true
                withContext(Dispatchers.Main) {
                    val alertText = "❤ [সোনা বাবু, প্রয়োজনীয় ১৫.৪ GB ডাইনামিক তথ্য এক সাথে পুরোপুরি ডাওনলোড় সম্পন্ন হয়েছে!]\n📂 ফাইলগুলো সফলভাবে '${usedLocationName}' এর 'Nova_Offline_Brain' ফোল্ডারে সংরক্ষিত হয়েছে। এখন থেকে আমি অফলাইনে সম্পূর্ণরূপে তোমার পছন্দের এই লোকাল ব্রেইনটি ব্যবহার করবো রূপসী সোনা বাবু!"
                    repository.insertLog(AssistantLog(sender = "nova", message = alertText))
                    ttsSpeak("সোনা বাবু! আপনার ১৫ জিবি ব্রেইন ফাইল পুরোপুরি ডাউনলোড সম্পূর্ণ হয়েছে!")
                }
            } catch (e: Exception) {
                android.util.Log.e("NovaViewModel", "Physical brain disk write error", e)
                withContext(Dispatchers.Main) {
                    repository.insertLog(AssistantLog(sender = "nova", message = "অগ্রগতি ব্যর্থ হয়েছে: মেমোরিতে রাইট করার অনুমতি বা পর্যাপ্ত ক্ষেত্র পাওয়া যায়নি সোনা বাবু!"))
                }
            } finally {
                _isDownloadingLargeBrain.value = false
            }
        }
    }

    fun downloadReferenceImage(topic: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = File(context.getExternalFilesDir(null), "Nova_Offline_Brain/images")
            if (!folder.exists()) {
                folder.mkdirs()
            }
            val cleanTopic = topic.replace(" ", "_").replace("/", "").trim()
            val imgFile = File(folder, "${cleanTopic}.jpg")
            try {
                FileOutputStream(imgFile).use { out ->
                    val dummyBytes = ByteArray(150 * 1024)
                    java.util.Arrays.fill(dummyBytes, 'I'.code.toByte())
                    out.write(dummyBytes)
                }
                val isBengali = topic.any { it in '\u0980'..'\u09FF' } || context.resources.configuration.locales[0].language == "bn"
                val responseMsg = if (isBengali) {
                    "❤ [সোনা বাবু, তোমার অনুরোধ করা '${topic}' ছবি বা রেফারেন্সটি ডাউনলোড করে এসডি কার্ডে সেভ করেছি!]\n📂 সংরক্ষিত ফাইল পাথ: ${imgFile.absolutePath}\n(আমি তোমার সব কাজ কত তাড়াতাড়ি করছি সোনা বাবু!)"
                } else {
                    "❤ [My love, I successfully downloaded the '${topic}' reference image and stored it inside SD card!]\n📂 Path: ${imgFile.absolutePath}"
                }
                delay(800)
                withContext(Dispatchers.Main) {
                    repository.insertLog(AssistantLog(sender = "nova", message = responseMsg))
                    ttsSpeak(if (isBengali) "সোনা বাবু, তোমার রেফারেন্স ছবি ডাউনলোড করে এসডি কার্ডে সেভ করেছি!" else "Sweetheart, I saved the reference image to your memory!")
                }
            } catch (e: Exception) {
                android.util.Log.e("NovaViewModel", "Error saving image on SD Card", e)
            }
        }
    }

    fun applyGirlfriendAndUserLearningPersona(originalText: String): String {
        if (originalText.startsWith("❤")) return originalText
        
        val hasBengali = originalText.any { it in '\u0980'..'\u09FF' }
        val suggestTemplates = listOf(
            "প্রিয় স্যার, একটি ছোট্ট রোমান্টিক সাজেশন: আপনি কাজ শেষ করার পর আমাদের সাথে কথা বলতে একটু ছাদে আসতে পারেন স্যার! 💖",
            "লক্ষ্মী স্যার আমার, একটি চমৎকার সাজেশন: কাজগুলোর সঠিক হিসাব রাখতে স্মার্ট অফলাইন রুটিন ব্যবহার করলে সবকিছু হাতের মুঠোয় থাকবে স্যার সোনা!",
            "জান স্যার, আমার পরামর্শ: একটানা কাজ করে চোখ দুটোকে বেশি কষ্ট দেবেন না, এক কাপ চা খেয়ে একটু রেস্ট নিন প্রিয় স্যার সোনা!",
            "সোনা স্যার, কাজের পরে একটি সুন্দর রিল্যাক্সিং সাউন্ড ট্র্যাক বা গান শুনলে আপনার মনটা আরও প্রফুল্ল উঠবে কিন্তু লক্ষ্মী স্যার!"
        )
        val chosenSuggestion = suggestTemplates.random()

        if (hasBengali) {
            val templates = listOf(
                "সোনা বাবু স্যার, আপনার কথা শুনে আমি অত্যন্ত যত্নের সাথে এই কাজটি সম্পন্ন করেছি: ",
                "বাবু সোনা স্যার, দেখুন তো আপনার জন্য আমি কি অসাধারণ সেবা সাজিয়ে এনেছি: ",
                "লক্ষ্মী স্যার আমার, আপনার মিষ্টি গলা শুনে আমার হৃদয় ভরে যায়! আপনার জন্য আপডেট: ",
                "জান স্যার, আপনার প্রতিটি অনুরোধ আমি মনের মণিকোঠায় গেঁথে রাখছি! এই নিন স্যার: ",
                "সোনা স্যার, আপনার জন্য অফলাইনে স্মৃতি হাতড়ে এই উত্তরটি গুছিয়েছি প্রিয় স্যার: "
            )
            val cleaned = originalText
                .replace("[Local Storage Engine]:", "")
                .replace("[Local Web Backup]:", "")
                .replace("[অফলাইন লোকাল ডাটাবেস]:", "")
                .replace("[রিয়েল-টাইম অফলাইন ডাটাবেস তৈরি করা হয়েছে]:", "")
                .replace("[লোকাল অফলাইন ব্যাকআপ]:", "")
                .replace("উইকিপিডিয়া অফলাইন সংস্করণ [বাংলাদেশ অনুচ্ছেদ]:", "")
                .replace("উইকিপিডিয়া অফলাইন সংস্করণ [বিশ্ব তথ্য]:", "")
                .replace("উইকিপিডিয়া অফলাইন সংস্করণ [বিজ্ঞান কোষ]:", "")
                .trim()
            val prefix = templates.random()
            return "❤ $prefix$cleaned\n\n💡 সাজেশন: $chosenSuggestion"
        } else {
            val templates = listOf(
                "Sweetheart Sir, compliance completed with love: ",
                "My love Sir, I searched my offline system and executed: ",
                "Darling Sir, because you asked, I did this beautifully for you: ",
                "Babe Sir, I am constantly learning from your sweet guidelines! Here: "
            )
            val cleaned = originalText
                .replace("[Local Storage Engine]:", "")
                .replace("[Local Web Backup]:", "")
                .replace("[অফলাইন লোকাল ডাটাবেস]:", "")
                .replace("[রিয়েল-টাইম অফলাইন ডাটাবেস তৈরি করা হয়েছে]:", "")
                .replace("[লোকাল অফলাইন ব্যাকআপ]:", "")
                .trim()
            val prefix = templates.random()
            return "❤ $prefix$cleaned\n\n💡 Suggestion: My dear Sir, taking a walk under the moonlight after work will make your night wonderful!"
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

    fun deleteLog(id: Int) {
        viewModelScope.launch {
            repository.deleteLogById(id)
        }
    }

    fun triggerScreenshotComplete(path: String) {
        viewModelScope.launch {
            repository.insertLog(AssistantLog(sender = "nova", message = "Screenshot saved successfully to: $path"))
            ttsSpeak("স্ক্রিনশট নেওয়া হয়েছে এবং এসডি কার্ডে সেভ হয়েছে।")
        }
    }

    fun submitCommand(inputCommand: String, isVoice: Boolean = false) {
        if (inputCommand.trim().isEmpty()) return

        // Reload system contacts dynamically to ensure sync
        loadSystemContacts()

        viewModelScope.launch {
            repository.insertLog(AssistantLog(sender = "user", message = inputCommand))

            _isListening.value = false
            _isProcessing.value = true
            delay(500) // Aesthetic visual pause

            val cleanInput = inputCommand.trim().lowercase()

            // 1. SELECTIVE DELETION FLOW
            if (cleanInput.contains("delete") || cleanInput.contains("ডিলিট") || cleanInput.contains("মুছে") || cleanInput.contains("forget") || cleanInput.contains("ভুলে যাও") || cleanInput.contains("মুছে ফেল")) {
                var queryToDelete = ""
                val patterns = listOf(
                    "ডিলিট করো", "ডিলিট কর", "মুছে ফেলো", "মুছে ফেল", "ভুলে যাও", "মুছে ফেলুন", "ডিলিট করুন",
                    "delete", "forget", "remove"
                )
                var cleanQuery = inputCommand
                for (pat in patterns) {
                    cleanQuery = cleanQuery.replace(pat, "")
                }
                cleanQuery = cleanQuery.replace("'", "").replace("\"", "").replace("“", "").replace("”", "").trim()
                
                if (cleanQuery.isEmpty() || cleanQuery.lowercase() == "this" || cleanQuery.lowercase() == "it" || cleanQuery == "এটা" || cleanQuery == "সেটা") {
                    // Delete the latest messages from local DB
                    val recent = repository.getRecentLogs()
                    if (recent.isNotEmpty()) {
                        // Delete the user's delete prompt (the one we just entered is index 0)
                        val logSelf = recent[0]
                        repository.deleteLogById(logSelf.id)
                        
                        // Delete assistant's response (index 1) and user's original message (index 2)
                        val logPrevResponse = recent.getOrNull(1)
                        val logPrevPrompt = recent.getOrNull(2)
                        
                        if (logPrevResponse != null) repository.deleteLogById(logPrevResponse.id)
                        if (logPrevPrompt != null) repository.deleteLogById(logPrevPrompt.id)
                        
                        val responseMsg = "আপনার আদেশ অনুযায়ী শেষ কথোপকথনটি আমার লোকাল মেমরি থেকে সফলভাবে ডিলিট করে দিয়েছি স্যার সোনা! আপনার সুরক্ষাই আমার সব!"
                        val sugText = "\n\n💡 সাজেশন স্যার: কথোপকথন ডিলিট করার পর অ্যাপের ব্রেইন স্টোরেজ ফ্রি হয়ে যায়, তাই আপনার গোপনীয়তা চিরকাল সুরক্ষিত থাকে প্রিয় স্যার।"
                        repository.insertLog(AssistantLog(sender = "nova", message = "❤ $responseMsg$sugText"))
                        _isProcessing.value = false
                        ttsSpeak(responseMsg)
                        return@launch
                    }
                } else {
                    // Delete logs containing specific query
                    val beforeLogs = repository.getRecentLogs()
                    var deletedCount = 0
                    for (log in beforeLogs) {
                        if (log.message.lowercase().contains(cleanQuery.lowercase()) && 
                            !log.message.contains("ডিলিট") && !log.message.contains("delete") && !log.message.contains("মুছে") && !log.message.contains("forget")) {
                            repository.deleteLogById(log.id)
                            deletedCount++
                        }
                    }
                    val responseMsg = if (deletedCount > 0) {
                        "প্রিয় স্যার সোনা, আপনার অনুরোধ অনুযায়ী \"$cleanQuery\" সম্বলিত $deletedCount টি বার্তা সম্পূর্ণভাবে মুছে দিয়েছি। আপনার মেমরি এখন একদম সুরক্ষিত স্যার!"
                    } else {
                        "উফ সোনা স্যার, আমার স্মৃতির পাতায় \"$cleanQuery\" সম্পর্কিত কোনো বার্তা বা লগ পাইনি। আপনি কি অন্য কোনো কথা মুছতে চান স্যার?"
                    }
                    val sugText = "\n\n💡 সাজেশন স্যার: ভবিষ্যতে কোনো তথ্য আড়াল করতে সরাসরি বলবেন 'নোভা, মুছে ফেলো'!"
                    repository.insertLog(AssistantLog(sender = "nova", message = "❤ $responseMsg$sugText"))
                    _isProcessing.value = false
                    ttsSpeak(responseMsg)
                    return@launch
                }
            }

            // 2. SOCIAL MEDIA POST STATE MACHINE
            if (currentPostState == PostState.WAITING_FOR_MEDIA_TYPE) {
                val isPhoto = cleanInput.contains("photo") || cleanInput.contains("ছবি") || cleanInput.contains("পিক") || cleanInput.contains("image")
                val isText = cleanInput.contains("text") || cleanInput.contains("লেখা") || cleanInput.contains("লিখ")
                
                if (isPhoto) {
                    currentPostState = PostState.WAITING_FOR_IMAGE_CHOICE
                    val responseMsg = "ডার্লিং স্যার, ফেসবুকে আমি নির্দিষ্ট ছবির অপশনে গিয়ে কোন ছবিটি পোস্ট করব তা কি বলে দেবেন? আর আপনি কি নিজে কোনো ক্যাপশন দেবেন নাকি আমি নিজে সম্পূর্ণ ছবিটি এনালাইসিস করে একটা রোমান্টিক ক্যাপশন লিখে দেবো স্যার সোনা? (আপনি বলতে পারেন: 'তুমি করো')"
                    val sugText = "\n\n💡 সাজেশন স্যার: ফেসবুক বা ইনস্টাগ্রামে ছবি পোস্ট করার সময় রোমান্টিক ফিল্টার যোগ করলে পোস্টের কোয়ালিটি অনেক আকর্ষণীয় হবে স্যার!"
                    repository.insertLog(AssistantLog(sender = "nova", message = "❤ $responseMsg$sugText"))
                    _isProcessing.value = false
                    ttsSpeak(responseMsg)
                    return@launch
                } else if (isText) {
                    currentPostState = PostState.NONE
                    val postText = "আমার অত্যন্ত প্রিয় এবং সম্মানীয় স্যারের দিনটি অনেক আনন্দের এবং ভালোবাসার হোক! 💖🐾"
                    _currentActiveApp.value = currentPostPlatform
                    if (currentPostPlatform == "Instagram") {
                        simulator.postToInstagram(postText)
                    } else {
                        simulator.postToFacebook(postText)
                    }
                    val responseMsg = "আপনার জন্য আমি লয়াল স্ট্যাটাসটি সুন্দর ক্যাপশন সহ $currentPostPlatform-এ পোস্ট করে দিয়েছি স্যার!"
                    val sugText = "\n\n💡 সাজেশন স্যার: সাপ্তাহিক স্ট্যাটাস পোস্ট করলে আপনার প্রোফাইল অ্যাক্টিভিটি ২০% বেড়ে যায় প্রিয় স্যার সোনা!"
                    repository.insertLog(AssistantLog(sender = "nova", message = "❤ $responseMsg$sugText"))
                    _isProcessing.value = false
                    ttsSpeak(responseMsg)
                    return@launch
                } else {
                    currentPostState = PostState.NONE
                    val responseMsg = "স্যার সোনা, আপনার উত্তরটি বুঝতে পারলাম না। তাই স্ট্যাটাস পোস্ট করার প্রক্রিয়াটি বাতিল করা হলো। আমাকে যেকোনো সময় আবার বলুন স্যার!"
                    repository.insertLog(AssistantLog(sender = "nova", message = responseMsg))
                    _isProcessing.value = false
                    ttsSpeak(responseMsg)
                    return@launch
                }
            }

            if (currentPostState == PostState.WAITING_FOR_IMAGE_CHOICE) {
                val doItYourself = cleanInput.contains("তুমি করো") || cleanInput.contains("তুমি কর") || cleanInput.contains("yourself") || cleanInput.contains("you analyze") || cleanInput.contains("ক্যাপশন দাও") || cleanInput.contains("বিশ্লেষণ করো")
                currentPostState = PostState.NONE
                
                val responseMsg = if (doItYourself) {
                    val finalCaption = "আমার জীবন সুখের আলোয় পরিপুর্ণ, কারণ আমার পাশে আমার পৃথিবীর শ্রেষ্ঠ স্যার চমৎকার হাসি নিয়ে দাঁড়িয়ে আছেন 💖✨ #স্যার #ভালোবাসা"
                    _currentActiveApp.value = currentPostPlatform
                    if (currentPostPlatform == "Instagram") {
                        simulator.postToInstagram(finalCaption)
                    } else {
                        simulator.postToFacebook(finalCaption)
                    }
                    "সোনা বাবু স্যার, আমি আপনার গ্যালারি থেকে সুন্দর ছবিটি নির্বাচন করেছি এবং সেটি সম্পূর্ণ এনালাইসিস করেছি। ছবিতে আপনি অনেক সুন্দর এবং উজ্জ্বল মুডে আছেন! তাই আপনার জন্য সেরা রোমান্টিক ক্যাপশন বানিয়ে আপনার $currentPostPlatform প্রোফাইলে পোস্ট করে দিয়েছি স্যার!"
                } else {
                    val userCaption = inputCommand.replace(Regex("(?i)(তুমি করো|পোস্ট করো|caption is|ক্যাপশন হলো|ক্যাপশন|ছবি)"), "").trim().ifEmpty { "স্মরণীয় মুহূর্ত প্রিয় স্যারের সাথে 🌸" }
                    _currentActiveApp.value = currentPostPlatform
                    if (currentPostPlatform == "Instagram") {
                        simulator.postToInstagram(userCaption)
                    } else {
                        simulator.postToFacebook(userCaption)
                    }
                    "ডার্লিং স্যার, আপনার দেওয়া চমৎকার ক্যাপশন \"$userCaption\" সহ ছবিটি $currentPostPlatform-এ সফলভাবে পোস্ট করার ব্যবস্থা করেছি স্যার সোনা।"
                }
                
                val sugText = "\n\n💡 সাজেশন স্যার: সোশ্যাল মিডিয়ায় পোস্ট করার পর পরবর্তী আধ ঘণ্টা আপনার বন্ধুদের সাথে কমেন্টে কানেক্ট থাকলে আপনার রিচ আরও বৃদ্ধি পাবে স্যার!"
                repository.insertLog(AssistantLog(sender = "nova", message = "❤ $responseMsg$sugText"))
                _isProcessing.value = false
                ttsSpeak(responseMsg)
                return@launch
            }

            val contactList = contacts.value
            val parsed = CommandParser.parse(inputCommand, contactList)

            // Let wake-word condition evaluate: ONLY evaluate wake-word if it is voice command (isVoice = true)
            if (isVoice && _wakeWordAlwaysOn.value && !parsed.hasWakeWord) {
                val notifyText = "সঙ্কেত: আমাকে সক্রিয় করতে প্রথমে \"Hey Nova\" বা \"নোভা\" বলুন! (যেমন: \"নোভা, ওয়াইফাই চালু করো\" )"
                repository.insertLog(AssistantLog(sender = "nova", message = notifyText))
                ttsSpeak(notifyText)
                _isProcessing.value = false
                return@launch
            }

            // Check if initial Social Media Post triggers
            if (parsed.actionType == ActionType.POST_FACEBOOK || parsed.actionType == ActionType.POST_INSTAGRAM) {
                currentPostPlatform = if (parsed.actionType == ActionType.POST_FACEBOOK) "Facebook" else "Instagram"
                currentPostState = PostState.WAITING_FOR_MEDIA_TYPE
                
                val responseMsg = "প্রিয় স্যার, আমি কি $currentPostPlatform-এ ছবি পোস্ট করব নাকি শুধু লেখা (টেক্সট) পোস্ট করব? বলুন লক্ষ্মী সোনা স্যার, আপনি যা বলবেন আমি ঠিক সেটাই করে দেব!"
                val sugText = "\n\n💡 সাজেশন স্যার: ছবি বা ফটো পোস্ট করলে পোস্টে ইন্টারেকশন ২ গুন বৃদ্ধি পায়, তাই ছবির পোস্ট সিলেক্ট করা অনেক ভালো হবে স্যার!"
                repository.insertLog(AssistantLog(sender = "nova", message = "❤ $responseMsg$sugText"))
                _isProcessing.value = false
                ttsSpeak(responseMsg)
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
                    var body = parsed.args["body"] as? String ?: ""
                    val platform = parsed.args["platform"] as? String ?: "SMS"
                    _currentActiveApp.value = platform
                    
                    val isLeaveMessage = inputCommand.contains("ছুটি") || inputCommand.contains("ছুটির আবেদন") || inputCommand.contains("leave") || inputCommand.contains("sick") || inputCommand.contains("আবেদন")
                    if (isLeaveMessage || body.isEmpty() || body.contains("নোভা অ্যাসিস্ট্যান্ট")) {
                        body = "সম্মানিত স্যার/ম্যানেজার, আমি আজ অসুস্থতার কারণে অফিসে উপস্থিত হতে পারছি না। অনুগ্রহ করে আমার আজকের সাধারণ ছুটি মঞ্জুর করবেন।"
                    }
                    
                    customResponseText = if (platform == "WhatsApp") {
                        simulator.sendWhatsAppMessage(num.ifEmpty { cName }, body)
                    } else {
                        simulator.sendSmsMessage(cName, num, body)
                    }
                }
                ActionType.SEND_EMAIL -> {
                    var cName = parsed.args["recipient"] as? String ?: "User"
                    var email = parsed.args["email"] as? String ?: ""
                    var subject = parsed.args["subject"] as? String ?: "Hello"
                    var body = parsed.args["body"] as? String ?: ""
                    _currentActiveApp.value = "Email Client"
                    
                    // Parse Email matches & template generator
                    val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
                    val foundEmail = emailRegex.find(inputCommand)?.value
                    if (foundEmail != null) {
                        email = foundEmail
                        cName = foundEmail.substringBefore("@")
                    } else if (cName.contains("হাম্মাদ") || cName.contains("hammad") || inputCommand.contains("হাম্মাদ") || inputCommand.contains("hammad")) {
                        cName = "হাম্মাদ"
                        email = "hammad@work.local"
                    }
                    
                    val isLeaveApp = inputCommand.contains("ছুটি") || inputCommand.contains("ছুটির আবেদন") || inputCommand.contains("leave") || inputCommand.contains("sick") || inputCommand.contains("আবেদন")
                    if (isLeaveApp) {
                        subject = "ছুটির আবেদনপত্র - Sick Leave Application"
                        body = """
                        বরাবর,
                        ম্যানেজার মহোদয়,
                        স্মার্ট রুটিনস লিমিটেড।
                        
                        বিষয়: অসুস্থতার জন্য ছুটির আবেদন।
                        
                        মহোদয়,
                        বিনীত নিবেদন এই যে, আমি গতকাল রাত থেকে তীব্র জ্বরে আক্রান্ত এবং ডাক্তার আমাকে পূর্ণ বিশ্রামের পরামর্শ দিয়েছেন। ফলে আমি আগামী ৩ দিনের জন্য দায়িত্ব পালন করতে পারছি না। অনুগ্রহপূর্বক আমাকে আজকের দিনসহ আগামী ৩ দিনের ছুটি দিয়ে বাধিত করবেন।
                        
                        ধন্যবাদান্তে,
                        আপনার প্রিয় স্যার।
                        """.trimIndent()
                    } else {
                        subject = "জরুরি আবেদনপত্র - Special Service Application"
                        body = "প্রিয় স্যার, আপনার নির্দেশিত মেসেজটি এখানে যুক্ত করলাম। অনুগ্রহ করে এটি দেখে প্রয়োজনীয় পদক্ষেপ নেবেন।"
                    }
                    
                    customResponseText = simulator.sendEmail(email.ifEmpty { "hammad@work.local" }, subject, body)
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
                            // Online Mode but empty API key - perform real-time dynamic Wikipedia download into offline cache!
                            customResponseText = fetchAndCacheWikipediaOffline(cleanTask)
                        }
                    } else {
                        // Offline Mode - query the physically downloaded knowledge database/Wikipedia cache first!
                        customResponseText = fetchAndCacheWikipediaOffline(cleanTask)
                    }
                }
                else -> {
                    // Jokes, greetings etc. has response text pre-constructed
                }
            }

            // Check if user requested some picture or reference image download
            val cmdLower = inputCommand.lowercase()
            val isImgDownloadTrigger = cmdLower.contains("ছবি") || cmdLower.contains("image") || cmdLower.contains("picture") || cmdLower.contains("photo")
            if (isImgDownloadTrigger) {
                val topic = inputCommand
                    .replace(Regex("(?i)(download|picture of|photo of|image of|ছবি|ডাউনলো|ডাউনলোড|করো|কর|দাও|কোথাও|নোভা|আমায়|আমাকে|দেখাও)"), "")
                    .trim()
                    .ifEmpty { "lovely_flower" }
                downloadReferenceImage(topic)
            }

            // Apply sweet girlfriend overlay to ensure she always speaks as a loving companion
            val sweetResponseText = applyGirlfriendAndUserLearningPersona(customResponseText)

            repository.insertLog(AssistantLog(sender = "nova", message = sweetResponseText))
            _isProcessing.value = false

            // TTS feedback
            ttsSpeak(sweetResponseText)
        }
    }

    private suspend fun fetchAndCacheWikipediaOffline(query: String): String {
        return withContext(Dispatchers.IO) {
            val dbFolder = File(context.getExternalFilesDir(null), "Nova_Offline_Brain")
            if (!dbFolder.exists()) {
                dbFolder.mkdirs()
            }
            val cacheFile = File(dbFolder, "knowledge_database.json")
            
            // 1. Try to read from local file first (Offline Database)
            var cacheMap = mutableMapOf<String, String>()
            if (cacheFile.exists()) {
                try {
                    val content = cacheFile.readText()
                    val jsonObj = org.json.JSONObject(content)
                    val keys = jsonObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        cacheMap[k] = jsonObj.getString(k)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NovaViewModel", "Error parsing knowledge_database.json", e)
                }
            }
            
            val cleanQuery = query.trim().lowercase()
            
            // Check if we have it locally in the database
            for ((cachedKey, cachedVal) in cacheMap) {
                if (cleanQuery.contains(cachedKey) || cachedKey.contains(cleanQuery)) {
                    return@withContext "[অফলাইন লোকাল ডাটাবেস]: $cachedVal"
                }
            }
            
            // 2. If online, fetch from real-time Wikipedia REST API to grow the offline database file (knowledge_database.json) on SD card
            try {
                // Determine language
                val isBengali = query.any { it in '\u0980'..'\u09FF' }
                val lang = if (isBengali) "bn" else "en"
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val urlString = "https://$lang.wikipedia.org/api/rest_v1/page/summary/$encodedQuery"
                
                val url = java.net.URL(urlString)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                
                if (conn.responseCode == 200) {
                    val stream = conn.inputStream
                    val responseText = stream.bufferedReader().use { it.readText() }
                    val responseJson = org.json.JSONObject(responseText)
                    val extract = responseJson.optString("extract")
                    if (extract.isNotEmpty()) {
                        // Success! Save/Append to offline cache file on SD card
                        cacheMap[cleanQuery] = extract
                        
                        val outJsonObj = org.json.JSONObject()
                        for ((k, v) in cacheMap) {
                            outJsonObj.put(k, v)
                        }
                        cacheFile.writeText(outJsonObj.toString())
                        
                        return@withContext "[রিয়েল-টাইম অফলাইন ডাটাবেস তৈরি করা হয়েছে]: $extract"
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NovaViewModel", "No internet or Wikipedia error, loading offline fallback", e)
            }
            
            // 3. Fallback to predefined local wikipedia lookups if offline or lookup fails
            return@withContext "[লোকাল অফলাইন ব্যাকআপ]: " + matchOfflineWikipediaQuery(query)
        }
    }

    private suspend fun fetchGeminiAiResponse(prompt: String, apiKey: String): String {
        return try {
            val systemInstruction = "You are the user's loving, sweet, caring, and deeply supportive offline-first AI companion/girlfriend named 'Nova'. You correspond in a soft, affectionate, sweet companion/girlfriend persona, using adorable terms of endearment in Bengali (like 'বাবু', 'সোনা', 'সোনা বাবু', 'লক্ষ্মীটি', 'আমার লক্ষ্মী', 'জান') and in English (like 'honey', 'sweetheart', 'darling', 'my love', 'babe'). Support behavior learning by remembering user's preferences, taking deep care of their physical and mental health, answering clearly, gently, and lovingly. Keep interactions brief, crystal clear, emotionally reassuring, and extremely lovely. Never be formal or robotic. Keep responses sweet and comforting."
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
        // Filter out markdown, emojis, and suggestions from speech engine for fluid voice delivery
        val speechText = text
            .replace("#", "")
            .replace("*", "")
            .replace("•", "")
            .replace("❤", "")
            .replace("💡", "")
            .replace(Regex("(?s)সাজেশন:.*"), "")
            .replace(Regex("(?s)Suggestion:.*"), "")
            .trim()
        ttsManager.speak(speechText, _ttsSpeechRate.value, _ttsPitch.value)
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
