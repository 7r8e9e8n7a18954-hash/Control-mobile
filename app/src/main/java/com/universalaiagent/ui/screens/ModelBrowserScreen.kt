package com.universalaiagent.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.universalaiagent.data.model.Capability
import com.universalaiagent.data.model.ModelInfo
import com.universalaiagent.ui.KeysViewModel
import com.universalaiagent.ui.components.EmptyState
import com.universalaiagent.ui.components.InfoCard
import com.universalaiagent.ui.components.ScreenScaffold
import com.universalaiagent.ui.components.StatusPill
import com.universalaiagent.ui.components.formatTimestamp

@Composable
fun ModelBrowserScreen(
    viewModel: KeysViewModel,
    onBack: () -> Unit,
    onAddProvider: () -> Unit,
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val busyId by viewModel.busyId.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(configs) {
        if (selectedId == null || configs.none { it.id == selectedId }) {
            selectedId = configs.firstOrNull()?.id
        }
    }
    val selected = configs.firstOrNull { it.id == selectedId }
    val filtered = selected?.discoveredModels.orEmpty()
        .filter { query.isBlank() || it.id.contains(query, true) || it.displayName.contains(query, true) }

    ScreenScaffold(
        title = "Model browser",
        subtitle = "Discovery is provider-dependent; manual IDs are always allowed",
        onBack = onBack,
        actions = {
            if (selected != null) {
                TextButton(
                    onClick = { viewModel.refreshModels(selected.id) },
                    enabled = busyId == null,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Refresh")
                }
            }
        },
    ) { padding ->
        if (configs.isEmpty()) {
            EmptyState(
                title = "No provider configured",
                body = "Add an API provider before browsing models.",
                modifier = Modifier.padding(padding),
            )
            return@ScreenScaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selected?.label ?: "Select configuration", modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    configs.forEach { config ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(config.nickname.ifBlank { config.provider.displayName })
                                    Text(
                                        config.model.ifBlank { "no model" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                selectedId = config.id
                                menuOpen = false
                            },
                        )
                    }
                }
            }
            if (selected == null) return@Column
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (busyId == selected.id) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("Loading models…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    "Current: ${selected.model.ifBlank { "none" }} · ${selected.maskedKey()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Last refresh: ${formatTimestamp(selected.lastTestAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search models") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
                status?.let {
                    Spacer(Modifier.height(8.dp))
                    InfoCard(title = "Status", body = it)
                }
                selected.lastError?.let {
                    Spacer(Modifier.height(8.dp))
                    InfoCard(title = "Last error", body = it)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (selected.discoveredModels.isEmpty()) {
                Column(Modifier.padding(16.dp)) {
                    InfoCard(
                        title = "No discovered models",
                        body = "This provider may not expose model discovery, or the key has no model " +
                            "access. Tap Refresh, or edit the provider and type a model ID manually.",
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onAddProvider) { Text("Open provider editor") }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { model ->
                        ModelRow(
                            model = model,
                            selected = model.id == selected.model,
                            onClick = { viewModel.selectModel(selected.id, model) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        model.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        model.id,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) StatusPill("Current", MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                model.capabilities.forEach { capability ->
                    StatusPill(
                        capability.name.lowercase().replace('_', ' '),
                        if (capability == Capability.VISION) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append("Context: ${model.contextWindow ?: "unknown"}")
                    model.maxOutputTokens?.let { append(" · Max output: $it") }
                    append(" · ${model.capabilitySource}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            model.description?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            }
        }
    }
}
