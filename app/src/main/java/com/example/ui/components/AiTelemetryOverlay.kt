package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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

@Composable
fun AiTelemetryOverlay(
    sceneType: AiSceneType,
    lighting: LightingAnalysis,
    focus: FocusAnalysis,
    isAiActive: Boolean,
    isTouchLocked: Boolean,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sceneIcon: ImageVector = when (sceneType) {
        AiSceneType.LOW_LIGHT -> Icons.Default.Nightlight
        AiSceneType.DAYLIGHT -> Icons.Default.WbSunny
        AiSceneType.PORTRAIT -> Icons.Default.Face
        AiSceneType.HIGH_CONTRAST -> Icons.Default.BrightnessMedium
        AiSceneType.BACKLIT -> Icons.Default.WbSunny
        AiSceneType.INDOOR_WARM -> Icons.Default.AutoAwesome
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scene Tag
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark.copy(alpha = 0.85f))
                    .border(1.dp, if (isAiActive) AiCyan.copy(alpha = 0.6f) else BorderDark, RoundedCornerShape(20.dp))
                    .clickable { onInfoClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("ai_scene_badge"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = sceneIcon,
                    contentDescription = sceneType.title,
                    tint = if (isAiActive) AiCyan else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = sceneType.title,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isAiActive) {
                    Text(
                        text = if (isTouchLocked) "● LOCKED" else "○ TUNING",
                        color = if (isTouchLocked) AiEmerald else AiAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quick Info Sheet Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark.copy(alpha = 0.85f))
                    .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
                    .clickable { onInfoClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("ai_info_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "AI Metrics",
                    tint = AiCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = lighting.evString,
                    color = AiCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Telemetry details ticker
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Focus: ${focus.status}",
                color = if (focus.isFocused) AiEmerald else AiAmber,
                fontSize = 11.sp
            )
            Text(
                text = lighting.colorWarmth,
                color = TextSecondary,
                fontSize = 11.sp
            )
            Text(
                text = "Sharp: ${lighting.sharpnessGrade}",
                color = TextPrimary,
                fontSize = 11.sp
            )
        }
    }
}
