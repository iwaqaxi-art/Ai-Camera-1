package com.example.viewmodel

import com.example.model.AiSceneType
import com.example.model.CapturedPhoto
import com.example.model.FlashMode
import com.example.model.FocusAnalysis
import com.example.model.LightingAnalysis

data class AiCameraUiState(
    val hasCameraPermission: Boolean = false,
    val isAiControlActive: Boolean = true,
    val lightingAnalysis: LightingAnalysis = LightingAnalysis(),
    val focusAnalysis: FocusAnalysis = FocusAnalysis(),
    val sceneType: AiSceneType = AiSceneType.DAYLIGHT,
    val flashMode: FlashMode = FlashMode.AI_AUTO,
    val zoomRatio: Float = 0f,
    val manualEv: Float = 0f,
    val isShutterAnimating: Boolean = false,
    val isCapturing: Boolean = false,
    val isEnhancingPhoto: Boolean = false,
    val enhancementStatusText: String = "Enhancing photo...",
    val photos: List<CapturedPhoto> = emptyList(),
    val selectedPhotoForPreview: CapturedPhoto? = null,
    val showInfoSheet: Boolean = false,
    val showSimulatorSheet: Boolean = false,
    val tapFocusPoint: Pair<Float, Float>? = null,
    val isTouchLocked: Boolean = true,
    val infoNotice: String? = null,
    val errorMessage: String? = null
)
