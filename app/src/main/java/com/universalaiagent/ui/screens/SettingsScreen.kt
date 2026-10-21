package com.universalaiagent.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalaiagent.data.local.ThemeMode
import com.universalaiagent.ui.SettingsViewModel
import com.universalaiagent.ui.components.ConfirmDialog
import com.universalaiagent.ui.components.InfoCard
import com.universalaiagent.ui.components.ScreenScaffold
import com.universalaiagent.ui.components.SectionTitle
import com.universalaiagent.ui.theme.DangerRed

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onKeys: () -> Unit,
    onRouting: () -> Unit,
    onPrivacy: () -> Unit,
    onDiagnostics: () -> Unit,
    onHistory: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var confirmDeleteKeys by remember { mutableStateOf(false) }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import configuration") },
            text = {
                Column {
                    Text(
                        "Paste an export created by this app. API keys are intentionally not included and " +
                            "must be entered again.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Exported JSON") },
                        minLines = 4,
                        maxLines = 8,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.importConfig(importText)
                        showImport = false
                        importText = ""
                    },
                    enabled = importText.isNotBlank(),
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) { Text("Cancel") }
            },
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Reset all settings?",
            message = "Theme, routing, safety preferences, and onboarding state return to defaults. " +
                "API keys, conversations, and attachments are not deleted by this action.",
            confirmLabel = "Reset settings",
            destructive = true,
            onConfirm = {
                viewModel.resetSettings()
                confirmReset = false
            },
            onDismiss = { confirmReset = false },
        )
    }
    if (confirmClearHistory) {
        ConfirmDialog(
            title = "Delete all local history?",
            message = "All conversations, image attachments, and usage records will be deleted. " +
                "API configurations and keys are kept.",
            confirmLabel = "Delete history",
            destructive = true,
            onConfirm = {
                viewModel.clearHistory()
                confirmClearHistory = false
            },
            onDismiss = { confirmClearHistory = false },
        )
    }
    if (confirmDeleteKeys) {
        ConfirmDialog(
            title = "Delete every API configuration?",
            message = "All stored configurations and encrypted keys will be removed. Conversations and " +
                "other settings are kept.",
            confirmLabel = "Delete keys",
            destructive = true,
            onConfirm = {
                viewModel.deleteAllKeys()
                confirmDeleteKeys = false
            },
            onDismiss = { confirmDeleteKeys = false },
        )
    }

    ScreenScaffold(
        title = "Settings",
        subtitle = "Appearance, privacy, limits, and data",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            message?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            SectionTitle("Appearance")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setTheme(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingSwitch(
                title = "Dynamic color",
                body = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    "Use wallpaper-derived colors on Android 12 and newer."
                } else {
                    "Requires Android 12 or newer; ignored on this device."
                },
                checked = settings.dynamicColor,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                onCheckedChange = viewModel::setDynamicColor,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionTitle("Privacy and images")
            SettingSwitch(
                title = "Save conversation history",
                body = "When off, new messages remain in memory for this session but are not written to " +
                    "conversations.json. Existing history is not deleted automatically.",
                checked = settings.historyEnabled,
                onCheckedChange = viewModel::setHistoryEnabled,
            )
            SettingSwitch(
                title = "Allow cloud image processing",
                body = "When off, screen captures and chat images are never sent to a provider. Chat image " +
                    "sending will honestly report that vision processing is disabled.",
                checked = settings.cloudImageProcessing,
                onCheckedChange = viewModel::setCloudImages,
            )
            SettingSwitch(
                title = "Confirm sensitive automation",
                body = "Ask before sending messages, publishing, purchasing, deleting data, changing " +
                    "security settings, or granting permissions. Turning this off does not bypass Android " +
                    "security dialogs or make those actions possible on protected screens.",
                checked = settings.confirmSensitiveActions,
                onCheckedChange = viewModel::setConfirmSensitive,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionTitle("Voice")
            SettingSwitch(
                title = "Speak assistant replies",
                body = "Use Android text-to-speech on the Voice screen. Speech stops before listening.",
                checked = settings.ttsEnabled,
                onCheckedChange = viewModel::setTtsEnabled,
            )
            Text(
                "Speech rate: ${"%.1f".format(settings.ttsRate)}×",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = settings.ttsRate,
                onValueChange = viewModel::setTtsRate,
                valueRange = 0.5f..2.0f,
                steps = 14,
                enabled = settings.ttsEnabled,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionTitle("Request limits")
            OutlinedTextField(
                value = settings.sessionRequestLimit.toString(),
                onValueChange = { value ->
                    value.toIntOrNull()?.let { viewModel.setSessionLimit(it.coerceIn(0, 999)) }
                },
                label = { Text("Requests per chat session") },
                supportingText = { Text("0 means unlimited. Counter resets when the app process restarts.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            SettingSwitch(
                title = "Warn before each request",
                body = "Show an estimated-token warning before every chat request. Token estimates are " +
                    "approximate; provider-reported costs appear in Diagnostics.",
                checked = settings.warnBeforeRequests,
                onCheckedChange = viewModel::setWarnBeforeRequests,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionTitle("Configuration")
            NavigationRow(
                icon = Icons.Default.Key,
                title = "API keys and providers",
                body = "Add, edit, test, enable, or delete configurations.",
                onClick = onKeys,
            )
            NavigationRow(
                icon = Icons.Default.Route,
                title = "Routing priority",
                body = "Choose fallback order per task.",
                onClick = onRouting,
            )
            NavigationRow(
                icon = Icons.Default.BugReport,
                title = "Diagnostics",
                body = "Connection tests, last errors, per-task routes, and usage.",
                onClick = onDiagnostics,
            )
            NavigationRow(
                icon = Icons.Default.PrivacyTip,
                title = "Privacy and security",
                body = "Read exactly what is stored, sent, and not sent.",
                onClick = onPrivacy,
            )
            NavigationRow(
                icon = Icons.Default.History,
                title = "Local history",
                body = "Browse and delete conversations and usage records.",
                onClick = onHistory,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionTitle("Export and import")
            InfoCard(
                title = "Exports never include secrets",
                body = "The export contains non-secret provider settings and app preferences. API keys stay " +
                    "in the encrypted device vault. Anyone with the export still needs their own keys.",
                icon = Icons.Default.FileDownload,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        viewModel.buildExport { json ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Universal AI Agent export", json))
                            viewModel.notifyExported()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Copy export")
                }
                OutlinedButton(
                    onClick = { showImport = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Import")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Copy export places sanitized JSON on the clipboard. The app does not write a world-readable file.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionTitle("Danger zone")
            OutlinedButton(
                onClick = { confirmClearHistory = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete conversations, attachments, and usage")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmDeleteKeys = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete all API configurations and keys")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Reset all settings")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Uninstalling the app removes local history and the Keystore-wrapped key material this app " +
                    "created. Exported files or clipboard contents are outside the app's control.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                body,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun NavigationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = onClick,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    body,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
