package com.universalaiagent.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.TaskType
import com.universalaiagent.ui.DiagnosticsViewModel
import com.universalaiagent.ui.components.ConfirmDialog
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
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onBack: () -> Unit,
    onKeys: () -> Unit,
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busyId by viewModel.busyId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClearUsage by remember { mutableStateOf(false) }

    if (confirmClearUsage) {
        ConfirmDialog(
            title = "Clear usage records?",
            message = "Request metadata and provider-reported costs will be deleted from this device.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                viewModel.clearUsage()
                confirmClearUsage = false
            },
            onDismiss = { confirmClearUsage = false },
        )
    }

    val successes = records.count { it.success }
    val failures = records.size - successes
    val reportedCost = records.sumOf { it.reportedCost ?: 0.0 }
    val inputTokens = records.sumOf { it.inputTokens ?: 0L }
    val outputTokens = records.sumOf { it.outputTokens ?: 0L }

    ScreenScaffold(
        title = "Diagnostics",
        subtitle = "Connections, routes, errors, and usage",
        onBack = onBack,
        actions = {
            TextButton(
                onClick = {
                    val report = buildString {
                        appendLine("Universal AI Agent diagnostics")
                        appendLine("Configurations: ${configs.size}")
                        appendLine("Requests: ${records.size}, ok: $successes, failed: $failures")
                        appendLine("Reported input tokens: $inputTokens, output tokens: $outputTokens")
                        appendLine("Reported cost: $reportedCost")
                        appendLine()
                        configs.forEach { config ->
                            appendLine(
                                "${config.label} [${config.provider.name}] model=${config.model} " +
                                    "enabled=${config.enabled} available=${config.available} " +
                                    "lastTest=${formatTimestamp(config.lastTestAt)} " +
                                    "lastError=${config.lastError.orEmpty()}",
                            )
                        }
                        appendLine()
                        TaskType.entries.forEach { task ->
                            val ids = settings.routeFor(task)
                            if (ids.isNotEmpty()) {
                                val labels = ids.mapNotNull { id -> configs.firstOrNull { it.id == id }?.label }
                                appendLine("${task.name}: ${labels.joinToString(" -> ")}")
                            }
                        }
                        appendLine()
                        records.take(50).forEach { record ->
                            appendLine(
                                "${formatTimestamp(record.timestamp)} ${record.provider.name} " +
                                    "${record.model} ok=${record.success} " +
                                    "in=${record.inputTokens} out=${record.outputTokens} " +
                                    "cost=${record.reportedCost} error=${record.errorCategory.orEmpty()}",
                            )
                        }
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("AI agent diagnostics", report))
                },
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text("Copy")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                InfoCard(
                    title = "What this page shows",
                    body = "Local connection test results, cooldowns, last errors, configured fallback order, " +
                        "and metadata for recent requests. API keys are never displayed or copied. Token " +
                        "counts and costs exist only when the provider reports them.",
                    icon = Icons.Default.BugReport,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Requests", records.size.toString(), Modifier.weight(1f))
                    MetricCard("Success", successes.toString(), Modifier.weight(1f), SuccessGreen)
                    MetricCard("Failed", failures.toString(), Modifier.weight(1f), if (failures > 0) DangerRed else SuccessGreen)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Input tokens", inputTokens.toString(), Modifier.weight(1f))
                    MetricCard("Output tokens", outputTokens.toString(), Modifier.weight(1f))
                    MetricCard("$ %.4f".format(reportedCost), "cost", Modifier.weight(1f))
                }
            }
            message?.let {
                item {
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
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                SectionTitle("Configurations")
            }
            if (configs.isEmpty()) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text("No API configuration exists.")
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onKeys) { Text("Configure API keys") }
                        }
                    }
                }
            } else {
                items(configs, key = { it.id }) { config ->
                    ConfigDiagnosticCard(
                        config = config,
                        busy = busyId == config.id,
                        tested = busyId != null,
                        onTest = { viewModel.test(config.id) },
                    )
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                SectionTitle("Configured routing")
                Text(
                    "Override order is stored per task. Empty tasks use global priority.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(TaskType.entries.toList(), key = { "route-${it.name}" }) { task ->
                val ids = settings.routeFor(task)
                if (ids.isNotEmpty()) {
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text(task.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            val labels = ids.mapNotNull { id -> configs.firstOrNull { it.id == id }?.label }
                            Text(
                                labels.ifEmpty { ids }.joinToString(" → "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Recent requests", Modifier.weight(1f))
                    TextButton(onClick = { confirmClearUsage = true }, enabled = records.isNotEmpty()) {
                        Text("Clear")
                    }
                }
            }
            if (records.isEmpty()) {
                item {
                    EmptyState(
                        title = "No requests yet",
                        body = "Send a chat or run a test to populate usage diagnostics.",
                        modifier = Modifier.height(180.dp),
                    )
                }
            } else {
                items(records.take(60), key = { it.id }) { record ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${record.provider.displayName} · ${record.model}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                )
                                StatusPill(
                                    if (record.success) "OK" else "Failed",
                                    if (record.success) SuccessGreen else DangerRed,
                                )
                            }
                            Text(
                                "${record.configLabel} · ${formatTimestamp(record.timestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val tokens = buildString {
                                record.inputTokens?.let { append("in $it ") }
                                record.outputTokens?.let { append("out $it ") }
                                record.reportedCost?.let { append("cost $${"%.4f".format(it)}") }
                            }
                            if (tokens.isNotBlank()) {
                                Text(tokens, style = MaterialTheme.typography.labelSmall)
                            }
                            record.errorCategory?.let { category ->
                                Text(
                                    "$category: ${record.errorMessage.orEmpty()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarningAmber,
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfigDiagnosticCard(
    config: ApiConfig,
    busy: Boolean,
    tested: Boolean,
    onTest: () -> Unit,
) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        config.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${config.provider.displayName} · ${config.model.ifBlank { "no model" }} · ${config.maskedKey()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    config.isCoolingDown() -> StatusPill("cooling down", WarningAmber)
                    !config.enabled -> StatusPill("disabled", MaterialTheme.colorScheme.onSurfaceVariant)
                    config.available -> StatusPill("available", SuccessGreen)
                    else -> StatusPill("unavailable", DangerRed)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Last test: ${formatTimestamp(config.lastTestAt)} · " +
                    "Last success: ${formatTimestamp(config.lastSuccessAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Requests: ${config.requestCount} · Errors: ${config.errorCount}" +
                    (config.reportedCost?.let { " · Cost: $${"%.4f".format(it)}" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            config.rateLimitInfo?.let {
                Text("Rate limit: $it", style = MaterialTheme.typography.labelSmall, color = WarningAmber)
            }
            config.lastError?.let {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(
                    "Last error: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = DangerRed,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onTest, enabled = !busy && !tested) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(6.dp))
                    Text("Testing…")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Test connection")
                }
            }
        }
    }
}
