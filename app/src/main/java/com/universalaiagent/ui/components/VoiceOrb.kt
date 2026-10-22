package com.universalaiagent.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun VoiceOrb(
    active: Boolean,
    speaking: Boolean,
    amplitude: Float,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "voice-orb")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    val energy = amplitude.coerceIn(0f, 1f)
    val animated = active || speaking

    val clickModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }

    Box(
        modifier = modifier.then(clickModifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val scale = if (animated) breathe + energy * 0.08f else breathe

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.primary.copy(alpha = 0.45f),
                        colors.secondary.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = base,
                ),
                radius = base,
                center = center,
            )

            if (animated) {
                repeat(3) { index ->
                    val progress = (phase + index / 3f) % 1f
                    drawCircle(
                        color = colors.primary.copy(alpha = (1f - progress) * 0.32f),
                        radius = base * (0.76f + progress * 0.34f + energy * 0.05f),
                        center = center,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.primary,
                        colors.secondary,
                        colors.tertiary.copy(alpha = 0.9f),
                    ),
                    center = center - Offset(0f, base * 0.14f),
                    radius = base * 0.9f,
                ),
                radius = base * 0.68f * scale,
                center = center,
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.34f),
                        Color.Transparent,
                    ),
                    center = center - Offset(base * 0.18f, base * 0.28f),
                    radius = base * 0.5f,
                ),
                radius = base * 0.62f * scale,
                center = center,
            )
        }

        Icon(
            imageVector = when {
                speaking -> Icons.Default.RecordVoiceOver
                active -> Icons.Default.GraphicEq
                else -> Icons.Default.Mic
            },
            contentDescription = when {
                speaking -> "Assistant is speaking"
                active -> "Listening"
                else -> "Start voice input"
            },
            modifier = Modifier.size(iconSize),
            tint = Color.White,
        )
    }
}
