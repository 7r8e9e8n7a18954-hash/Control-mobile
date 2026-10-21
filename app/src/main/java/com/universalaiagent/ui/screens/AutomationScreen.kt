package com.universalaiagent.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalaiagent.ui.AutomationViewModel
import com.universalaiagent.ui.components.ErrorBanner
import com.universalaiagent.ui.components.InfoCard
import com.universalaiagent.ui.components.ScreenScaffold
import com.universalaiagent.ui.components.SectionTitle
import com.universalaiagent.ui.components.StatusPill
import com.universalaiagent.ui.components.formatTimestamp
import com.universalaiagent.ui.theme.DangerRed
import com.universalaiagent.ui.theme.SuccessGreen
import com.universalaiagent.ui.theme.WarningAmber

@Composable
fun AutomationScreen(
    viewModel: AutomationViewModel,
    onBack: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val history by viewModel.taskHistory.collectAsStateWithLifecycle()
    val pending by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val context = LocalContext.current

    pending?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { viewModel.respondConfirmation(false) },
            title = { Text("Confirm action") },
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
                        "Review the screen before approving. Leave this dialog unanswered and the task " +
                            "stops after two minutes.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.respondConfirmation(true) }) { Text("Approve") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.respondConfirmation(false) }) { Text("Decline") }
            },
        )
    }

    ScreenScaffold(
        title = "Android automation",
        subtitle = "Accessibility-driven, confirmation-gated",
        onBack = onBack,
        actions = {
            TextButton(onClick = onOpenTasks) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Tasks")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (connected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessibilityNew, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Accessibility service", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        StatusPill(
                            if (connected) "Connected" else "Off",
                            if (connected) SuccessGreen else DangerRed,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (connected) {
                            "The service can read visible screen elements and perform the supported actions. " +
                                "It is used only while a task is running."
                        } else {
                            "Enable Universal AI Agent in Android Settings → Accessibility. The service " +
                                "reads only the currently visible screen and never runs in the background on " +
                                "its own."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            },
                        ) {
                            Text(if (connected) "Accessibility settings" else "Enable service")
                        }
                        OutlinedButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Refresh")
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = ui.instruction,
                onValueChange = viewModel::setInstruction,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What should the assistant do on screen?") },
                placeholder = { Text("Open Settings and turn on Wi-Fi, then stop before connecting to a network") },
                minLines = 3,
                maxLines = 6,
                enabled = !ui.running,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::runTask,
                    enabled = connected && !ui.running && ui.instruction.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Run task")
                }
                OutlinedButton(
                    onClick = viewModel::stopTask,
                    enabled = ui.running,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Stop")
                }
            }
            ui.status?.let {
                Spacer(Modifier.height(10.dp))
                StatusPill(it, if (ui.running) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ui.error?.let {
                Spacer(Modifier.height(10.dp))
                ErrorBanner(message = it, onDismiss = viewModel::clearError)
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Screen snapshot", Modifier.weight(1f))
                OutlinedButton(onClick = viewModel::readScreen, enabled = connected) { Text("Read now") }
            }
            val snapshot = ui.snapshot
            if (snapshot == null) {
                Card {
                    Text(
                        "No snapshot yet. Enable accessibility, open the target app, then read the screen.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${snapshot.appLabel} · ${snapshot.packageName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${snapshot.elements.size} readable elements",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        snapshot.elements.take(14).forEach { element ->
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Column {
                                Text(
                                    element.text.ifBlank { element.description }.ifBlank { element.className.substringAfterLast('.') },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (element.clickable) StatusPill("clickable", MaterialTheme.colorScheme.primary)
                                    if (element.editable) StatusPill("editable", MaterialTheme.colorScheme.tertiary)
                                    if (element.scrollable) StatusPill("scrollable", MaterialTheme.colorScheme.secondary)
                                    if (!element.enabled) StatusPill("disabled", MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (snapshot.elements.size > 14) {
                            Text(
                                "and ${snapshot.elements.size - 14} more elements…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("Launchable apps")
            Text(
                "${ui.apps.size} apps visible to the launcher action",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(ui.apps.take(20), key = { it.packageName }) { app ->
                    StatusPill(app.label, MaterialTheme.colorScheme.secondary)
                }
            }

            if (history.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Recent tasks", Modifier.weight(1f))
                    TextButton(onClick = onOpenTasks) { Text("View all") }
                }
                history.take(3).forEach { run ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                run.instruction,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                            )
                            Text(
                                "${formatTimestamp(run.finishedAt ?: run.startedAt)} · " +
                                    when (run.success) {
                                        true -> "completed"
                                        false -> "not completed"
                                        null -> "stopped"
                                    },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            InfoCard(
                title = "Safety limits are enforced locally",
                body = "Maximum 20 actions and 6 planning rounds per task. The same action three times " +
                    "stops the run. Sensitive or irreversible steps ask for confirmation unless you turn " +
                    "that off in Settings. Accessibility cannot and will not bypass lock screens, " +
                    "biometrics, passwords, or system security dialogs.",
                icon = Icons.Default.WarningAmber,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            InfoCard(
                title = "Readable content is sent for planning",
                body = "Visible text and view metadata are sent to your routed AI provider so it can plan " +
                    "actions. Password fields are not read by the accessibility service. Do not run tasks " +
                    "on screens containing secrets you do not want processed by that provider.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
