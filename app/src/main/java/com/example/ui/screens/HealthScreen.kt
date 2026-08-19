package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.OmniSyncViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HealthScreen(
    viewModel: OmniSyncViewModel,
    onRequestHealthPermissions: () -> Unit = {}
) {
    val healthList by viewModel.healthMetrics.collectAsState()
    val isSyncingWatch by viewModel.isSyncingWatch.collectAsState()
    val aiWellnessInsights by viewModel.aiWellnessAnalysis.collectAsState()
    val generatingInsights by viewModel.generatingWellnessAnalysis.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("health_screen")
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
    ) {
        // --- Header Core ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wearable Vitals",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Synced from Health Connect (Samsung Health, Fitbit, Google Fit…)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                Button(
                    onClick = { viewModel.triggerWatchSync() },
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSyncingWatch,
                    modifier = Modifier.testTag("health_sync_btn")
                ) {
                    if (isSyncingWatch) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
                    } else {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // --- Health Connect permission entry point ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.HealthAndSafety,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Health Connect access",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Allow steps, heart rate and sleep to be read from your wearable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = onRequestHealthPermissions,
                        modifier = Modifier.testTag("health_permission_btn")
                    ) {
                        Text("Grant", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- Vital ECG Pulse-Wave Animation ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MonitorHeart, contentDescription = null, tint = Color(0xFFEF5350))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Wearable Vital Waveform", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text("ECG Channel I (Simulated)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Native pulse line draw
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    ) {
                        EcgWaveformDrawing()
                    }
                }
            }
        }

        // --- Wellness AI Advisor Recommendations ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gemini AI Wellness Analysis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        if (generatingInsights) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
                        } else {
                            IconButton(onClick = { viewModel.generateWellnessInsights() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Regenerate insights")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (aiWellnessInsights.isNotEmpty()) {
                        Text(
                            text = aiWellnessInsights,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Analyzing your sleep, calories, and steps to write your actionable improvement tips...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // --- Metric Streams Log ---
        item {
            Text("Wearable Synchronization Streams", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        if (healthList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No watch records recorded. Complete search & active sync.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        } else {
            items(healthList) { metric ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Watch, contentDescription = "Watch Sync", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(metric.rxtype, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Pulse: ${metric.heartRate} bpm", style = MaterialTheme.typography.bodySmall)
                                Text("Steps: ${metric.steps}", style = MaterialTheme.typography.bodySmall)
                                Text("Sleep Score: ${metric.sleepScore}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Latest Hub Logs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EcgWaveformDrawing() {
    val infiniteTransition = rememberInfiniteTransition(label = "ecgWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ecgPhase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val path = Path()

        path.moveTo(0f, centerY)

        val pointsCount = 100
        val segmentWidth = width / pointsCount

        for (i in 0..pointsCount) {
            val x = i * segmentWidth
            val progress = (x / width + phase) % 1f
            val rad = progress * 2f * Math.PI

            // Simulate baseline noise + QRS ECG pulse peak
            val qrsIntensity = if (progress >= 0.4f && progress <= 0.5f) {
                val subProgress = (progress - 0.4f) / 0.1f
                if (subProgress < 0.3f) {
                    -20f // Q wave dip
                } else if (subProgress < 0.7f) {
                    48f  // R wave peak
                } else {
                    -30f // S wave dip
                }
            } else if (progress >= 0.52f && progress <= 0.62f) {
                12f  // T wave
            } else {
                (Math.sin(rad * 10).toFloat() * 1.5f) // Baseline noise
            }

            val y = centerY - qrsIntensity
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = Color(0xFFEF5350).copy(alpha = 0.9f),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
