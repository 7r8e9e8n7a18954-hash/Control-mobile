package com.universalaiagent.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalaiagent.data.model.Capability
import com.universalaiagent.data.model.ProviderType
import com.universalaiagent.ui.AddProviderViewModel
import com.universalaiagent.ui.components.ErrorBanner
import com.universalaiagent.ui.components.InfoCard
import com.universalaiagent.ui.components.ScreenScaffold
import com.universalaiagent.ui.components.SectionTitle
import com.universalaiagent.ui.components.StatusPill

@Composable
fun AddProviderScreen(
    viewModel: AddProviderViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var providerMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }

    LaunchedEffect(ui.saved) {
        if (ui.saved) onBack()
    }

    ScreenScaffold(
        title = if (ui.isEditing) "Edit provider" else "Add API provider",
        subtitle = "Keys are encrypted locally and never exported",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionTitle("Provider")
            Spacer(Modifier.height(6.dp))
            Box {
                OutlinedButton(
                    onClick = { providerMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        ui.provider.displayName,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                    ProviderType.entries.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider.displayName) },
                            onClick = {
                                viewModel.setProvider(provider)
                                providerMenu = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.nickname,
                onValueChange = viewModel::setNickname,
                label = { Text("Nickname (optional)") },
                placeholder = { Text("e.g. Personal Gemini") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.apiKey,
                onValueChange = viewModel::setApiKey,
                label = { Text("API key") },
                placeholder = {
                    Text(
                        when {
                            ui.hasStoredKey -> "Stored key present — leave blank to keep it"
                            ui.provider == ProviderType.CUSTOM_OPENAI -> "Optional for local endpoints"
                            else -> "Paste your provider API key"
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (ui.apiKeyVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = viewModel::toggleKeyVisibility) {
                        Icon(
                            if (ui.apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (ui.apiKeyVisible) "Hide key" else "Show key",
                        )
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "The full key is never displayed again. Only the last four characters are shown in lists.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.baseUrl,
                onValueChange = viewModel::setBaseUrl,
                label = { Text("Base URL (optional override)") },
                placeholder = {
                    Text(
                        ui.provider.defaultBaseUrl.ifBlank { "https://your-server.example/v1" },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Default: ${ui.provider.defaultBaseUrl.ifBlank { "required for custom providers" }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ui.baseUrl.startsWith("http://") || ui.provider == ProviderType.CUSTOM_OPENAI) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Allow insecure local HTTP", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Only localhost, 127.0.0.1, and 10.0.2.2 are accepted. HTTPS is always preferred.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = ui.allowInsecureHttp,
                        onCheckedChange = viewModel::setAllowInsecureHttp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.priority.toString(),
                onValueChange = { viewModel.setPriority(it.toIntOrNull() ?: 0) },
                label = { Text("Default priority (lower number runs first)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Spacer(Modifier.height(20.dp))
            SectionTitle("Model")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::discoverModels,
                    enabled = !ui.discovering && !ui.loading,
                    modifier = Modifier.weight(1f),
                ) {
                    if (ui.discovering) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(if (ui.discovering) "Discovering…" else "Discover models")
                }
                if (ui.models.isNotEmpty()) {
                    OutlinedButton(onClick = { modelMenu = true }, modifier = Modifier.weight(1f)) {
                        Text("${ui.models.size} models")
                    }
                }
            }
            if (ui.models.isNotEmpty()) {
                Box {
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        ui.models.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(model.displayName)
                                        Text(
                                            model.id,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.selectModel(model)
                                    modelMenu = false
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.model,
                onValueChange = viewModel::setModel,
                label = { Text("Model ID") },
                placeholder = { Text("Select from discovery or enter manually") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            val selectedModel = ui.models.firstOrNull { it.id == ui.model }
            if (selectedModel != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    selectedModel.capabilities.forEach { capability ->
                        StatusPill(
                            capability.name.lowercase().replace('_', ' '),
                            when (capability) {
                                Capability.VISION -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.secondary
                            },
                        )
                    }
                }
                selectedModel.contextWindow?.let {
                    Text(
                        "Context window: $it tokens · ${selectedModel.capabilitySource}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Supports image input", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Enable only if this model accepts images. Routing excludes other models for vision tasks.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = ui.supportsVision, onCheckedChange = viewModel::setSupportsVision)
            }

            ui.error?.let {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = it, onDismiss = viewModel::clearMessages)
            }
            ui.message?.let {
                Spacer(Modifier.height(12.dp))
                InfoCard(title = "Status", body = it)
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = viewModel::save,
                enabled = !ui.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (ui.loading) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (ui.isEditing) "Save changes" else "Save provider")
            }
            Spacer(Modifier.height(14.dp))
            InfoCard(
                title = "Direct connections only",
                body = "Requests go from this device to the provider. The app never sends your key to a " +
                    "developer server. Billing, quotas, and model access are controlled by your provider account.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
