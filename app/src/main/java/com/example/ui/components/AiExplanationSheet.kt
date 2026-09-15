package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AiSceneType
import com.example.model.FocusAnalysis
import com.example.model.LightingAnalysis
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiExplanationSheet(
    sceneType: AiSceneType,
    lighting: LightingAnalysis,
    focus: FocusAnalysis,
    isTouchLocked: Boolean,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        contentColor = TextPrimary,
        modifier = Modifier.testTag("ai_explanation_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AI Optics & Enhancement Engine",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Active Hardware & AI Pipeline Metrics",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            // Pipeline Highlight Card (Step 1 OpenCV + Step 2 Gemini)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceVariantDark)
                    .border(1.dp, AiCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AiCyan, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Two-Step Enhancement Pipeline",
                        color = AiCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "1. OpenCV On-Device: Bilateral noise filter, LAB-space CLAHE local contrast, and Unsharp masking.\n" +
                           "2. Gemini Flagship AI: Professional smartphone camera tuning for dynamic range, natural skin tones, and texture preservation.",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            // Scene Card
            MetricRow(
                icon = Icons.Default.Science,
                title = "Detected Scene: ${sceneType.title}",
                description = sceneType.description,
                badge = if (sceneType.isNightMode) "Night Assist Active" else "Balanced"
            )

            // Touch Lock status
            MetricRow(
                icon = Icons.Default.Tune,
                title = "AI Touch-Lock Engine",
                description = if (isTouchLocked) {
                    "Hardware focus & EV locked to prevent hunting. Tap viewfinder anytime to re-target."
                } else {
                    "Adjusting optics to tap coordinate before re-locking."
                },
                badge = if (isTouchLocked) "Locked" else "Tuning"
            )

            // Live Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricChip(title = "Exposure Bias", value = lighting.evString, modifier = Modifier.weight(1f))
                MetricChip(title = "Ambient Lux", value = "${lighting.luxEstimate.toInt()} lx", modifier = Modifier.weight(1f))
                MetricChip(title = "Color Temp", value = lighting.colorWarmth.split(" ").firstOrNull() ?: "5500K", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricRow(
    icon: ImageVector,
    title: String,
    description: String,
    badge: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, BorderDark, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = AiCyan, modifier = Modifier.size(18.dp))
                Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = badge,
                color = AiEmerald,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AiEmerald.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(text = description, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun MetricChip(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = title, color = TextMuted, fontSize = 11.sp)
        Text(text = value, color = AiCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
