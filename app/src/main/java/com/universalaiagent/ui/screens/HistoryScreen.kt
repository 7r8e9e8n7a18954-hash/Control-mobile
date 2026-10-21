package com.universalaiagent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalaiagent.data.model.Conversation
import com.universalaiagent.ui.ChatViewModel
import com.universalaiagent.ui.DiagnosticsViewModel
import com.universalaiagent.ui.components.ConfirmDialog
import com.universalaiagent.ui.components.EmptyState
import com.universalaiagent.ui.components.ScreenScaffold
import com.universalaiagent.ui.components.SectionTitle
import com.universalaiagent.ui.components.StatusPill
import com.universalaiagent.ui.components.formatTimestamp
import com.universalaiagent.ui.theme.DangerRed
import com.universalaiagent.ui.theme.SuccessGreen
import com.universalaiagent.ui.theme.WarningAmber

@Composable
fun HistoryScreen(
    chatViewModel: ChatViewModel,
    diagnosticsViewModel: DiagnosticsViewModel,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    val conversations by chatViewModel.conversations.collectAsStateWithLifecycle()
    val usage by diagnosticsViewModel.records.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }
    var confirmClearUsage by remember { mutableStateOf(false) }

    val filteredChats = conversations.filter { conversation ->
        query.isBlank() ||
            conversation.title.contains(query, true) ||
            conversation.messages.any { it.content.contains(query, true) }
    }
    val filteredUsage = usage.filter { record ->
        query.isBlank() ||
            record.configLabel.contains(query, true) ||
            record.model.contains(query, true) ||
            record.provider.displayName.contains(query, true)
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename conversation") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Title") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val title = renameText.trim()
                        if (title.isNotBlank()) chatViewModel.renameConversation(target.id, title)
                        renameTarget = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete conversation?",
            message = "“${target.title}” and its image attachments will be removed from this device.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                chatViewModel.deleteConversation(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
    if (confirmClearUsage) {
        ConfirmDialog(
            title = "Clear usage records?",
            message = "This removes request metadata, token counts, and recorded costs from this device.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                diagnosticsViewModel.clearUsage()
                confirmClearUsage = false
            },
            onDismiss = { confirmClearUsage = false },
        )
    }

    ScreenScaffold(
        title = "History",
        subtitle = "Conversations and request usage stay on this device",
        onBack = onBack,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search history") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Conversations", Modifier.weight(1f))
                    Text(
                        "${filteredChats.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (filteredChats.isEmpty()) {
                item {
                    Text(
                        if (conversations.isEmpty()) "No saved conversations yet."
                        else "No conversations match your search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(filteredChats, key = { it.id }) { conversation ->
                    ConversationCard(
                        conversation = conversation,
                        onClick = {
                            chatViewModel.selectConversation(conversation.id)
                            onOpenConversation(conversation.id)
                        },
                        onRename = {
                            renameTarget = conversation
                            renameText = conversation.title
                        },
                        onDelete = { deleteTarget = conversation },
                    )
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Request usage", Modifier.weight(1f))
                    TextButton(onClick = { confirmClearUsage = true }, enabled = usage.isNotEmpty()) {
                        Text("Clear")
                    }
                }
                Text(
                    "Metadata only: provider, model, tokens reported by the provider, and any provider-reported cost. " +
                        "Prompts and responses are not copied here.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (filteredUsage.isEmpty()) {
                item {
                    Text(
                        if (usage.isEmpty()) "No requests recorded yet."
                        else "No usage records match your search.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(filteredUsage.take(100), key = { it.id }) { record ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Toll, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    record.configLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                StatusPill(
                                    if (record.success) "OK" else "Failed",
                                    if (record.success) SuccessGreen else DangerRed,
                                )
                            }
                            Text(
                                "${record.provider.displayName} · ${record.model}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                buildString {
                                    append(formatTimestamp(record.timestamp))
                                    record.inputTokens?.let { append(" · in $it") }
                                    record.outputTokens?.let { append(" · out $it") }
                                    record.reportedCost?.let { append(" · $${"%.4f".format(it)}") }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            record.errorCategory?.let {
                                Text(
                                    "$it: ${record.errorMessage.orEmpty()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarningAmber,
                                )
                            }
                        }
                    }
                }
            }
            if (filteredUsage.size > 100) {
                item {
                    Text(
                        "Showing the newest 100 matching records.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationCard(
    conversation: Conversation,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Chat, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    conversation.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    conversation.messages.lastOrNull()?.content?.replace('\n', ' ')?.take(100)
                        ?: "No messages",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    "${conversation.messages.size} messages · ${formatTimestamp(conversation.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed)
            }
        }
    }
}
