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
import com.example.data.Contact
import com.example.data.Reminder
import com.example.data.Routine
import com.example.viewmodel.NovaViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovaDashboard(viewModel: NovaViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Unified color system (Cosmic Indigo Theme)
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0C091A), // Cosmic midnight top
            Color(0xFF141226)  // Tech dark violet base
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF16142A),
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Mic, contentDescription = "Voice Assistant") },
                    label = { Text("Assistant") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD0BCFF),
                        selectedTextColor = Color(0xFFD0BCFF),
                        indicatorColor = Color(0xFF2C2442),
                        unselectedIconColor = Color(0xFF8B8A9E),
                        unselectedTextColor = Color(0xFF8B8A9E)
                    ),
                    modifier = Modifier.testTag("tab_assistant")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Reminders") },
                    label = { Text("Reminders") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD0BCFF),
                        selectedTextColor = Color(0xFFD0BCFF),
                        indicatorColor = Color(0xFF2C2442),
                        unselectedIconColor = Color(0xFF8B8A9E),
                        unselectedTextColor = Color(0xFF8B8A9E)
                    ),
                    modifier = Modifier.testTag("tab_reminders")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Automation") },
                    label = { Text("Automation") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD0BCFF),
                        selectedTextColor = Color(0xFFD0BCFF),
                        indicatorColor = Color(0xFF2C2442),
                        unselectedIconColor = Color(0xFF8B8A9E),
                        unselectedTextColor = Color(0xFF8B8A9E)
                    ),
                    modifier = Modifier.testTag("tab_automation")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Shield, contentDescription = "Permissions & Settings") },
                    label = { Text("System") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFD0BCFF),
                        selectedTextColor = Color(0xFFD0BCFF),
                        indicatorColor = Color(0xFF2C2442),
                        unselectedIconColor = Color(0xFF8B8A9E),
                        unselectedTextColor = Color(0xFF8B8A9E)
                    ),
                    modifier = Modifier.testTag("tab_system")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> AssistantTab(viewModel)
                1 -> RemindersTab(viewModel)
                2 -> AutomationContactsTab(viewModel)
                3 -> SystemPermissionsTab(viewModel)
            }
        }
    }
}

@Composable
fun AssistantTab(viewModel: NovaViewModel) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val activeApp by viewModel.currentActiveApp.collectAsStateWithLifecycle()
    val alwaysOnWake by viewModel.wakeWordAlwaysOn.collectAsStateWithLifecycle()
    val mode by viewModel.modelMode.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var inputVal by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Keyboard state detection for scrolling to remains
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Upper Quick Stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NOVA AI",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (mode == "Online Hybrid") Color(0xFF81C784) else Color(0xFFFFB74D)
                            )
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = if (mode == "Online Hybrid") "Hybrid Core: Gemini" else "Local Core: Offline Mode",
                        fontSize = 12.sp,
                        color = Color(0xFF8B8A9E)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Clear state
                IconButton(
                    onClick = { viewModel.clearAllLogs() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF2C2442), RoundedCornerShape(12.dp))
                        .testTag("clear_logs_button")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Clear Session logs",
                        tint = Color(0xFFFF8A80),
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Mode Toggle
                IconButton(
                    onClick = { viewModel.toggleModelMode() },
                    modifier = Modifier
                        .height(40.dp)
                        .padding(horizontal = 4.dp)
                        .background(Color(0xFF2C2442), RoundedCornerShape(12.dp))
                        .testTag("network_mode_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (mode == "Online Hybrid") Icons.Default.Cloud else Icons.Default.CloudOff,
                            contentDescription = "Core Engine Mode",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (mode == "Online Hybrid") "Hybrid" else "Local",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Center visual pulsing sphere
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.3f),
            contentAlignment = Alignment.Center
        ) {
            NovaPulsingSphere(isListening, isProcessing, isSpeaking, activeApp)
        }

        // Suggestions bar
        LazyRowSuggestions(
            alwaysOnWake = alwaysOnWake,
            onSuggestionClicked = { suggestion ->
                inputVal = suggestion
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Chat Bubble area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x4016142A))
                .border(1.dp, Color(0xFF2E2445), RoundedCornerShape(16.dp))
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
                        tint = Color(0x33FFFFFF),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "No ongoing speech conversations.\nSay " + '"' + "Hey Nova" + '"' + " to activate.",
                        color = Color(0x55FFFFFF),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
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

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom query field & Toggle listener mic
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Wake mode indicator toggle
            IconButton(
                onClick = { viewModel.toggleWakeWord() },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (alwaysOnWake) Color(0xFF2C2442) else Color(0x22FFFFFF),
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("wake_word_always_on")
            ) {
                Icon(
                    if (alwaysOnWake) Icons.Default.Hearing else Icons.Default.HearingDisabled,
                    contentDescription = "Always Listening status toggle",
                    tint = if (alwaysOnWake) Color(0xFFD0BCFF) else Color(0xFF8B8A9E),
                    modifier = Modifier.size(24.dp)
                )
            }

            OutlinedTextField(
                value = inputVal,
                onValueChange = { inputVal = it },
                placeholder = { Text("Ask Nova anything...", color = Color(0xFF8B8A9E), fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_input_field"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFD0BCFF),
                    unfocusedBorderColor = Color(0xFF2E2445),
                    focusedContainerColor = Color(0xFF16142A),
                    unfocusedContainerColor = Color(0xFF16142A)
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
                            Icon(Icons.Default.Clear, contentDescription = "Clear Text", tint = Color.Gray)
                        }
                    }
                }
            )

            // Submit / Trigger listen mic
            IconButton(
                onClick = {
                    if (inputVal.trim().isNotEmpty()) {
                        viewModel.submitCommand(inputVal)
                        inputVal = ""
                        keyboardController?.hide()
                    } else {
                        // Toggle visual Listening mode
                        viewModel.setListening(!isListening)
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isListening) Color(0xFF00E5FF) else Color(0xFFD0BCFF),
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("action_execute_button")
            ) {
                Icon(
                    if (inputVal.trim().isNotEmpty()) Icons.Default.Send else Icons.Default.Mic,
                    contentDescription = "Dispatch voice trigger",
                    tint = Color(0xFF16142A),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun LazyRowSuggestions(
    alwaysOnWake: Boolean,
    onSuggestionClicked: (String) -> Unit
) {
    val prefixes = if (alwaysOnWake) listOf("Hey Nova, ", "Nova, ", "Hey Assistant, ") else listOf("", "", "", "")
    val items = listOf(
        "open YouTube",
        "open settings",
        "call Mom",
        "send a WhatsApp message to Rahim",
        "turn on WiFi",
        "volume up",
        "tell me a joke",
        "what reminders do I have today?"
    )

    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(items) { baseCmd ->
            val finalCmd = if (baseCmd.startsWith("what") || baseCmd.startsWith("tell") || baseCmd.startsWith("volume")) {
                prefixes[1] + baseCmd
            } else {
                prefixes[0] + baseCmd
            }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1B3E)),
                modifier = Modifier
                    .clickable { onSuggestionClicked(finalCmd) }
                    .border(0.5.dp, Color(0xFF382F57), RoundedCornerShape(12.dp))
                    .testTag("suggestion_$baseCmd")
            ) {
                Text(
                    text = finalCmd,
                    color = Color(0xFFE2D6FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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
    // Canvas animation states
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseSize by transition.animateFloat(
        initialValue = 180f,
        targetValue = 240f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_size"
    )

    val waveRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_rot"
    )

    // Base color matches assistant states
    val activeColor = when {
        isListening -> Color(0xFF00E5FF)       // Aqua/Teal Listening
        isProcessing -> Color(0xFF9E00FF)      // Amethyst AI thinking
        isSpeaking -> Color(0xFFFF2D55)        // Red/Pink speaker feedback
        else -> Color(0xFF2C2442)              // Standard sleep system status
    }

    val stateText = when {
        isListening -> "Hey Nova: Listening..."
        isProcessing -> "Nova: Synthesizing system control..."
        isSpeaking -> "Nova: Conversing out loud..."
        else -> "Standby wake-word state."
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .testTag("nova_pulsing_sphere"),
            contentAlignment = Alignment.Center
        ) {
            // Pulsing background rings
            Canvas(modifier = Modifier.fillMaxSize()) {
                val circleRadius = if (isListening || isSpeaking || isProcessing) pulseSize else 180f
                val brush = Brush.radialGradient(
                    colors = listOf(
                        activeColor.copy(alpha = 0.35f),
                        activeColor.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = circleRadius
                )
                drawCircle(
                    brush = brush,
                    radius = circleRadius,
                    center = center
                )

                // Draw technical decorative circular indicators
                if (isListening || isProcessing || isSpeaking) {
                    drawCircle(
                        color = activeColor.copy(alpha = 0.5f),
                        radius = 160f,
                        center = center,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(15f, 15f),
                                waveRotation
                            )
                        )
                    )
                }
            }

            // Core solid crystal ball
            Card(
                shape = CircleShape,
                modifier = Modifier
                    .size(130.dp)
                    .shadow(16.dp, CircleShape),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF130E26))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        activeColor.copy(alpha = 0.9f),
                                        Color(0xFF03010A)
                                    )
                                )
                            )
                    ) {
                        Icon(
                            imageVector = when {
                                isListening -> Icons.Default.Hearing
                                isProcessing -> Icons.Default.SettingsSuggest
                                isSpeaking -> Icons.Default.RecordVoiceOver
                                else -> Icons.Default.Mic
                            },
                            contentDescription = "Visual State Indicator Icon",
                            tint = Color.White,
                            modifier = Modifier
                                .size(42.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // State & Active Intent Overlay Log
        Text(
            text = stateText,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Display Active Application context
        Text(
            text = "Active Environment: $activeApp",
            color = Color(0xFFA19FB9),
            fontWeight = FontWeight.Light,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SpeechBubble(log: AssistantLog) {
    val isUser = log.sender == "user"
    val cardBg = if (isUser) Color(0xFF5D40A8) else Color(0xFF16142A)
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val txtColor = if (isUser) Color.White else Color(0xFFECEBFF)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_bubble_${log.sender}"),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E2445))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Android,
                        contentDescription = "Nova icon",
                        tint = Color(0xFFD0BCFF),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                modifier = Modifier
                    .border(
                        1.dp,
                        if (isUser) Color.Transparent else Color(0xFF2E2445),
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .shadow(4.dp, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = log.message,
                        color = txtColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTime(log.timestamp),
                        color = Color(0x60FFFFFF),
                        fontSize = 9.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            if (isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF5D40A8))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "User icon",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// Reminders local Persistence UI tab
@Composable
fun RemindersTab(viewModel: NovaViewModel) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    var openAddDialog by remember { mutableStateOf(false) }
    var titleInput by remember { mutableStateOf("") }
    var hourInput by remember { mutableStateOf("08") }
    var minuteInput by remember { mutableStateOf("30") }
    var periodInput by remember { mutableStateOf("AM") } // AM or PM

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "SQLite Task Memory",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "Track reminders and scheduler events offline",
                    fontSize = 12.sp,
                    color = Color(0xFF8B8A9E)
                )
            }

            Button(
                onClick = { openAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("add_reminder_trigger_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add tasks", tint = Color(0xFF130E26))
                    Text("Add", color = Color(0xFF130E26), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (reminders.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.PlaylistAdd,
                    contentDescription = null,
                    tint = Color(0x33FFFFFF),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "No offline reminders found.\nCreate one above to update the database.",
                    color = Color(0x88FFFFFF),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reminders) { reminder ->
                    ReminderListItem(
                        reminder = reminder,
                        onCheckedChange = { viewModel.toggleReminderCompleted(reminder) },
                        onDeleteClick = { viewModel.deleteManualReminder(reminder.id) }
                    )
                }
            }
        }
    }

    // SQLite Add Reminder Dialog sheet
    if (openAddDialog) {
        AlertDialog(
            onDismissRequest = { openAddDialog = false },
            containerColor = Color(0xFF1F1B3E),
            title = { Text("Schedule Offline Reminder", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Reminder Title", color = Color(0xFFBCAAA4)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFD0BCFF)
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_reminder_title_field")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = hourInput,
                            onValueChange = { if (it.length <= 2) hourInput = it },
                            label = { Text("HH", color = Color(0xFFBCAAA4)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFD0BCFF)
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minuteInput,
                            onValueChange = { if (it.length <= 2) minuteInput = it },
                            label = { Text("MM", color = Color(0xFFBCAAA4)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFD0BCFF)
                            ),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        // AM / PM Selectable Row
                        Row(
                            modifier = Modifier
                                .weight(1.5f)
                                .border(1.dp, Color(0xFF382F57), RoundedCornerShape(8.dp)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        if (periodInput == "AM") Color(0xFF5D40A8) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { periodInput = "AM" }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("AM", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(
                                        if (periodInput == "PM") Color(0xFF5D40A8) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { periodInput = "PM" }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("PM", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (titleInput.trim().isNotEmpty()) {
                            val finalTime = "${hourInput.padStart(2, '0')}:${minuteInput.padStart(2, '0')} $periodInput"
                            viewModel.addManualReminder(titleInput, finalTime)
                            titleInput = ""
                            openAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF)),
                    modifier = Modifier.testTag("dialog_reminder_confirm_button")
                ) {
                    Text("Save To SQLite", color = Color(0xFF130E26), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { openAddDialog = false }) {
                    Text("Cancel", color = Color(0xFFBCAAA4))
                }
            }
        )
    }
}

@Composable
fun ReminderListItem(
    reminder: Reminder,
    onCheckedChange: (Boolean) -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reminder_card_${reminder.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16142A)),
        border = BorderStroke(1.dp, Color(0xFF2E2445))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Checkbox(
                    checked = reminder.isCompleted,
                    onCheckedChange = onCheckedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFFD0BCFF),
                        checkmarkColor = Color(0xFF130E26)
                    ),
                    modifier = Modifier.testTag("reminder_checkbox_${reminder.id}")
                )

                Column {
                    Text(
                        text = reminder.title,
                        color = if (reminder.isCompleted) Color(0xFF8B8A9E) else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = if (reminder.isCompleted) LocalTextStyle.current.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else LocalTextStyle.current
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFFBCAAA4), modifier = Modifier.size(12.dp))
                        Text(
                            text = reminder.timeLabel,
                            color = Color(0xFFBCAAA4),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("delete_reminder_button_${reminder.id}")
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Reminder",
                    tint = Color(0xFFEF9A9A),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Dialog for seeded Automation routines & seed Contacts manager helper
@Composable
fun AutomationContactsTab(viewModel: NovaViewModel) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    var activeSubTab by remember { mutableIntStateOf(0) } // 0 -> Routines, 1 -> Contacts

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = Color(0xFF16142A),
            contentColor = Color(0xFFD0BCFF),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF2E2445), RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text("Automations") },
                modifier = Modifier.testTag("subtab_routines")
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = { Text("Contacts") },
                modifier = Modifier.testTag("subtab_contacts")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (activeSubTab == 0) {
            // Routines view execution console
            Text(
                "Automation Routines",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "Trigger automated, synchronized tasks like WiFi actions",
                fontSize = 12.sp,
                color = Color(0xFF8B8A9E)
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(routines) { routine ->
                    RoutineListItem(
                        routine = routine,
                        onTriggerClick = { viewModel.triggerRoutineSequence(routine) }
                    )
                }
            }
        } else {
            // Contacts database control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Local Contacts Manager",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Manage offline contacts for dials/SMS routing",
                        fontSize = 12.sp,
                        color = Color(0xFF8B8A9E)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(contacts) { contact ->
                    ContactListItem(
                        contact = contact,
                        onDelete = { viewModel.deleteManualContact(contact) }
                    )
                }
            }
        }
    }
}

@Composable
fun RoutineListItem(routine: Routine, onTriggerClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("routine_card_${routine.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16142A)),
        border = BorderStroke(1.dp, Color(0xFF2E2445))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = routine.actionsSummary,
                    color = Color(0xFF8B8A9E),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val list = routine.actionsList.split(",")
                    list.forEach { action ->
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF2C2442), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = action.replace("_", " "),
                                color = Color(0xFFD0BCFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onTriggerClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D40A8)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                    .testTag("trigger_routine_${routine.id}")
                    .border(1.dp, Color(0xFF7E57C2), RoundedCornerShape(10.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run Routine", tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("Trigger", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ContactListItem(contact: Contact, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("contact_card_${contact.name}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16142A)),
        border = BorderStroke(1.dp, Color(0xFF2E2445))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2442)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (contact.isFavorite) Icons.Default.Star else Icons.Default.Person,
                        contentDescription = null,
                        tint = if (contact.isFavorite) Color(0xFFFFD54F) else Color(0xFFD0BCFF),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = contact.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = contact.phoneNumber,
                        color = Color(0xFF8B8A9E),
                        fontSize = 12.sp
                    )
                    Text(
                        text = contact.email,
                        color = Color(0x60FFFFFF),
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("delete_contact_button_${contact.name}")
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete Contact",
                    tint = Color(0xFFEF9A9A),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// System Permission & Mocks Status Controller tab
@Composable
fun SystemPermissionsTab(viewModel: NovaViewModel) {
    val wifiActive by viewModel.wifiActive.collectAsStateWithLifecycle()
    val bluetoothActive by viewModel.bluetoothActive.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Observe local Android Permissions directly for SMS/Calls etc.
    val permissionsToRequest = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.RECORD_AUDIO
    )

    var hasSmsGrant by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    var hasContactsGrant by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var hasCallGrant by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
    }
    var hasMicGrant by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasSmsGrant = results[Manifest.permission.SEND_SMS] ?: hasSmsGrant
        hasContactsGrant = results[Manifest.permission.READ_CONTACTS] ?: hasContactsGrant
        hasCallGrant = results[Manifest.permission.CALL_PHONE] ?: hasCallGrant
        hasMicGrant = results[Manifest.permission.RECORD_AUDIO] ?: hasMicGrant
        Toast.makeText(context, "System permission states synchronized.", Toast.LENGTH_SHORT).show()
    }

    // Custom overlays or mock switches for other accessibility rules
    var mockAccessibilityService by remember { mutableStateOf(true) }
    var mockNotificationAccess by remember { mutableStateOf(true) }
    var mockOverlayPermission by remember { mutableStateOf(false) }
    var mockStorageAccess by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Security & System Permissions",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "Access settings details and coordinate Android features securely.",
            fontSize = 12.sp,
            color = Color(0xFF8B8A9E)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { permissionLauncher.launch(permissionsToRequest) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("request_native_permissions_button")
        ) {
            Text("Request Native Permissions Dialog", color = Color(0xFF130E26), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grid of permission cards
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Requested Voice permissions:", color = Color(0xFFD0BCFF), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                PermissionSwitchRow(
                    title = "Microphone Access",
                    description = "Required for wake-word listeners and speech recognition voice commands.",
                    isChecked = hasMicGrant,
                    onCheckedChange = { permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
                    tag = "perm_mic"
                )
            }
            item {
                PermissionSwitchRow(
                    title = "SMS Dispatch Permission",
                    description = "Allows sending texts to contacts via offline SMS actions.",
                    isChecked = hasSmsGrant,
                    onCheckedChange = { permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS)) },
                    tag = "perm_sms"
                )
            }
            item {
                PermissionSwitchRow(
                    title = "Contacts Directory Access",
                    description = "Scans SQLite database contacts for voice dial lookup matching.",
                    isChecked = hasContactsGrant,
                    onCheckedChange = { permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS)) },
                    tag = "perm_contacts"
                )
            }
            item {
                PermissionSwitchRow(
                    title = "Call Routing Permission",
                    description = "Allows dialing phone actions directly to the calling dialer.",
                    isChecked = hasCallGrant,
                    onCheckedChange = { permissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE)) },
                    tag = "perm_call"
                )
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Background System Integrations:", color = Color(0xFFD0BCFF), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                PermissionSwitchRow(
                    title = "Accessibility Service",
                    description = "Required to simulation tap, scroll, and read on-screen widgets.",
                    isChecked = mockAccessibilityService,
                    onCheckedChange = { mockAccessibilityService = it },
                    tag = "perm_accessibility"
                )
            }
            item {
                PermissionSwitchRow(
                    title = "Notification Panel Access",
                    description = "Allows reading, replying to, and filtering incoming alert notifications.",
                    isChecked = mockNotificationAccess,
                    onCheckedChange = { mockNotificationAccess = it },
                    tag = "perm_notifications"
                )
            }
            item {
                PermissionSwitchRow(
                    title = "System Overlays / Draw Top",
                    description = "Allows drawing custom assistant UI above other active apps.",
                    isChecked = mockOverlayPermission,
                    onCheckedChange = { mockOverlayPermission = it },
                    tag = "perm_overlays"
                )
            }
            item {
                PermissionSwitchRow(
                    title = "Storage / SD Card Control",
                    description = "Allows writing logs and Wikipedia summaries offline to SD card.",
                    isChecked = mockStorageAccess,
                    onCheckedChange = { mockStorageAccess = it },
                    tag = "perm_storage"
                )
            }
        }
    }
}

@Composable
fun PermissionSwitchRow(
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16142A)),
        border = BorderStroke(1.dp, Color(0xFF2E2445)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(description, color = Color(0xFF8B8A9E), fontSize = 11.sp, lineHeight = 14.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF130E26),
                    checkedTrackColor = Color(0xFFD0BCFF)
                ),
                modifier = Modifier.testTag(tag)
            )
        }
    }
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
