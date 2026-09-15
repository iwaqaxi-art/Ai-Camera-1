package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AiAmber
import com.example.ui.theme.AiCyan

@Composable
fun AiFocusReticle(
    normalizedX: Float,
    normalizedY: Float,
    isLocked: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val reticleSize = 72.dp
        val pxWidth = constraints.maxWidth.toFloat()
        val pxHeight = constraints.maxHeight.toFloat()

        val centerX = normalizedX * pxWidth
        val centerY = normalizedY * pxHeight

        val reticleColor = if (isLocked) AiCyan else AiAmber

        Canvas(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val s = reticleSize.toPx() * (if (isLocked) 1f else scale)
            val left = centerX - s / 2f
            val top = centerY - s / 2f

            // Outer brackets
            drawRoundRect(
                color = reticleColor,
                topLeft = Offset(left, top),
                size = Size(s, s),
                cornerRadius = CornerRadius(12f, 12f),
                style = Stroke(width = 2.5f)
            )

            // Center crosshair / dot
            drawCircle(
                color = reticleColor,
                radius = 3.5f,
                center = Offset(centerX, centerY)
            )

            // Top, bottom, left, right notch ticks
            val tickLen = 8f
            drawLine(reticleColor, Offset(centerX, top), Offset(centerX, top + tickLen), strokeWidth = 2f)
            drawLine(reticleColor, Offset(centerX, top + s), Offset(centerX, top + s - tickLen), strokeWidth = 2f)
            drawLine(reticleColor, Offset(left, centerY), Offset(left + tickLen, centerY), strokeWidth = 2f)
            drawLine(reticleColor, Offset(left + s, centerY), Offset(left + s - tickLen, centerY), strokeWidth = 2f)
        }
    }
}
