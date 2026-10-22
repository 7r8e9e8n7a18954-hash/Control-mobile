package com.universalaiagent.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VoiceChat
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.ChatMessage
import com.universalaiagent.data.model.ChatRole
import com.universalaiagent.automation.StepStatus
import com.universalaiagent.automation.TaskRun
import com.universalaiagent.ui.AutomationUiState
import com.universalaiagent.ui.AutomationViewModel
import com.universalaiagent.ui.ChatUiState
import com.universalaiagent.ui.ChatViewModel
import com.universalaiagent.ui.components.ConfirmDialog
import com.universalaiagent.ui.components.ErrorBanner
import com.universalaiagent.ui.components.MarkdownText
import com.universalaiagent.ui.components.StatusPill
import com.universalaiagent.ui.components.VoiceOrb
import com.universalaiagent.ui.theme.DangerRed
import com.universalaiagent.ui.theme.SuccessGreen
import com.universalaiagent.ui.theme.WarningAmber
import com.universalaiagent.voice.VoiceManagerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    automationViewModel: AutomationViewModel,
    onHistory: () -> Unit,
    onKeys: () -> Unit,
    onSettings: () -> Unit,
    onVoice: () -> Unit,
    onRouting: () -> Unit,
    onDiagnostics: () -> Unit,
    onScreen: () -> Unit,
    onTasks: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val voice by viewModel.voiceState.collectAsStateWithLifecycle()
    val conversation by viewModel.selectedConversation.collectAsStateWithLifecycle()
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val automation by automationViewModel.ui.collectAsStateWithLifecycle()
    val connected by automationViewModel.connected.collectAsStateWithLifecycle()
    val currentTask by automationViewModel.currentTask.collectAsStateWithLifecycle()
    val pending by automationViewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        if (granted) viewModel.startVoiceInput()
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.attachImage(uri)
    }

    val isBusy = ui.isGenerating || automation.running

    LaunchedEffect(currentTask?.id, currentTask?.finishedAt) {
        val task = currentTask
        if (task != null && task.finishedAt != null) {
            viewModel.appendAgentOutcome(task.id, task.success, task.report)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopVoiceInput() }
    }

    val onVoiceOrb = {
        if (!hasMicPermission) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else if (voice.listening) {
            viewModel.stopVoiceInput()
        } else {
            viewModel.startVoiceInput()
        }
    }

    val onSend = {
        if (ui.agentMode) {
            if (connected) {
                val instruction = viewModel.takeAgentInstruction()
                if (instruction != null) {
                    automationViewModel.setInstruction(instruction)
                    automationViewModel.runTask()
                }
            }
        } else {
            viewModel.send()
        }
    }

    pending?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { automationViewModel.respondConfirmation(false) },
            title = { Text("Agent needs confirmation") },
            text = {
                Column {
                    Text(confirmation.reason)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Action: ${confirmation.action}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The agent is paused until you approve or decline. It will wait as long as you need.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { automationViewModel.respondConfirmation(true) }) {
                    Text("Approve")
                }
            },
            dismissButton = {
                TextButton(onClick = { automationViewModel.respondConfirmation(false) }) {
                    Text("Decline")
                }
            },
        )
    }

    val messages = conversation?.messages.orEmpty()
    val hasStreaming = ui.isGenerating
    val itemCount = messages.size + if (hasStreaming) 1 else 0

    LaunchedEffect(itemCount, ui.streamingText.length) {
        if (itemCount > 0) {
            runCatching { listState.animateScrollToItem(itemCount - 1) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(34.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Spacer(Modifier.size(9.dp))
                        Column {
                            Text(
                                conversation?.title ?: "Chat",
                                maxLines = 1,
                            )
                            val active = ui.activeProvider
                                ?: messages.lastOrNull {
                                    it.role == ChatRole.ASSISTANT && !it.isError
                                }?.providerLabel
                            Text(
                                when {
                                    ui.agentMode && active != null -> "Agent mode · $active"
                                    ui.agentMode -> "Agent mode · on-device control"
                                    active != null -> "Active: $active"
                                    else -> "No request sent yet"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ui.agentMode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.newConversation() }) {
                        Icon(Icons.Default.AddComment, contentDescription = "New conversation")
                    }
                    IconButton(onClick = onHistory) {
                        Icon(Icons.Default.History, contentDescription = "Conversation history")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("API keys") },
                                onClick = { menuOpen = false; onKeys() },
                            )
                            DropdownMenuItem(
                                text = { Text("Routing priority") },
                                onClick = { menuOpen = false; onRouting() },
                            )
                            DropdownMenuItem(
                                text = { Text("Voice assistant") },
                                onClick = { menuOpen = false; onVoice() },
                            )
                            DropdownMenuItem(
                                text = { Text("Screen understanding") },
                                onClick = { menuOpen = false; onScreen() },
                            )
                            DropdownMenuItem(
                                text = { Text("API diagnostics") },
                                onClick = { menuOpen = false; onDiagnostics() },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = { menuOpen = false; onSettings() },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                ui = ui,
                voice = voice,
                isBusy = isBusy,
                agentRunning = automation.running,
                onInput = viewModel::updateInput,
                onSend = onSend,
                onStop = {
                    if (automation.running) automationViewModel.stopTask()
                    else viewModel.stopGeneration()
                },
                onAttach = { imagePicker.launch("image/*") },
                onRemoveAttachment = viewModel::removeAttachment,
                onVoice = onVoice,
                onVoiceOrb = onVoiceOrb,
                onToggleAgent = {
                    if (!ui.agentMode) automationViewModel.refresh()
                    viewModel.toggleAgentMode()
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (configs.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "No API keys configured. Add one to send requests.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onKeys) { Text("Add key") }
                    }
                }
            }
            ui.fallbackNotice?.let { notice ->
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Automatic fallback: $notice",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = viewModel::clearErrorAndNotice) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            ui.error?.let { error ->
                ErrorBanner(
                    message = error,
                    onDismiss = viewModel::clearError,
                    actionLabel = "Retry last request",
                    onAction = viewModel::regenerate,
                )
            }
            if (ui.agentMode || automation.running) {
                automation.error?.let { error ->
                    ErrorBanner(
                        message = error,
                        onDismiss = automationViewModel::clearError,
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (messages.isEmpty() && !hasStreaming) {
                    item {
                        EmptyChatState(hasConfigs = configs.isNotEmpty(), onKeys = onKeys)
                    }
                }
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        onCopy = {
                            clipboard.setText(AnnotatedString(message.content))
                        },
                        onRegenerate = if (
                            message.role == ChatRole.ASSISTANT &&
                            message.id == messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.id
                        ) {
                            viewModel::regenerate
                        } else {
                            null
                        },
                    )
                }
                if (hasStreaming) {
                    item(key = "streaming") {
                        StreamingBubble(ui)
                    }
                }
            }

            if (ui.agentMode || automation.running) {
                AgentPanel(
                    ui = ui,
                    automation = automation,
                    connected = connected,
                    currentTask = currentTask,
                    onEnable = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    onStop = automationViewModel::stopTask,
                    onTasks = onTasks,
                    onToggleAudio = { viewModel.setTtsEnabled(!ui.ttsEnabled) },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val provider = ui.activeProvider
                    ?: messages.lastOrNull { it.role == ChatRole.ASSISTANT && !it.isError }?.providerLabel
                val model = ui.activeModel
                    ?: messages.lastOrNull { it.role == ChatRole.ASSISTANT && !it.isError }?.model
                if (provider != null) {
                    StatusPill("$provider · ${model ?: "model"}", SuccessGreen)
                } else {
                    StatusPill("No active provider", MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(
                    onClick = { showModeDialog = true },
                    label = {
                        Text(if (ui.manualMode) "Manual" else "Auto routing")
                    },
                )
                if (ui.sessionLimit > 0) {
                    StatusPill(
                        "${ui.sessionRequests}/${ui.sessionLimit}",
                        if (ui.sessionRequests >= ui.sessionLimit) DangerRed else WarningAmber,
                    )
                }
            }
        }
    }

    if (showModeDialog) {
        RoutingModeDialog(
            ui = ui,
            configs = configs,
            onDismiss = { showModeDialog = false },
            onAuto = {
                viewModel.setAutoMode()
                showModeDialog = false
            },
            onManual = { config ->
                viewModel.setManualMode(config.id, ui.manualAllowFallback)
                showModeDialog = false
            },
            onAllowFallback = viewModel::setManualAllowFallback,
        )
    }

    if (ui.awaitingCostWarning) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCostWarning,
            title = { Text("Send request?") },
            text = {
                Text(
                    "This request may use your provider quota and could incur charges. " +
                        "You can disable this warning in Settings.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmSendAfterWarning) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCostWarning) { Text("Cancel") }
            },
        )
    }

}

@Composable
private fun Composer(
    ui: ChatUiState,
    voice: VoiceManagerState,
    isBusy: Boolean,
    agentRunning: Boolean,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onVoice: () -> Unit,
    onVoiceOrb: () -> Unit,
    onToggleAgent: () -> Unit,
) {
    Surface(
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (ui.attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.attachments.forEach { path ->
                        AttachmentPreview(path = path, onRemove = { onRemoveAttachment(path) })
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = ui.agentMode,
                    onClick = onToggleAgent,
                    label = { Text(if (ui.agentMode) "Agent mode on" else "Agent mode") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
                if (ui.agentMode) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Controls your phone",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (voice.listening) {
                    Text(
                        "Listening…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else if (voice.speaking) {
                    Text(
                        "Speaking…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(onClick = onVoice) { Text("Voice") }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAttach) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach image")
                }
                OutlinedTextField(
                    value = ui.input,
                    onValueChange = onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (ui.agentMode) {
                                "Tell the agent what to do on your phone…"
                            } else {
                                "Message the assistant…"
                            },
                        )
                    },
                    maxLines = 6,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions(),
                )
                Spacer(Modifier.size(6.dp))
                VoiceOrb(
                    active = voice.listening,
                    speaking = voice.speaking,
                    amplitude = voice.amplitude,
                    modifier = Modifier.size(48.dp),
                    iconSize = 20.dp,
                    onClick = onVoiceOrb,
                )
                Spacer(Modifier.size(6.dp))
                if (isBusy) {
                    FilledIconButton(onClick = onStop) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = if (agentRunning) "Stop agent" else "Stop generation",
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = ui.input.isNotBlank() || ui.attachments.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
            if (voice.listening && voice.partialTranscript.isNotBlank()) {
                Text(
                    voice.partialTranscript,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 52.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AgentPanel(
    ui: ChatUiState,
    automation: AutomationUiState,
    connected: Boolean,
    currentTask: TaskRun?,
    onEnable: () -> Unit,
    onStop: () -> Unit,
    onTasks: () -> Unit,
    onToggleAudio: () -> Unit,
) {
    val running = automation.running
    val container = when {
        running -> MaterialTheme.colorScheme.primaryContainer
        !connected -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Agent mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when {
                            running -> automation.status ?: "Working…"
                            !connected -> "Accessibility is off"
                            currentTask?.finishedAt != null -> "Ready for another task"
                            else -> "Ready to control your phone"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                StatusPill(
                    if (running) "Running" else if (connected) "Ready" else "Setup",
                    when {
                        running -> WarningAmber
                        connected -> SuccessGreen
                        else -> DangerRed
                    },
                )
            }

            if (!running && !connected) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Enable the accessibility service so the agent can read the screen and tap other " +
                        "apps. It runs only while a task is active and asks before sensitive steps.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onEnable) {
                    Icon(
                        Icons.Default.AccessibilityNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Enable accessibility")
                }
            } else if (running) {
                Spacer(Modifier.height(8.dp))
                val steps = currentTask?.steps.orEmpty().takeLast(3)
                if (steps.isEmpty()) {
                    Text(
                        "Waiting for the first action…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    steps.forEach { step ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (step.status) {
                                    StepStatus.SUCCESS -> "OK"
                                    StepStatus.FAILED -> "ERR"
                                    StepStatus.WAITING -> "WAIT"
                                    else -> "-"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = when (step.status) {
                                    StepStatus.SUCCESS -> SuccessGreen
                                    StepStatus.FAILED -> DangerRed
                                    StepStatus.WAITING -> WarningAmber
                                    else -> MaterialTheme.colorScheme.primary
                                },
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                step.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = onTasks,
                        label = { Text("Task log") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                    AssistChip(
                        onClick = onToggleAudio,
                        label = { Text(if (ui.ttsEnabled) "Sound" else "Muted") },
                        leadingIcon = {
                            Icon(
                                if (ui.ttsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                    FilledTonalButton(onClick = onStop) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("Cancel")
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                currentTask?.report?.takeIf { it.isNotBlank() }?.let { report ->
                    Text(
                        report,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = onTasks,
                        label = { Text("Task log") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                    AssistChip(
                        onClick = onToggleAudio,
                        label = { Text(if (ui.ttsEnabled) "Sound" else "Muted") },
                        leadingIcon = {
                            Icon(
                                if (ui.ttsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Send an instruction below. The agent keeps working until you cancel.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AttachmentPreview(path: String, onRemove: () -> Unit) {
    val bitmap = remember(path) {
        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }
    Box {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Attached image",
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        } else {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove attachment", modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
    onRegenerate: (() -> Unit)?,
) {
    val isUser = message.role == ChatRole.USER
    Box(Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
            color = when {
                message.isError -> MaterialTheme.colorScheme.errorContainer
                isUser -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = 360.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (!isUser && !message.isError) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(
                            listOfNotNull(message.providerLabel, message.model).joinToString(" · ")
                                .ifBlank { "Assistant" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (message.isError) {
                    Text("Provider error", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                }
                MarkdownText(message.content)
                if (message.attachmentPaths.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        message.attachmentPaths.forEach { path ->
                            val bitmap = remember(path) {
                                runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Attached image",
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                            }
                        }
                    }
                }
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (onRegenerate != null) {
                            IconButton(onClick = onRegenerate, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Regenerate",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        IconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy response",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(ui: ChatUiState) {
    Box(Modifier.fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (ui.streamingText.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            ui.status ?: "Waiting for ${if (ui.manualMode) "provider" else "the next provider"}…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    MarkdownText(ui.streamingText)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun EmptyChatState(hasConfigs: Boolean, onKeys: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text("Start a conversation", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasConfigs) {
                "Ask anything, attach an image, or switch to Agent mode below to let the assistant " +
                    "tap, type, and scroll through other apps. You can cancel at any time."
            } else {
                "Add at least one provider API key to send requests."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasConfigs) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onKeys) { Text("Add API key") }
        }
    }
}

@Composable
private fun RoutingModeDialog(
    ui: ChatUiState,
    configs: List<ApiConfig>,
    onDismiss: () -> Unit,
    onAuto: () -> Unit,
    onManual: (ApiConfig) -> Unit,
    onAllowFallback: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Routing mode") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Automatic mode follows your per-task fallback order. Manual mode pins one " +
                        "configuration unless you allow fallback.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onAuto),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = !ui.manualMode, onClick = onAuto)
                    Text("Automatic routing")
                }
                Text("Manual", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                if (configs.isEmpty()) {
                    Text(
                        "No provider configurations yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                configs.forEach { config ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onManual(config) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = ui.manualMode && ui.manualConfigId == config.id,
                            onClick = { onManual(config) },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(config.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${config.provider.displayName} · ${config.model.ifBlank { "no model" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!config.enabled) {
                            StatusPill("disabled", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow fallback in manual mode", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "If the selected provider fails, continue through the fallback order.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = ui.manualAllowFallback, onCheckedChange = onAllowFallback)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
