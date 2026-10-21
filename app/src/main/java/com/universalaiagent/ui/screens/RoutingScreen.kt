package com.universalaiagent.ui.screens

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalaiagent.data.model.ApiConfig
import com.universalaiagent.data.model.TaskType
import com.universalaiagent.ui.RoutingViewModel
import com.universalaiagent.ui.components.InfoCard
import com.universalaiagent.ui.components.NumberedBadge
import com.universalaiagent.ui.components.ScreenScaffold
import com.universalaiagent.ui.components.SectionTitle
import com.universalaiagent.ui.components.StatusPill
import com.universalaiagent.ui.theme.DangerRed
import com.universalaiagent.ui.theme.SuccessGreen
import com.universalaiagent.ui.theme.WarningAmber
import kotlin.math.abs

@Composable
fun RoutingScreen(
    viewModel: RoutingViewModel,
    onBack: () -> Unit,
    onAddProvider: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val threshold = 56.dp

    ScreenScaffold(
        title = "Routing priority",
        subtitle = "Fallback order is configured per task",
        onBack = onBack,
        actions = {
            TextButton(onClick = viewModel::resetToPriority) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Reset")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TaskType.entries) { task ->
                        FilterChip(
                            selected = state.task == task,
                            onClick = { viewModel.selectTask(task) },
                            label = { Text(task.label) },
                        )
                    }
                }
            }
            item {
                InfoCard(
                    title = state.task.label,
                    body = state.task.description + ". Only enabled configurations that support the " +
                        "required capability are eligible. Cooling-down keys are skipped automatically. " +
                        "Long-press a drag handle or use the arrows to reorder.",
                )
            }
            if (state.task.requiresVision) {
                item {
                    InfoCard(
                        title = "Vision capability required",
                        body = "Configurations without image input are ignored for this task even if " +
                            "they appear in the fallback list.",
                        icon = Icons.Default.WarningAmber,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    )
                }
            }
            if (state.task == TaskType.AUDIO_INPUT || state.task == TaskType.AUDIO_OUTPUT ||
                state.task == TaskType.TRANSCRIPTION
            ) {
                item {
                    InfoCard(
                        title = "Audio routes are limited",
                        body = "The app uses Android speech recognition and text-to-speech as its baseline " +
                            "audio pipeline. No configured provider adapter currently accepts raw audio, so " +
                            "these task routes are not used by the voice screen.",
                        icon = Icons.Default.WarningAmber,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    )
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Fallback order", Modifier.weight(1f))
                    Text(
                        "${state.included.size} active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.included.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("No eligible configurations")
                            Text(
                                "Add an API provider, enable it, and include it below.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = onAddProvider) { Text("Add provider") }
                        }
                    }
                }
            }

            items(state.included, key = { it.id }) { config ->
                val index = state.included.indexOfFirst { it.id == config.id }
                val dragging = draggingId == config.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NumberedBadge(index + 1)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                config.nickname.ifBlank { config.provider.displayName },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${config.provider.displayName} · ${config.model.ifBlank { "no model" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                when {
                                    !config.available -> StatusPill("unavailable", DangerRed)
                                    config.isCoolingDown() -> StatusPill("cooling down", WarningAmber)
                                    config.supportsVision -> StatusPill("vision", SuccessGreen)
                                    else -> StatusPill("ready", SuccessGreen)
                                }
                            }
                        }
                        IconButton(
                            onClick = { viewModel.move(config.id, -1) },
                            enabled = index > 0,
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { viewModel.move(config.id, 1) },
                            enabled = index < state.included.lastIndex,
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                        }
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            modifier = Modifier
                                .size(28.dp)
                                .pointerInput(config.id) {
                                    var rowOffset = 0f
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingId = config.id
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            rowOffset += amount.y
                                            val thresholdPx = threshold.toPx()
                                            if (abs(dragOffset) >= thresholdPx) {
                                                if (dragOffset > 0) {
                                                    viewModel.move(config.id, 1)
                                                } else {
                                                    viewModel.move(config.id, -1)
                                                }
                                                dragOffset = 0f
                                                rowOffset = 0f
                                            }
                                        },
                                    )
                                },
                        )
                        Switch(
                            checked = true,
                            onCheckedChange = { viewModel.setIncluded(config.id, it) },
                        )
                    }
                }
            }

            if (state.excluded.isNotEmpty()) {
                item {
                    SectionTitle("Excluded", Modifier.padding(top = 10.dp))
                }
                items(state.excluded, key = { "excluded-${it.id}" }) { config ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    config.nickname.ifBlank { config.provider.displayName },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    "${config.provider.displayName} · ${config.model.ifBlank { "no model" }}" +
                                        if (!config.enabled) " · globally disabled" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = false, onCheckedChange = { viewModel.setIncluded(config.id, it) })
                        }
                    }
                }
            }

            if (state.allEmpty) {
                item {
                    OutlinedButton(onClick = onAddProvider, modifier = Modifier.fillMaxWidth()) {
                        Text("Add an API provider")
                    }
                }
            }
        }
    }
}
