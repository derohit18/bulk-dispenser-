#!/bin/bash

# Update Colors for a Premium M3 Look
cat << 'INNER' > app/src/main/java/com/example/ui/theme/Color.kt
package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val TelecomCobalt = Color(0xFF4F46E5) // Indigo 600
val TelecomCobaltDark = Color(0xFF3730A3) // Indigo 800
val TelecomCobaltLight = Color(0xFFE0E7FF) // Indigo 100

val CanvasBackground = Color(0xFFF8FAFC) // Slate 50
val CardSurface = Color(0xFFFFFFFF)
val CardSurfaceVariant = Color(0xFFF1F5F9) // Slate 100
val BorderSubtle = Color(0xFFE2E8F0) // Slate 200

val TextPrimary = Color(0xFF0F172A) // Slate 900
val TextSecondary = Color(0xFF475569) // Slate 600
val TextMuted = Color(0xFF94A3B8) // Slate 400

val StatusDelivered = Color(0xFF059669) // Emerald 600
val StatusDeliveredBg = Color(0xFFD1FAE5) // Emerald 100

val StatusThrottle = Color(0xFFD97706) // Amber 600
val StatusThrottleBg = Color(0xFFFEF3C7) // Amber 100

val StatusFailed = Color(0xFFDC2626) // Red 600
val StatusFailedBg = Color(0xFFFEE2E2) // Red 100

val StatusQueued = Color(0xFF64748B) // Slate 500
val StatusQueuedBg = Color(0xFFF1F5F9) // Slate 100
INNER

# Redesign TelemetryHud.kt
cat << 'INNER' > app/src/main/java/com/example/ui/components/TelemetryHud.kt
package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.LiveDispatchState
import com.example.ui.theme.*

@Composable
fun TelemetryHud(
    liveState: LiveDispatchState,
    totalCount: Int,
    queuedCount: Int,
    sentCount: Int,
    deliveredCount: Int,
    failedCount: Int,
    hasSmsPermission: Boolean,
    onStartDispatch: () -> Unit,
    onPauseDispatch: () -> Unit,
    onResumeDispatch: () -> Unit,
    onStopDispatch: () -> Unit,
    onRetryFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CardSurface)
            .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (liveState.isRunning) TelecomCobaltLight else CardSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (liveState.isRunning) Icons.Rounded.RocketLaunch else Icons.Rounded.DataUsage,
                        contentDescription = null,
                        tint = if (liveState.isRunning) TelecomCobalt else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Campaign Status",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = when {
                            liveState.isRunning && !liveState.isPaused -> "Active Dispatch"
                            liveState.isPaused -> "Paused"
                            totalCount > 0 && queuedCount == 0 -> "Completed"
                            else -> "Ready to Send"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            if (!hasSmsPermission) {
                Icon(Icons.Rounded.Warning, contentDescription = "No Permission", tint = StatusFailed)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Progress Bar
        val progress = if (totalCount > 0) ((totalCount - queuedCount).toFloat() / totalCount.toFloat()) else 0f
        val animatedProgress by animateFloatAsState(targetValue = progress)

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Progress", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("${(animatedProgress * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TelecomCobalt)
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = TelecomCobalt,
                trackColor = CardSurfaceVariant,
            )
            if (liveState.isRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = liveState.statusMessage,
                    fontSize = 12.sp,
                    fontFamily = TelemetryMonospace,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Grid Metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard("Delivered", deliveredCount.toString(), Icons.Rounded.CheckCircle, StatusDelivered, StatusDeliveredBg, Modifier.weight(1f))
            MetricCard("Queued", queuedCount.toString(), Icons.Rounded.HourglassEmpty, StatusQueued, StatusQueuedBg, Modifier.weight(1f))
            MetricCard("Failed", failedCount.toString(), Icons.Rounded.ErrorOutline, StatusFailed, StatusFailedBg, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!liveState.isRunning) {
                Button(
                    onClick = onStartDispatch,
                    enabled = queuedCount > 0 && hasSmsPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomCobalt),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier.weight(1f).testTag("start_dispatch_button")
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Campaign", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                if (failedCount > 0) {
                    FilledTonalButton(
                        onClick = onRetryFailed,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = StatusFailedBg, contentColor = StatusFailed)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            } else {
                if (liveState.isPaused) {
                    Button(
                        onClick = onResumeDispatch,
                        colors = ButtonDefaults.buttonColors(containerColor = TelecomCobalt),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        modifier = Modifier.weight(1f).testTag("resume_dispatch_button")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resume", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    FilledTonalButton(
                        onClick = onPauseDispatch,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = StatusThrottleBg, contentColor = StatusThrottle),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        modifier = Modifier.weight(1f).testTag("pause_dispatch_button")
                    ) {
                        Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pause", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                FilledTonalButton(
                    onClick = onStopDispatch,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = StatusFailedBg, contentColor = StatusFailed),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier.testTag("stop_dispatch_button")
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = TextPrimary)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
    }
}
INNER
