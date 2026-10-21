package com.universalaiagent.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalaiagent.screen.ScreenCaptureController
import com.universalaiagent.ui.ScreenViewModel
import com.universalaiagent.ui.components.ErrorBanner
import com.universalaiagent.ui.components.InfoCard
import com.universalaiagent.ui.components.MarkdownText
import com.universalaiagent.ui.components.ScreenScaffold
import com.universalaiagent.ui.components.StatusPill
import com.universalaiagent.ui.theme.DangerRed
import com.universalaiagent.ui.theme.SuccessGreen
import com.universalaiagent.ui.theme.WarningAmber

@Composable
fun ScreenUnderstandingScreen(
    viewModel: ScreenViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val projectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureController.start(context, result.resultCode, data)
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    fun requestSharing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    ScreenScaffold(
        title = "Screen understanding",
        subtitle = "Consent-based, single-frame capture",
        onBack = onBack,
        actions = {
            if (ui.sharingActive) {
                TextButtonStop(onClick = viewModel::stopSharing)
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
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ScreenShare, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Screen sharing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (ui.sharingActive) {
                            StatusPill("Active", SuccessGreen)
                        } else {
                            StatusPill("Inactive", MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Android will ask for screen-capture consent every time. While sharing, a persistent " +
                            "notification has a Stop action. The app captures only when you tap Capture or " +
                            "Analyze, and it does not save screenshots or recordings.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (ui.sharingActive) {
                        OutlinedButton(onClick = viewModel::stopSharing) {
                            Icon(Icons.Default.StopScreenShare, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Stop screen sharing")
                        }
                    } else {
                        Button(onClick = { requestSharing() }) {
                            Icon(Icons.Default.ScreenShare, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Start screen sharing")
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Send screenshots to the AI provider", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "When off, captures stay local and are never uploaded. Vision routing only uses " +
                            "models marked as image-capable.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = ui.cloudEnabled, onCheckedChange = viewModel::setCloudEnabled)
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.prompt,
                onValueChange = viewModel::setPrompt,
                label = { Text("Question about the screen (optional)") },
                placeholder = { Text("What is shown here and what should I do next?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::capturePreview,
                    enabled = ui.sharingActive && !ui.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Capture only")
                }
                Button(
                    onClick = viewModel::captureAndAnalyze,
                    enabled = ui.sharingActive && !ui.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    if (ui.busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(if (ui.busy) "Working…" else "Capture & analyze")
                }
            }
            if (!ui.sharingActive) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Start screen sharing before capturing.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ui.status?.let {
                Spacer(Modifier.height(10.dp))
                StatusPill(it, WarningAmber)
            }
            ui.error?.let {
                Spacer(Modifier.height(10.dp))
                ErrorBanner(message = it, onDismiss = viewModel::clearError)
            }

            ui.preview?.let { bitmap ->
                Spacer(Modifier.height(14.dp))
                Text("Last frame (memory only)", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured screen preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }

            if (ui.analysis.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("Screen analysis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        MarkdownText(ui.analysis)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            InfoCard(
                title = "What is sent and what is not",
                body = "Only the frame you explicitly analyze is sent, together with your question, to the " +
                    "selected vision-capable provider. Providers receive that image under their own privacy " +
                    "terms. The app does not record video, does not upload automatically, and clears the " +
                    "preview when you stop sharing. Secure windows may appear black or fail to capture.",
                icon = Icons.Default.ScreenShare,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            InfoCard(
                title = "Screenshot protection cannot be bypassed",
                body = "Apps can mark windows as secure. The app respects Android's capture restrictions and " +
                    "cannot reveal protected content.",
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TextButtonStop(onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text("Stop", color = DangerRed)
    }
}
