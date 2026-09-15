package com.example.model

import android.graphics.Bitmap

enum class AiSceneType(
    val title: String,
    val description: String,
    val recommendedEvOffset: Float,
    val isNightMode: Boolean = false,
    val requiresHdr: Boolean = false
) {
    DAYLIGHT("Daylight", "Bright natural light detected. Flat balance active.", 0.0f),
    PORTRAIT("Portrait Subject", "Center subject detected. Warm skin tone enhancement active.", 0.3f),
    LOW_LIGHT("Low Light / Night", "Shadows detected. Auto night assist active.", 1.2f, isNightMode = true),
    HIGH_CONTRAST("High Contrast (HDR)", "High dynamic range detected. Shadow lift active.", -0.5f, requiresHdr = true),
    INDOOR_WARM("Indoor Warm", "Artificial warm lighting. Auto white balance correction active.", 0.0f),
    BACKLIT("Backlit Subject", "Bright background detected. Fill-light exposure boost active.", 0.8f, requiresHdr = true)
}

enum class FlashMode(val title: String) {
    OFF("Off"),
    ON("Forced On"),
    AI_AUTO("AI Smart Auto")
}

data class LightingAnalysis(
    val luminance: Float = 0f,
    val status: String = "Analyzing...",
    val appliedEvIndex: Int = 0,
    val evValue: Float = 0f,
    val isNightAssistActive: Boolean = false,
    val histogramBins: List<Float> = emptyList(),
    val luxEstimate: Float = 300f,
    val contrastBoost: Float = 1.0f,
    val colorWarmth: String = "5500K Neutral",
    val saturationBoost: Float = 1.0f,
    val sharpnessGrade: String = "Crisp (98%)",
    val flashRecommendation: String = "Natural Ambient (No Flash)"
) {
    val evString: String
        get() = if (evValue >= 0f) String.format("+%.1f EV", evValue) else String.format("%.1f EV", evValue)
}

data class FocusAnalysis(
    val focusScore: Int = 0,
    val isFocused: Boolean = false,
    val targetX: Float = 0.5f,
    val targetY: Float = 0.5f,
    val status: String = "Searching..."
)

data class CapturedPhoto(
    val id: String,
    val filePath: String,
    val contentUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val sceneType: AiSceneType = AiSceneType.DAYLIGHT,
    val evApplied: String = "0.0 EV",
    val sharpness: Int = 85,
    val luminance: Int = 130,
    val thumbnailBitmap: Bitmap? = null,
    val isAiEnhanced: Boolean = false
)
