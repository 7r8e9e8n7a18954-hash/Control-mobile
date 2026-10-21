package com.universalaiagent.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.universalaiagent.ui.components.InfoCard
import com.universalaiagent.ui.components.SectionTitle

@Composable
fun WelcomeScreen(
    onChat: () -> Unit,
    onKeys: () -> Unit,
    onPrivacy: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.padding(14.dp).size(34.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column {
                    Text("Universal AI Agent", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Your keys. Your models. Your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "A multi-provider Android assistant",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Bring your own OpenAI, Gemini, Claude, Groq, OpenRouter, or compatible API keys. " +
                            "Chat, fall back between providers, understand screens, use voice, and automate " +
                            "accessible Android UI — without a custom backend.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("What it can do")
            Spacer(Modifier.height(10.dp))
            FeatureRow(
                icon = Icons.Default.Key,
                title = "Multi-key routing",
                body = "Store multiple keys per provider, order fallbacks per task, and cool down failing keys.",
            )
            FeatureRow(
                icon = Icons.Default.SmartToy,
                title = "Real provider APIs",
                body = "Streaming chat, model discovery, token usage, and provider error handling.",
            )
            FeatureRow(
                icon = Icons.Default.Screenshot,
                title = "Screen understanding",
                body = "Explicit MediaProjection consent, one-frame capture, and optional vision analysis.",
            )
            FeatureRow(
                icon = Icons.Default.TouchApp,
                title = "Accessible automation",
                body = "Structured UI actions through Android Accessibility — never generated executable code.",
            )
            FeatureRow(
                icon = Icons.Default.Mic,
                title = "Voice assistant",
                body = "Push-to-talk speech recognition with text-to-speech and echo prevention.",
            )

            Spacer(Modifier.height(20.dp))
            InfoCard(
                title = "No backend, no developer-owned keys",
                body = "All configuration stays on this device. API keys are encrypted with an Android " +
                    "Keystore-backed key. Requests go directly to the provider you configure.",
                icon = Icons.Default.PrivacyTip,
            )
            Spacer(Modifier.height(12.dp))
            InfoCard(
                title = "Android limits are real",
                body = "Screen capture requires consent, automation requires an enabled Accessibility " +
                    "service, protected windows may block screenshots, and some apps prevent automation. " +
                    "Sensitive actions ask for confirmation.",
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onChat,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open chat")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onKeys,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Configure API keys")
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onPrivacy, modifier = Modifier.weight(1f)) {
                    Text("Privacy")
                }
                OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Settings")
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Universal AI Agent v1.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun FeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Row(Modifier.padding(vertical = 8.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(9.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
