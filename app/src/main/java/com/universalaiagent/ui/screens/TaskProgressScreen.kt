package com.universalaiagent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalaiagent.automation.StepStatus
import com.universalaiagent.automation.TaskRun
import com.universalaiagent.automation.TaskStep
import com.universalaiagent.ui.AutomationViewModel
import com.universalaiagent.ui.components.EmptyState
import com.universalaiagent.ui.components.InfoCard
import com.universalaiagent.ui.components.ScreenScaffold
import com.universalaiagent.ui.components.SectionTitle
import com.universalaiagent.ui.components.StatusPill
import com.universalaiagent.ui.components.formatTimestamp
import com.universalaiagent.ui.theme.DangerRed
import com.universalaiagent.ui.theme.SuccessGreen
import com.universalaiagent.ui.theme.WarningAmber

@Composable
fun TaskProgressScreen(
    viewModel: AutomationViewModel,
    onBack: () -> Unit,
) {
    val current by viewModel.currentTask.collectAsStateWithLifecycle()
    val history by viewModel.taskHistory.collectAsStateWithLifecycle()
    val pending by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    pending?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { viewModel.respondConfirmation(false) },
            title = { Text("Confirm action") },
            text = { Text(confirmation.reason) },
            confirmButton = {
                TextButton(onClick = { viewModel.respondConfirmation(true) }) { Text("Approve") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.respondConfirmation(false) }) { Text("Decline") }
            },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear task history?") },
            text = { Text("This removes the current run and all saved task reports from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clearTaskHistory()
                    },
                ) { Text("Clear", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }

    ScreenScaffold(
        title = "Task progress",
        subtitle = "Step-by-step execution log",
        onBack = onBack,
        actions = {
            if (current != null || history.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("Clear")
                }
            }
        },
    ) { padding ->
        val run = current
        if (run == null) {
            EmptyState(
                title = "No task yet",
                body = "Start a task from Automation. Every step, confirmation and result is logged here " +
                    "and kept in local history.",
                modifier = Modifier.padding(padding),
            )
            return@ScreenScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (run.success) {
                        true -> MaterialTheme.colorScheme.secondaryContainer
                        false -> MaterialTheme.colorScheme.errorContainer
                        null -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Instruction", style = MaterialTheme.typography.labelSmall)
                            Text(
                                run.instruction,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (run.running) {
                            StatusPill("Running", WarningAmber)
                        } else {
                            StatusPill(
                                when (run.success) {
                                    true -> "Completed"
                                    false -> "Not completed"
                                    null -> "Stopped"
                                },
                                when (run.success) {
                                    true -> SuccessGreen
                                    false -> DangerRed
                                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val finished = run.steps.count { it.status == StepStatus.SUCCESS || it.status == StepStatus.SKIPPED }
                    val fraction = if (run.steps.isEmpty()) 0f else finished.toFloat() / run.steps.size
                    if (run.running) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${run.steps.size} steps · started ${formatTimestamp(run.startedAt)}" +
                            (run.finishedAt?.let { " · ended ${formatTimestamp(it)}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (run.running) {
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = viewModel::stopTask) {
                            Text("Stop task")
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            if (run.steps.isEmpty()) {
                Text(
                    "Waiting for the first planned action…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                run.steps.forEachIndexed { index, step ->
                    StepRow(index + 1, step, isLast = index == run.steps.lastIndex)
                }
            }

            run.report?.let { report ->
                Spacer(Modifier.height(14.dp))
                InfoCard(
                    title = "Result report",
                    body = report,
                    icon = when (run.success) {
                        true -> Icons.Default.CheckCircle
                        false -> Icons.Default.Cancel
                        null -> Icons.Default.CheckCircle
                    },
                )
            }

            val older = history.filterNot { it.id == run.id }
            if (older.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionTitle("Earlier runs")
                older.take(10).forEach { past ->
                    HistoryCard(past)
                }
            }

            Spacer(Modifier.height(14.dp))
            InfoCard(
                title = "Model-reported completion is not proof",
                body = "The report reflects what the planning model observed and what the accessibility " +
                    "service could execute. Always verify important results yourself on the device.",
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StepRow(number: Int, step: TaskStep, isLast: Boolean) {
    Row {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        when (step.status) {
                            StepStatus.SUCCESS -> SuccessGreen.copy(alpha = 0.16f)
                            StepStatus.FAILED -> DangerRed.copy(alpha = 0.16f)
                            StepStatus.WAITING -> WarningAmber.copy(alpha = 0.16f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when (step.status) {
                    StepStatus.RUNNING -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    StepStatus.SUCCESS -> Text(number.toString(), style = MaterialTheme.typography.labelSmall)
                    StepStatus.FAILED -> Text(number.toString(), style = MaterialTheme.typography.labelSmall, color = DangerRed)
                    StepStatus.WAITING -> Text(number.toString(), style = MaterialTheme.typography.labelSmall, color = WarningAmber)
                    StepStatus.SKIPPED -> Text(number.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (!isLast) {
                Box(
                    Modifier
                        .size(width = 2.dp, height = 46.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    step.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StepIcon(step.status)
            }
            if (step.detail.isNotBlank()) {
                Text(
                    step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                formatTimestamp(step.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepIcon(status: StepStatus) {
    val color: Color = when (status) {
        StepStatus.SUCCESS -> SuccessGreen
        StepStatus.FAILED -> DangerRed
        StepStatus.WAITING -> WarningAmber
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = when (status) {
        StepStatus.SUCCESS -> Icons.Default.CheckCircle
        StepStatus.FAILED -> Icons.Default.Cancel
        StepStatus.WAITING -> Icons.Default.HourglassEmpty
        StepStatus.SKIPPED -> Icons.Default.SkipNext
        StepStatus.RUNNING -> null
    }
    if (icon != null) {
        androidx.compose.material3.Icon(icon, contentDescription = status.name, tint = color, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun HistoryCard(run: TaskRun) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Icon(
                when (run.success) {
                    true -> Icons.Default.CheckCircle
                    false -> Icons.Default.Cancel
                    null -> Icons.Default.HourglassEmpty
                },
                contentDescription = null,
                tint = when (run.success) {
                    true -> SuccessGreen
                    false -> DangerRed
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(run.instruction, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Text(
                    "${formatTimestamp(run.finishedAt ?: run.startedAt)} · ${run.steps.size} steps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
