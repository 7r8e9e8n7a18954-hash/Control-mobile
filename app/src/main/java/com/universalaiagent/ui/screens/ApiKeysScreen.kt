package com.universalaiagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.ui.KeysViewModel
import com.universalaiagent.ui.components.ConfirmDialog
import com.universalaiagent.ui.components.ErrorBanner
import com.universalaiagent.ui.components.InfoCard
import com.universalaiagent.ui.components.ScreenScaffold
import com.universalaiagent.ui.components.StatusPill
import com.universalaiagent.ui.components.formatTimestamp
import com.universalaiagent.ui.theme.DangerRed
import com.universalaiagent.ui.theme.SuccessGreen
import com.universalaiagent.ui.theme.WarningAmber

@Composable
fun ApiKeysScreen(
    viewModel: KeysViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onModels: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val busyId by viewModel.busyId.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<ApiConfig?>(null) }

    LaunchedEffect(status) {
        if (status != null) {
            kotlinx.coroutines.delay(6000)
            viewModel.clearStatus()
        }
    }

    ScreenScaffold(
        title = "API key manager",
        subtitle = "${configs.size} configuration(s) · keys encrypted on device",
        onBack = onBack,
        actions = {
            TextButton(onClick = onModels) { Text("Models") }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add provider") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoCard(
                    title = "Keys never appear in full",
                    body = "Keys are encrypted with an Android Keystore-backed AES-GCM key and stored " +
                        "in private app storage. They are masked in lists and never written to chat, logs, " +
                        "exports, or backups. A rooted or compromised device can still expose client-side keys.",
                    icon = Icons.Default.VpnKey,
                )
            }
            status?.let { message ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (configs.isEmpty()) {
                item {
                    Card {
                        Column(Modifier.padding(20.dp)) {
                            Text("No API configurations", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Add an OpenAI, Gemini, Claude, Groq, OpenRouter, or custom " +
                                    "OpenAI-compatible endpoint. You can add multiple keys per provider.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = onAdd) { Text("Add your first provider") }
                        }
                    }
                }
            }
            items(configs, key = { it.id }) { config ->
                ConfigCard(
                    config = config,
                    busy = busyId == config.id,
                    onToggle = { viewModel.setEnabled(config.id, it) },
                    onTest = { viewModel.test(config.id) },
                    onEdit = { onEdit(config.id) },
                    onModels = onModels,
                    onDelete = { deleteTarget = config },
                )
            }
            item {
                TextButton(onClick = onDiagnostics) { Text("Open API diagnostics and usage") }
            }
        }
    }

    deleteTarget?.let { config ->
        ConfirmDialog(
            title = "Delete ${config.label}?",
            message = "This removes the configuration and its encrypted API key from this device. " +
                "The provider account and billing are not affected.",
            confirmLabel = "Delete key",
            destructive = true,
            onConfirm = {
                viewModel.delete(config.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun ConfigCard(
    config: ApiConfig,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onModels: () -> Unit,
    onDelete: () -> Unit,
) {
    val now = System.currentTimeMillis()
    Card {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        config.nickname.ifBlank { config.provider.displayName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        config.provider.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = config.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    !config.enabled -> StatusPill("Disabled", MaterialTheme.colorScheme.onSurfaceVariant)
                    config.cooldownUntil > now -> StatusPill(
                        "Cooling down ${((config.cooldownUntil - now) / 1000)}s",
                        WarningAmber,
                    )
                    !config.available -> StatusPill("Unavailable", DangerRed)
                    config.keyLast4.isBlank() -> StatusPill("Key missing", WarningAmber)
                    else -> StatusPill("Ready", SuccessGreen)
                }
                if (config.supportsVision) StatusPill("Vision", MaterialTheme.colorScheme.primary)
                StatusPill("Priority ${config.priority}", MaterialTheme.colorScheme.secondary)
                if (config.lastError != null) StatusPill("Last request failed", DangerRed)
            }
            Spacer(Modifier.height(10.dp))
            KeyValue("Model", config.model.ifBlank { "Not selected" })
            KeyValue("API key", config.maskedKey())
            KeyValue("Last success", formatTimestamp(config.lastSuccessAt))
            KeyValue(
                "Usage",
                "${config.requestCount} requests · ${config.inputTokens} in / ${config.outputTokens} out tokens",
            )
            if (config.rateLimitInfo != null) {
                KeyValue("Rate limit", config.rateLimitInfo)
            }
            config.lastError?.let { error ->
                Spacer(Modifier.height(8.dp))
                ErrorBanner(message = error)
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(onClick = onTest, enabled = !busy) {
                    if (busy) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(6.dp))
                    }
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Test")
                }
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Edit")
                }
                IconButton(onClick = onModels) {
                    Icon(Icons.Default.VpnKey, contentDescription = "Browse models")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete configuration",
                        tint = DangerRed,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            modifier = Modifier.weight(0.32f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(0.68f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
