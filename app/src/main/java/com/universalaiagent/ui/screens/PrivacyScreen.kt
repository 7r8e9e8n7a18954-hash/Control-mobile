package com.universalaiagent.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.universalaiagent.ui.components.InfoCard
import com.universalaiagent.ui.components.ScreenScaffold
import com.universalaiagent.ui.components.SectionTitle

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    ScreenScaffold(
        title = "Privacy and security",
        subtitle = "A plain-language data map",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            InfoCard(
                title = "Short version",
                body = "No developer backend, no analytics, no ads, no telemetry. Your API keys, chats, " +
                    "images, screenshots, and usage records stay on this device unless you explicitly send " +
                    "content to the provider you configured. The app's author receives nothing.",
                icon = Icons.Default.PrivacyTip,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
            Spacer(Modifier.height(16.dp))

            SectionTitle("Stored on this device")
            Spacer(Modifier.height(8.dp))
            InfoCard(
                title = "Encrypted API keys",
                body = "Keys are encrypted with an AES-GCM key inside the Android Keystore. The ciphertext " +
                    "is stored in files/secure/api_keys.vault. Keys are masked in the UI and excluded from " +
                    "exports and backup rules.",
                icon = Icons.Default.Key,
            )
            Spacer(Modifier.height(8.dp))
            InfoCard(
                title = "Conversations and images",
                body = "Chats are written to files/conversations.json when history is enabled. Attached " +
                    "images are downscaled, re-encoded as JPEG, and copied into files/attachments. Deleting " +
                    "a conversation removes its stored images.",
                icon = Icons.Default.Storage,
            )
            Spacer(Modifier.height(8.dp))
            InfoCard(
                title = "Settings and usage",
                body = "Preferences live in DataStore (files/app_settings.preferences_pb). Request metadata " +
                    "is stored in files/usage_history.json: provider, model, tokens reported by the provider, " +
                    "error category, and any cost the provider reports. Prompts and responses are not copied " +
                    "into usage records.",
                icon = Icons.Default.BugReport,
            )
            Spacer(Modifier.height(8.dp))
            InfoCard(
                title = "Task reports",
                body = "Automation task instructions, steps, confirmations, and result reports are kept in " +
                    "memory. Only the current task and the latest task history survive while the process is " +
                    "alive; they are not written to disk by this build.",
                icon = Icons.Default.TouchApp,
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("Sent to your provider")
            Spacer(Modifier.height(8.dp))
            Text(
                "Prompts, chat history needed for context, attached images that you send, a single " +
                    "screen frame when you tap Capture & analyze, and UI text from the visible screen when " +
                    "you run an automation task are sent directly to the provider selected by routing. The " +
                    "request includes your API key in the provider-specific header. Provider logging, " +
                    "retention, training, and jurisdiction are governed by that provider's terms, not by this app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            InfoCard(
                title = "Images and screen frames",
                body = "Chat images are sent only when the selected task route supports vision. Screen " +
                    "analysis sends one JPEG frame, never a recording. Turn off cloud image processing in " +
                    "Settings to block image uploads entirely.",
                icon = Icons.Default.Image,
            )
            Spacer(Modifier.height(8.dp))
            InfoCard(
                title = "Voice",
                body = "Speech recognition is provided by Android or the device's recognition service, which " +
                    "may process audio in the cloud under the device vendor's terms. Only the resulting " +
                    "transcript is sent to your AI provider. Android TTS may also use network voices. The app " +
                    "does not record or store raw microphone audio.",
                icon = Icons.Default.Mic,
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("Not collected")
            Spacer(Modifier.height(8.dp))
            Text(
                "No account, email, phone number, contacts, location, advertising ID, analytics SDK, crash " +
                    "reporting SDK, or developer-operated server. No silent background requests. No " +
                    "provider-owned developer key: usage is billed to your own account.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("Android security boundaries")
            Spacer(Modifier.height(8.dp))
            Text(
                "Screen capture requires explicit MediaProjection consent and shows a persistent " +
                    "notification. Apps can mark windows secure, and the app cannot capture them. " +
                    "Accessibility can read and operate only exposed UI; it cannot bypass lock screens, " +
                    "biometrics, passwords, permission dialogs, or protected system UI. The app does not " +
                    "request root, does not install code dynamically, and never executes model-generated " +
                    "code, shell commands, JavaScript, or coordinates.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("Limits of client-side security")
            Spacer(Modifier.height(8.dp))
            Text(
                "A rooted, unlocked, or compromised device can expose any client-side secret, including " +
                    "keys decrypted for a request. Keystore encryption protects data at rest and ties the key " +
                    "to this app and device, but it cannot protect against a fully compromised operating " +
                    "system or a malicious provider. Use scoped, revocable API keys with spending limits.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("Backups and exports")
            Spacer(Modifier.height(8.dp))
            InfoCard(
                title = "Sensitive files are excluded from backup",
                body = "Android backup and device-to-device transfer rules exclude the encrypted key vault, " +
                    "conversations, attachments, and usage records. Sanitized exports are copied to the " +
                    "clipboard on request and contain no API keys.",
                icon = Icons.Default.Backup,
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("Your controls")
            Spacer(Modifier.height(8.dp))
            Text(
                "Disable history, cloud image processing, TTS, and sensitive-action confirmations in " +
                    "Settings. Delete conversations, attachments, usage, individual keys, all keys, or all " +
                    "settings from Settings. Revoke provider keys in the provider's dashboard. Uninstalling " +
                    "the app removes its private storage and app-created Keystore material.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
