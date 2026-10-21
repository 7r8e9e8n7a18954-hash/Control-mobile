package com.universalaiagent.ui.screens

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VoiceChat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.ChatMessage
import com.universalaiagent.data.model.ChatRole
import com.universalaiagent.ui.ChatUiState
import com.universalaiagent.ui.ChatViewModel
import com.universalaiagent.ui.components.ConfirmDialog
import com.universalaiagent.ui.components.ErrorBanner
import com.universalaiagent.ui.components.MarkdownText
import com.universalaiagent.ui.components.StatusPill
import com.universalaiagent.ui.theme.DangerRed
import com.universalaiagent.ui.theme.SuccessGreen
import com.universalaiagent.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onHistory: () -> Unit,
    onKeys: () -> Unit,
    onSettings: () -> Unit,
    onVoice: () -> Unit,
    onRouting: () -> Unit,
    onDiagnostics: () -> Unit,
    onScreen: () -> Unit,
    onAutomation: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val conversation by viewModel.selectedConversation.collectAsStateWithLifecycle()
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.attachImage(uri)
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
                    Column {
                        Text(
                            conversation?.title ?: "Chat",
                            maxLines = 1,
                        )
                        val active = ui.activeProvider
                            ?: messages.lastOrNull { it.role == ChatRole.ASSISTANT && !it.isError }?.providerLabel
                        Text(
                            active?.let { "Active: $it" } ?: "No request sent yet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
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
                                text = { Text("Android automation") },
                                onClick = { menuOpen = false; onAutomation() },
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
                onInput = viewModel::updateInput,
                onSend = viewModel::send,
                onStop = viewModel::stopGeneration,
                onAttach = { imagePicker.launch("image/*") },
                onRemoveAttachment = viewModel::removeAttachment,
                onVoice = onVoice,
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
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onVoice: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
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
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onAttach) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Attach image")
                }
                IconButton(onClick = onVoice) {
                    Icon(Icons.Default.VoiceChat, contentDescription = "Voice assistant")
                }
                OutlinedTextField(
                    value = ui.input,
                    onValueChange = onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message the assistant…") },
                    maxLines = 6,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions(),
                )
                Spacer(Modifier.size(6.dp))
                if (ui.isGenerating) {
                    FilledIconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop generation")
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
                "Ask anything, attach an image, or switch between automatic routing and a manual model. " +
                    "Fallback status will appear above the composer."
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
