package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AssistantLog
import com.example.viewmodel.NovaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovaDashboard(viewModel: NovaViewModel) {
    val context = LocalContext.current
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val activeApp by viewModel.currentActiveApp.collectAsStateWithLifecycle()
    val alwaysOnWake by viewModel.wakeWordAlwaysOn.collectAsStateWithLifecycle()
    val mode by viewModel.modelMode.collectAsStateWithLifecycle()

    // Download/Provisioning status
    val isDownloading by viewModel.isDownloadingOfflineData.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val offlineDbSize by viewModel.offlineDataSizeMb.collectAsStateWithLifecycle()
    val offlineDbExists by viewModel.offlineDataExists.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var inputVal by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    var showPermissionsPanel by remember { mutableStateOf(false) }

    // Scroll to latest conversation bubbles automatically
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    // Classic Obsidian Slate palette
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF060608), 
            Color(0xFF0F0E13)  
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Pristine Sleek Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "N O V A",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6DFD5),
                            letterSpacing = 3.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (mode == "Online Hybrid") Color(0xFFD0BCFF) else Color(0xFFA8A29A)
                                    )
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = if (mode == "Online Hybrid") "Hybrid AI Core" else "Local Storage Engine (Direct)",
                                fontSize = 11.sp,
                                color = Color(0xFF8B8A9E),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Header Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Permissions panel drawer button
                        IconButton(
                            onClick = { showPermissionsPanel = !showPermissionsPanel },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF16142A), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF2E2445), RoundedCornerShape(8.dp))
                                .testTag("toggle_security_panel")
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = "System Security status",
                                tint = if (showPermissionsPanel) Color(0xFFD0BCFF) else Color(0xFF8B8A9E),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Clear Logs
                        IconButton(
                            onClick = { viewModel.clearAllLogs() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF121212), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF1F1F1F), RoundedCornerShape(8.dp))
                                .testTag("clear_logs_button")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear conversation buffers",
                                tint = Color(0xFFE6DFD5),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Local / Hybrid Toggle
                        IconButton(
                            onClick = { viewModel.toggleModelMode() },
                            modifier = Modifier
                                .height(36.dp)
                                .background(Color(0xFF121212), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF1F1F1F), RoundedCornerShape(8.dp))
                                .testTag("network_mode_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    if (mode == "Online Hybrid") Icons.Default.Cloud else Icons.Default.CloudOff,
                                    contentDescription = "Connection context toggle",
                                    tint = Color(0xFFE6DFD5),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (mode == "Online Hybrid") "Hybrid" else "Offline",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE6DFD5)
                                )
                            }
                        }
                    }
                }

                // Callouts for Download Provision status/Wikipedia offline databases
                AnimatedVisibility(
                    visible = isDownloading,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1625)),
                        border = BorderStroke(1.dp, Color(0xFF32284F)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFFD0BCFF), modifier = Modifier.size(18.dp))
                                    Text("Wikipedia Offline DB Provisioning...", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("${(downloadProgress * 100).toInt()}%", color = Color(0xFFD0BCFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = Color(0xFFD0BCFF),
                                trackColor = Color(0xFF100B1E)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Storing high-fidelity Wikipedia summary tables onto SD card: ${offlineDbSize} MB / 42 MB complete.",
                                color = Color(0xFF8B8A9E),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Display info when offline database exists
                AnimatedVisibility(
                    visible = !isDownloading && offlineDbExists && logs.size <= 2,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0E13)),
                        border = BorderStroke(0.5.dp, Color(0xFF1E1C24)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = Color(0xFFB39DDB), modifier = Modifier.size(16.dp))
                            Text(
                                text = "Offline Wiki Index loaded (${offlineDbSize} MB). Talk naturally in Bengali or English!",
                                color = Color(0xFFA8A29A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }

                // Collapsible Security/Permissions Overlay Control
                AnimatedVisibility(
                    visible = showPermissionsPanel,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Box(modifier = Modifier.padding(vertical = 8.dp)) {
                        InlinePermissionsPanel(viewModel) {
                            showPermissionsPanel = false
                        }
                    }
                }

                // 2. Central visual pulsing intelligence sphere
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.2f),
                    contentAlignment = Alignment.Center
                ) {
                    NovaPulsingSphere(isListening, isProcessing, isSpeaking, activeApp)
                }

                // 3. Suggestions Horizontal Row
                LazyRowSuggestions(
                    alwaysOnWake = alwaysOnWake,
                    onSuggestionClicked = { inputVal = it }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 4. Clean Immersive Chat Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2.0f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0B0B0D))
                        .border(1.dp, Color(0xFF16161B), RoundedCornerShape(14.dp))
                        .padding(8.dp)
                ) {
                    if (logs.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = Color(0x1AFFFFFF),
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "No logs registered.\nSay \"Hey Nova\" or \"নোভা, কল করো মম কে!\"",
                                color = Color(0x4DFFFFFF),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(logs) { log ->
                                SpeechBubble(log)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. Query Fields and Dynamic Mic trigger handles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Always listening wake switch
                    IconButton(
                        onClick = { viewModel.toggleWakeWord() },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (alwaysOnWake) Color(0xFFE6DFD5) else Color(0xFF121212),
                                RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, Color(0xFF1F1F1F), RoundedCornerShape(8.dp))
                            .testTag("wake_word_always_on")
                    ) {
                        Icon(
                            if (alwaysOnWake) Icons.Default.Hearing else Icons.Default.HearingDisabled,
                            contentDescription = "Toggle wake word trigger",
                            tint = if (alwaysOnWake) Color(0xFF0F0E13) else Color(0xFF8E8E93),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    OutlinedTextField(
                        value = inputVal,
                        onValueChange = { inputVal = it },
                        placeholder = { Text("নোভা কে জিজ্ঞেস করুন...", color = Color(0xFF8B8A9E), fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("command_input_field"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE6DFD5),
                            unfocusedBorderColor = Color(0xFF1A1A22),
                            focusedContainerColor = Color(0xFF121212),
                            unfocusedContainerColor = Color(0xFF121212)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputVal.trim().isNotEmpty()) {
                                viewModel.submitCommand(inputVal)
                                inputVal = ""
                                keyboardController?.hide()
                            }
                        }),
                        trailingIcon = {
                            if (inputVal.isNotEmpty()) {
                                IconButton(onClick = { inputVal = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                                }
                            }
                        }
                    )

                    // Execute mic/text trigger
                    IconButton(
                        onClick = {
                            if (inputVal.trim().isNotEmpty()) {
                                viewModel.submitCommand(inputVal)
                                inputVal = ""
                                keyboardController?.hide()
                            } else {
                                viewModel.setListening(!isListening)
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (isListening) Color(0xFFE6DFD5) else Color(0xFF121212),
                                RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, Color(0xFF1F1F1F), RoundedCornerShape(8.dp))
                            .testTag("action_execute_button")
                    ) {
                        Icon(
                            if (inputVal.trim().isNotEmpty()) Icons.Default.Send else Icons.Default.Mic,
                            contentDescription = "Trigger Speech input",
                            tint = if (isListening) Color(0xFF0F0E13) else Color(0xFFE6DFD5),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InlinePermissionsPanel(viewModel: NovaViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var hasMic by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var hasContacts by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var hasCall by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
    }
    var hasSms by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasMic = results[Manifest.permission.RECORD_AUDIO] ?: hasMic
        hasContacts = results[Manifest.permission.READ_CONTACTS] ?: hasContacts
        hasCall = results[Manifest.permission.CALL_PHONE] ?: hasCall
        hasSms = results[Manifest.permission.SEND_SMS] ?: hasSms
        viewModel.loadSystemContacts()
        Toast.makeText(context, "System permission states synchronized.", Toast.LENGTH_SHORT).show()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14131D)),
        border = BorderStroke(1.dp, Color(0xFF28253A)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Android System Permissions Sync",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close description", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Nova maps actual Android actions (routing dialers, sending WhatsApp/SMS, capturing offline speech). Standard permissions allow real interactions.",
                color = Color(0xFF8B8A9E),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.CALL_PHONE,
                            Manifest.permission.SEND_SMS
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("request_native_permissions_button")
            ) {
                Text("Sync Native System Permissions dialog", color = Color(0xFF120E2C), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PermissionMiniRow("Microphone Voice", "Reads microphone waveforms dynamically.", hasMic)
                PermissionMiniRow("Contacts Index", "Reads actual ContentProvider common names.", hasContacts)
                PermissionMiniRow("Call Dialer", "Instantly routes standard tel numbers.", hasCall)
                PermissionMiniRow("SMS Direct", "Opens sending SMS composer with values.", hasSms)
            }
        }
    }
}

@Composable
fun PermissionMiniRow(title: String, desc: String, isGranted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = Color(0xFF8B8A9E), fontSize = 10.sp)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) Color(0xFF81C784) else Color(0xFFE57373))
            )
            Text(
                text = if (isGranted) "Granted" else "Missing",
                color = if (isGranted) Color(0xFF81C784) else Color(0xFFE57373),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LazyRowSuggestions(
    alwaysOnWake: Boolean,
    onSuggestionClicked: (String) -> Unit
) {
    val prefixes = if (alwaysOnWake) listOf("Hey Nova, ", "নোভা, ", "Hey Assistant, ") else listOf("", "", "", "")
    val items = listOf(
        "কল করো মা কে",
        "send a WhatsApp message to Rahim",
        "গুগল করো বাংলাদেশ",
        "ফেসবুকে পোস্ট করো নোভা অনেক সুন্দর!",
        "open YouTube",
        "ওয়াইফাই চালু করো",
        "volume up",
        "স্ক্রিনশট নাও",
        "একটি কৌতুক বলো"
    )

    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(items) { baseCmd ->
            // Smart Bengali vs English prefixing
            val prefix = if (baseCmd.contains(Regex("[\\u0980-\\u09FF]")) || baseCmd.contains("মম") || baseCmd.contains("মা")) {
                prefixes[1]
            } else if (baseCmd.startsWith("what") || baseCmd.startsWith("tell") || baseCmd.startsWith("volume") || baseCmd.startsWith("send")) {
                prefixes[0]
            } else {
                ""
            }
            val finalCmd = prefix + baseCmd
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
                modifier = Modifier
                    .clickable { onSuggestionClicked(finalCmd) }
                    .border(1.dp, Color(0xFF1A1A1E), RoundedCornerShape(18.dp))
                    .testTag("suggestion_$baseCmd")
            ) {
                Text(
                    text = finalCmd,
                    color = Color(0xFFC7C7CC),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun NovaPulsingSphere(
    isListening: Boolean,
    isProcessing: Boolean,
    isSpeaking: Boolean,
    activeApp: String
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseSize by transition.animateFloat(
        initialValue = 135f,
        targetValue = 175f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_size"
    )

    val activeColor = when {
        isListening -> Color(0xFFD0BCFF)       // Soft purple light
        isProcessing -> Color(0xFFA8A29A)      // Warm Platinum Slate
        isSpeaking -> Color(0xFFE6DFD5)        // Ambient sand pearl
        else -> Color(0xFF2E2D38)              // Obsidian graphite glow
    }

    Box(
        modifier = Modifier
            .size(200.dp)
            .testTag("nova_intelligence_sphere"),
        contentAlignment = Alignment.Center
    ) {
        // Glowing Aura Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2, size.height / 2)
            drawCircle(
                color = activeColor.copy(alpha = if (isListening || isSpeaking || isProcessing) 0.15f else 0.05f),
                radius = pulseSize,
                center = centerOffset
            )
            drawCircle(
                color = activeColor.copy(alpha = if (isListening || isSpeaking || isProcessing) 0.35f else 0.10f),
                radius = pulseSize - 25f,
                center = centerOffset
            )
            // Delicate Orbit line
            drawCircle(
                brush = Brush.sweepGradient(listOf(activeColor, Color.Transparent, activeColor)),
                radius = pulseSize - 5f,
                center = centerOffset,
                style = Stroke(width = 1.5f, cap = StrokeCap.Round)
            )
        }

        // Central Minimalist Core Sphere
        Box(
            modifier = Modifier
                .size(75.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(activeColor, activeColor.copy(alpha = 0.8f), Color(0xFF060608))
                    )
                )
                .shadow(elevation = 10.dp, shape = CircleShape)
                .border(2.dp, activeColor.copy(alpha = 0.4f), CircleShape)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when {
                        isListening -> Icons.Default.Hearing
                        isProcessing -> Icons.Default.Cyclone
                        isSpeaking -> Icons.Default.VolumeUp
                        else -> Icons.Default.Mic
                    },
                    contentDescription = null,
                    tint = if (isListening || isSpeaking || isProcessing) Color(0xFF0F0E13) else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun SpeechBubble(log: AssistantLog) {
    val isUser = log.sender == "user"
    val bubbleColor = if (isUser) Color(0xFF1E1B29) else Color(0xFF121212)
    val textColor = if (isUser) Color.White else Color(0xFFE6DFD5)
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("speech_bubble_${log.sender}"),
        horizontalAlignment = alignment
    ) {
        Row(
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isUser) 14.dp else 2.dp,
                            bottomEnd = if (isUser) 2.dp else 14.dp
                        )
                    )
                    .background(bubbleColor)
                    .border(
                        1.dp,
                        if (isUser) Color(0xFF342E46) else Color(0xFF1F1F1F),
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isUser) 14.dp else 2.dp,
                            bottomEnd = if (isUser) 2.dp else 14.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = log.message,
                    color = textColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
        Text(
            text = formatTime(log.timestamp),
            color = Color(0x3BFFFFFF),
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
