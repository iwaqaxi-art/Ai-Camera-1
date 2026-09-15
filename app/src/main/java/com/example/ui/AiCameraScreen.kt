package com.example.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.FlashMode
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.AiCameraViewModel

@Composable
fun AiCameraScreen(
    viewModel: AiCameraViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setPermissionGranted(isGranted)
    }

    LaunchedEffect(Unit) {
        val hasCam = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        viewModel.setPermissionGranted(hasCam)
    }

    if (!uiState.hasCameraPermission) {
        CameraPermissionCard(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            modifier = modifier
        )
        return
    }

    val shutterScale by animateFloatAsState(
        targetValue = if (uiState.isShutterAnimating) 0.85f else 1.0f,
        label = "shutterScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CameraBlack)
            .testTag("ai_camera_screen")
    ) {
        // Camera Preview
        var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewViewRef = this
                    viewModel.cameraManager.bindCamera(
                        lifecycleOwner = lifecycleOwner,
                        previewView = this,
                        onAnalysisUpdate = { lighting, focus, scene ->
                            viewModel.updateAnalysis(lighting, focus, scene)
                        },
                        onError = { /* fallback gracefully */ }
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val normX = (offset.x / size.width).coerceIn(0f, 1f)
                        val normY = (offset.y / size.height).coerceIn(0f, 1f)
                        viewModel.handleTapToFocus(normX, normY)
                    }
                }
        )

        // Focus Reticle Indicator
        uiState.tapFocusPoint?.let { (x, y) ->
            AiFocusReticle(
                normalizedX = x,
                normalizedY = y,
                isLocked = uiState.isTouchLocked
            )
        }

        // Top Control Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flash Toggle
                IconButton(
                    onClick = {
                        val nextFlash = when (uiState.flashMode) {
                            FlashMode.AI_AUTO -> FlashMode.ON
                            FlashMode.ON -> FlashMode.OFF
                            FlashMode.OFF -> FlashMode.AI_AUTO
                        }
                        viewModel.setFlashMode(nextFlash)
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SurfaceDark.copy(alpha = 0.7f))
                        .border(1.dp, BorderDark, CircleShape)
                        .testTag("flash_toggle_button")
                ) {
                    val icon = when (uiState.flashMode) {
                        FlashMode.AI_AUTO -> Icons.Default.FlashAuto
                        FlashMode.ON -> Icons.Default.FlashOn
                        FlashMode.OFF -> Icons.Default.FlashOff
                    }
                    val tint = if (uiState.flashMode == FlashMode.AI_AUTO) AiCyan else TextPrimary
                    Icon(icon, contentDescription = "Flash ${uiState.flashMode.title}", tint = tint)
                }

                // AI Engine Pill Toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (uiState.isAiControlActive) AiCyan.copy(alpha = 0.2f) else SurfaceDark.copy(alpha = 0.7f))
                        .border(1.dp, if (uiState.isAiControlActive) AiCyan else BorderDark, RoundedCornerShape(20.dp))
                        .clickable { viewModel.toggleAiControl() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("ai_autopilot_toggle"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI Control",
                        tint = if (uiState.isAiControlActive) AiCyan else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (uiState.isAiControlActive) "AI AUTO-PILOT" else "MANUAL",
                        color = if (uiState.isAiControlActive) AiCyan else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Right Actions: Simulator & Info Sheets
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { viewModel.toggleSimulatorSheet(true) },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceDark.copy(alpha = 0.7f))
                            .border(1.dp, BorderDark, CircleShape)
                            .testTag("open_simulator_button")
                    ) {
                        Icon(Icons.Default.Science, contentDescription = "Simulate", tint = TextPrimary)
                    }

                    IconButton(
                        onClick = { viewModel.toggleInfoSheet(true) },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceDark.copy(alpha = 0.7f))
                            .border(1.dp, BorderDark, CircleShape)
                            .testTag("open_info_button")
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Info", tint = TextPrimary)
                    }
                }
            }

            // Real-time AI Telemetry Overlay
            AiTelemetryOverlay(
                sceneType = uiState.sceneType,
                lighting = uiState.lightingAnalysis,
                focus = uiState.focusAnalysis,
                isAiActive = uiState.isAiControlActive,
                isTouchLocked = uiState.isTouchLocked,
                onInfoClick = { viewModel.toggleInfoSheet(true) }
            )

            // Info Notice Pill
            uiState.infoNotice?.let { notice ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = notice,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceDark.copy(alpha = 0.9f))
                            .border(1.dp, AiCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Bottom Controls Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Zoom Selector Buttons (0.5x, 1x, 2x)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark.copy(alpha = 0.75f))
                    .border(1.dp, BorderDark, RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                listOf(
                    Pair("0.5x", 0.0f),
                    Pair("1x", 0.35f),
                    Pair("2x", 0.75f)
                ).forEach { (label, ratio) ->
                    val isSelected = kotlin.math.abs(uiState.zoomRatio - ratio) < 0.15f
                    Text(
                        text = label,
                        color = if (isSelected) AiCyan else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) AiCyan.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { viewModel.setZoom(ratio) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("zoom_button_$label")
                    )
                }
            }

            // Shutter Row: Gallery Thumb, Shutter Button, Flip Camera
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery Thumbnail Button
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceVariantDark)
                        .border(1.5.dp, BorderDark, RoundedCornerShape(14.dp))
                        .clickable {
                            uiState.photos.firstOrNull()?.let {
                                viewModel.openPhotoPreview(it)
                            }
                        }
                        .testTag("gallery_thumbnail_button"),
                    contentAlignment = Alignment.Center
                ) {
                    val lastPhoto = uiState.photos.firstOrNull()
                    if (lastPhoto?.thumbnailBitmap != null) {
                        Image(
                            bitmap = lastPhoto.thumbnailBitmap.asImageBitmap(),
                            contentDescription = "Last Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Shutter Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(4.dp, AiCyan, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isCapturing) AiCyan.copy(alpha = 0.5f) else Color.White)
                        .scale(shutterScale)
                        .clickable(enabled = !uiState.isCapturing && !uiState.isEnhancingPhoto) {
                            viewModel.takePhoto()
                        }
                        .testTag("camera_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = CameraBlack,
                            strokeWidth = 3.dp
                        )
                    }
                }

                // Flip Camera Lens Button
                IconButton(
                    onClick = {
                        previewViewRef?.let { pv ->
                            viewModel.cameraManager.toggleCameraLens(
                                lifecycleOwner = lifecycleOwner,
                                previewView = pv,
                                onAnalysisUpdate = { lighting, focus, scene ->
                                    viewModel.updateAnalysis(lighting, focus, scene)
                                },
                                onError = { /* fallback */ }
                            )
                        }
                    },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(SurfaceVariantDark)
                        .border(1.dp, BorderDark, CircleShape)
                        .testTag("flip_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip Camera",
                        tint = TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // "Enhancing photo..." Loading Indicator Dialog
        EnhancingDialog(
            isVisible = uiState.isEnhancingPhoto,
            statusText = uiState.enhancementStatusText
        )

        // Sheets
        if (uiState.showInfoSheet) {
            AiExplanationSheet(
                sceneType = uiState.sceneType,
                lighting = uiState.lightingAnalysis,
                focus = uiState.focusAnalysis,
                isTouchLocked = uiState.isTouchLocked,
                onDismiss = { viewModel.toggleInfoSheet(false) }
            )
        }

        if (uiState.showSimulatorSheet) {
            AiSceneSimulatorSheet(
                currentScene = uiState.sceneType,
                onSelectScene = { scene ->
                    viewModel.simulateSceneCondition(scene)
                },
                onDismiss = { viewModel.toggleSimulatorSheet(false) }
            )
        }

        uiState.selectedPhotoForPreview?.let { photo ->
            PhotoPreviewSheet(
                photo = photo,
                onDelete = { viewModel.deletePhoto(photo) },
                onDismiss = { viewModel.closePhotoPreview() }
            )
        }
    }
}
