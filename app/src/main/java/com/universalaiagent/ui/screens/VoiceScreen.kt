package com.universalaiagent.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalaiagent.ui.VoiceViewModel
import com.universalaiagent.ui.components.ErrorBanner
import com.universalaiagent.ui.components.InfoCard
import com.universalaiagent.ui.components.MarkdownText
import com.universalaiagent.ui.components.ScreenScaffold
import com.universalaiagent.ui.components.StatusPill
import com.universalaiagent.ui.components.VoiceOrb
import com.universalaiagent.ui.theme.DangerRed
import com.universalaiagent.ui.theme.SuccessGreen
import com.universalaiagent.ui.theme.WarningAmber

@Composable
fun VoiceScreen(
    viewModel: VoiceViewModel,
    onBack: () -> Unit,
    onSendToAgent: (String) -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val voice by viewModel.voiceState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        if (granted) viewModel.startListening()
    }

    ScreenScaffold(
        title = "Voice assistant",
        subtitle = "Push-to-talk · speech recognition + TTS",
        onBack = onBack,
        actions = {
            IconButton(onClick = viewModel::clear) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            InfoCard(
                title = "Live push-to-talk orb",
                body = "Hold the orb and speak. The rings react to your voice level in real time. " +
                    "Your speech uses one strong haptic pulse, and the assistant's spoken reply uses a " +
                    "distinct double pulse. TTS stops before listening to prevent echo.",
            )
            Spacer(Modifier.height(22.dp))

            Box(
                modifier = Modifier
                    .size(214.dp)
                    .pointerInput(hasMicPermission, ui.busy) {
                        detectTapGestures(
                            onPress = {
                                if (!hasMicPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else if (!ui.busy) {
                                    viewModel.startListening()
                                    tryAwaitRelease()
                                    viewModel.stopListening()
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                VoiceOrb(
                    active = voice.listening,
                    speaking = voice.speaking,
                    amplitude = voice.amplitude,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 52.dp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    voice.listening -> "Listening… release to send"
                    voice.speaking -> "Assistant is speaking"
                    ui.busy -> "Thinking…"
                    else -> "Hold the orb to talk"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    !hasMicPermission -> StatusPill("Mic permission needed", WarningAmber)
                    voice.listening -> StatusPill("Recording", DangerRed)
                    voice.speaking -> StatusPill("TTS active", SuccessGreen)
                    ui.busy -> StatusPill("Request in progress", WarningAmber)
                    else -> StatusPill("Idle", MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!voice.recognitionAvailable) {
                    StatusPill("Recognition unavailable", DangerRed)
                }
            }
            if (voice.listening) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { voice.amplitude.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("You said", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        voice.partialTranscript.ifBlank { ui.transcript.ifBlank { "Nothing yet." } },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Assistant", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        ui.responseProvider?.let { StatusPill(it, SuccessGreen) }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (ui.response.isBlank()) {
                        Text(
                            if (ui.busy) "Generating a spoken response…" else "No response yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MarkdownText(ui.response)
                    }
                }
            }

            ui.error?.let {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = it)
            }
            voice.error?.let {
                Spacer(Modifier.height(12.dp))
                ErrorBanner(message = it)
            }
            ui.status?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::stopGeneration,
                    enabled = ui.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Stop AI")
                }
                OutlinedButton(
                    onClick = viewModel::stopSpeaking,
                    enabled = voice.speaking,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Stop speech")
                }
            }
            if (ui.transcript.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onSendToAgent(ui.transcript) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Use transcript in Agent mode")
                }
            }
            Spacer(Modifier.height(14.dp))
            InfoCard(
                title = "Voice + on-device actions",
                body = "Voice commands are transcribed by Android, sent as text, and spoken with Android TTS. " +
                    "This works even when a provider has no realtime audio API. The transcript can jump " +
                    "straight into Agent mode in chat, where sensitive actions ask for confirmation.",
                icon = Icons.Default.Mic,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}
