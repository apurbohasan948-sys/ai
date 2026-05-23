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
import kotlinx.coroutines.launch
import com.example.viewmodel.NovaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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

    // Massive 15.4 GB Offline Brain downloader state bindings
    val isDownloadingLargeBrain by viewModel.isDownloadingLargeBrain.collectAsStateWithLifecycle()
    val largeBrainProgress by viewModel.largeBrainProgress.collectAsStateWithLifecycle()
    val largeBrainSizeGb by viewModel.largeBrainSizeGb.collectAsStateWithLifecycle()
    val largeBrainExists by viewModel.largeBrainExists.collectAsStateWithLifecycle()
    val backgroundSyncProgressMb by viewModel.backgroundSyncProgressMb.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var inputVal by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0D0C14),
                drawerContentColor = Color.White,
                modifier = Modifier.width(320.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                text = "N O V A   C O R E",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD0BCFF),
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Settings & Context Controls",
                                fontSize = 11.sp,
                                color = Color(0xFF8B8A9E)
                            )
                        }
                        HorizontalDivider(color = Color(0xFF1E1C29))
                    }

                    // Network Hybrid vs Local Mode Toggle Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF14131D)),
                            border = BorderStroke(1.dp, Color(0xFF28253A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Hybrid AI Core",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (mode == "Online Hybrid") "Hybrid Online Active" else "Direct Offline Local Engine",
                                            fontSize = 10.sp,
                                            color = Color(0xFF8B8A9E)
                                        )
                                    }
                                    Switch(
                                        checked = mode == "Online Hybrid",
                                        onCheckedChange = { viewModel.toggleModelMode() },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFFD0BCFF),
                                            checkedTrackColor = Color(0xFF5E35B1)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Wake Word Always-On Toggle Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF14131D)),
                            border = BorderStroke(1.dp, Color(0xFF28253A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Voice wake-word trigger",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (alwaysOnWake) "Always Listening Active" else "Wake Word Wake/Mic OFF",
                                            fontSize = 10.sp,
                                            color = Color(0xFF8B8A9E)
                                        )
                                    }
                                    Switch(
                                        checked = alwaysOnWake,
                                        onCheckedChange = { viewModel.toggleWakeWord() },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFFD0BCFF),
                                            checkedTrackColor = Color(0xFF5E35B1)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Wikipedia / Offline database downloader pane inside Drawer
                    item {
                        Column {
                            if (isDownloading) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C182A)),
                                    border = BorderStroke(1.dp, Color(0xFF382A5F)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Downloading Wikipedia Offline Database...",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { downloadProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(CircleShape),
                                            color = Color(0xFFD0BCFF),
                                            trackColor = Color(0xFF0F0A1E)
                                        )
                                        Text(
                                            text = "${(downloadProgress * 100).toInt()}% completo (${offlineDbSize} MB)",
                                            color = Color(0xFF8B8A9E),
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            } else if (offlineDbExists) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1C0F)),
                                    border = BorderStroke(0.5.dp, Color(0xFF1B3D23)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Save,
                                            contentDescription = null,
                                            tint = Color(0xFF81C784),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Offline Index Loaded (${offlineDbSize} MB)",
                                            color = Color(0xFF81C784),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            } else {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161422)),
                                    border = BorderStroke(1.dp, Color(0xFF28253A)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Download,
                                                contentDescription = null,
                                                tint = Color(0xFF8B8A9E),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Offline DB Pending",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Automatic Wikipedia provisioning triggers on first start if needed.",
                                            color = Color(0xFF8B8A9E),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Massive Offline Brain (15-16 GB) Downloader UI Card
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "O F F L I N E   B R A I N   S D",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF48FB1), // Sweet soft pink highlights
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            
                            if (isDownloadingLargeBrain) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1C24)),
                                    border = BorderStroke(1.dp, Color(0xFFE91E63)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                color = Color(0xFFF48FB1),
                                                strokeWidth = 1.5.dp
                                            )
                                            Text(
                                                text = "Downloading Companion Brain (15.4 GB)...",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            progress = { largeBrainProgress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(CircleShape),
                                            color = Color(0xFFF48FB1),
                                            trackColor = Color(0xFF261D22)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${(largeBrainProgress * 100).toInt()}% completed (${String.format(Locale.US, "%.2f", largeBrainSizeGb)} GB / 15.4 GB Saved under SD Card/Nova_Offline_Brain)",
                                            color = Color(0xFFF48FB1),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            } else if (largeBrainExists) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF142419)),
                                    border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = Color(0xFF81C784),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Offline Girlfriend Brain Active",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "15.4 GB full intelligence database indexed completely inside SD Card. Running pure offline routing without limits!",
                                            color = Color(0xFFA5D6A7),
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            } else {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF18151A)),
                                    border = BorderStroke(1.dp, Color(0xFF3C2F3D)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                tint = Color(0xFFF48FB1),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Download Large Brain (15.4 GB)",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Saves the complete offline companion intelligence, images, visual reference catalogs, and speech datasets to SD Card for limitless use.",
                                            color = Color(0xFF8B8A9E),
                                            fontSize = 9.sp,
                                            lineHeight = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { viewModel.triggerLargeBrainDownload() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("Provision 15-16 GB Database", fontSize = 11.sp, color = Color.White)
                                        }
                                    }
                                }
                            }

                            // Limitless passive background sync status panel
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0E17)),
                                border = BorderStroke(0.5.dp, Color(0xFF241F35)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF00E676))
                                        )
                                        Text(
                                            text = "Headless Syncing (No Limit)",
                                            color = Color(0xFF8B8A9E),
                                            fontSize = 9.sp
                                        )
                                    }
                                    Text(
                                        text = "+${String.format(Locale.US, "%.1f", backgroundSyncProgressMb)} MB dynamically downloaded",
                                        color = Color(0xFF00E676),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Permissions index panel inline inside drawer
                    item {
                        InlinePermissionsPanel(viewModel) {}
                    }

                    // Clear logs trigger row in Drawer
                    item {
                        Button(
                            onClick = {
                                viewModel.clearAllLogs()
                                scope.launch { drawerState.close() }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("clear_logs_button")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Clear conversation history",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Conversations Logs", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) {
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
                    // Pristine Sleek Header with Drawer navigation trigger
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFF16142A), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF28253A), RoundedCornerShape(8.dp))
                                .testTag("menu_drawer_button")
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Open Settings Control Index",
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

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
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4. Immersive Full-Height Chat Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
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
                                    "No logs registered.\nType naturally below to talk with Nova!",
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

                    // 3. Suggestions Horizontal Row
                    LazyRowSuggestions(
                        alwaysOnWake = alwaysOnWake,
                        onSuggestionClicked = { inputVal = it }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 5. Query Fields and Dynamic Mic trigger handles completely simplified
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

                        // Execute text direct trigger
                        IconButton(
                            onClick = {
                                if (inputVal.trim().isNotEmpty()) {
                                    viewModel.submitCommand(inputVal)
                                    inputVal = ""
                                    keyboardController?.hide()
                                }
                            },
                            enabled = inputVal.trim().isNotEmpty(),
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (inputVal.trim().isNotEmpty()) Color(0xFFE6DFD5) else Color(0xFF121212),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, Color(0xFF1F1F1F), RoundedCornerShape(8.dp))
                                .testTag("action_execute_button")
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send text input command",
                                tint = if (inputVal.trim().isNotEmpty()) Color(0xFF0F0E13) else Color(0xFF8E8E93),
                                modifier = Modifier.size(20.dp)
                            )
                        }
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
    val bubbleColor = if (isUser) Color(0xFF1E1B29) else Color(0xFF2C151B) // Rosy pink bubble for girlfriend
    val textColor = if (isUser) Color.White else Color(0xFFFFEBEE) // Warm sweet text color for girlfriend
    val alignment = if (isUser) Alignment.End else Alignment.Start

    val msg = log.message
    val hasImagePath = msg.contains("📂 Path: ") || msg.contains("📂 সংরক্ষিত ফাইল পাথ: ")
    val extractedPath = if (hasImagePath) {
        val token = if (msg.contains("📂 Path: ")) "📂 Path: " else "📂 সংরক্ষিত ফাইল পাথ: "
        msg.substringAfter(token).substringBefore("\n").trim()
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("speech_bubble_${log.sender}"),
        horizontalAlignment = alignment
    ) {
        // Name Header for girlfriend to make it sweet & humanized
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFE91E63),
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = "নোভা সোনা",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF48FB1)
                )
            }
        }

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
                        if (isUser) Color(0xFF342E46) else Color(0xFFE91E63), // Pink border for girlfriend's responses
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isUser) 14.dp else 2.dp,
                            bottomEnd = if (isUser) 2.dp else 14.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = log.message,
                        color = textColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Normal
                    )

                    // Render gorgeous high-contrast reference image snapshot card of the SD Storage JPG
                    if (extractedPath != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFE91E63), RoundedCornerShape(8.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF140A0D))
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color(0xFF880E4F), Color(0xFF1A000C))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = Color(0xFFF48FB1),
                                            modifier = Modifier.size(34.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "REFERENCE IMAGE SECURED",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "150 KB JPEG • Local SD Storage",
                                            color = Color(0xFFF48FB1),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "📂 SD File Path:",
                                        color = Color(0xFF8B8A9E),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = extractedPath,
                                        color = Color(0xFFFF80AB),
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
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
