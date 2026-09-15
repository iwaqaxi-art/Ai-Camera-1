package com.example.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.AiCameraManager
import com.example.enhancement.GeminiEnhancer
import com.example.enhancement.OpenCvEnhancer
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AiCameraViewModel(application: Application) : AndroidViewModel(application) {

    val cameraManager = AiCameraManager(application.applicationContext)

    private val _uiState = MutableStateFlow(AiCameraUiState())
    val uiState: StateFlow<AiCameraUiState> = _uiState.asStateFlow()

    private var tapResetJob: Job? = null
    private var noticeResetJob: Job? = null
    private var isFirstAnalysisReceived = false

    init {
        // Initialize OpenCV in background
        viewModelScope.launch(Dispatchers.Default) {
            OpenCvEnhancer.init(application.applicationContext)
        }
        loadExistingPhotos()
    }

    private fun loadExistingPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            val photoDir = File(getApplication<Application>().filesDir, "ai_photos")
            if (photoDir.exists()) {
                val files = photoDir.listFiles { f -> f.extension.equals("jpg", ignoreCase = true) }
                    ?.sortedByDescending { it.lastModified() }
                    ?.take(10) ?: emptyList()

                val list = files.map { file ->
                    val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    val thumb = bmp?.let {
                        val maxDim = 256
                        val scale = maxDim.toFloat() / maxOf(it.width, it.height)
                        Bitmap.createScaledBitmap(it, (it.width * scale).toInt(), (it.height * scale).toInt(), true)
                    }
                    CapturedPhoto(
                        id = file.nameWithoutExtension,
                        filePath = file.absolutePath,
                        contentUri = null,
                        timestamp = file.lastModified(),
                        sceneType = AiSceneType.DAYLIGHT,
                        evApplied = "0.0 EV",
                        sharpness = 92,
                        luminance = 128,
                        thumbnailBitmap = thumb,
                        isAiEnhanced = true
                    )
                }
                _uiState.update { it.copy(photos = list) }
            }
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted) }
    }

    fun updateAnalysis(lighting: LightingAnalysis, focus: FocusAnalysis, scene: AiSceneType) {
        val wasFirst = !isFirstAnalysisReceived
        isFirstAnalysisReceived = true

        _uiState.update { current ->
            current.copy(
                lightingAnalysis = lighting,
                focusAnalysis = focus,
                sceneType = scene,
                isTouchLocked = if (wasFirst) true else current.isTouchLocked,
                infoNotice = if (wasFirst) "AI Auto-Pilot: Scene Optimized & Locked" else current.infoNotice
            )
        }

        if (wasFirst) {
            scheduleClearNotice()
        }
    }

    fun toggleAiControl() {
        _uiState.update { current ->
            val newState = !current.isAiControlActive
            cameraManager.setAiControlEnabled(newState)
            current.copy(
                isAiControlActive = newState,
                infoNotice = if (newState) "AI Engine: Enabled" else "Manual Controls: Enabled"
            )
        }
        scheduleClearNotice()
    }

    fun setFlashMode(mode: FlashMode) {
        cameraManager.setFlashMode(mode)
        _uiState.update { it.copy(flashMode = mode) }
    }

    fun setZoom(ratio: Float) {
        cameraManager.setZoom(ratio)
        _uiState.update { it.copy(zoomRatio = ratio) }
    }

    fun setManualEv(ev: Float) {
        cameraManager.setManualExposure(ev)
        _uiState.update { it.copy(manualEv = ev) }
    }

    fun handleTapToFocus(normalizedX: Float, normalizedY: Float) {
        tapResetJob?.cancel()
        cameraManager.triggerTapToFocus(normalizedX, normalizedY)

        _uiState.update {
            it.copy(
                tapFocusPoint = Pair(normalizedX, normalizedY),
                isTouchLocked = false,
                infoNotice = "Optimizing focus & exposure for target..."
            )
        }

        tapResetJob = viewModelScope.launch {
            delay(1800L)
            _uiState.update {
                it.copy(
                    tapFocusPoint = null,
                    isTouchLocked = true,
                    infoNotice = "AI Touch-Lock: Target Locked"
                )
            }
            scheduleClearNotice()
        }
    }

    fun takePhoto() {
        if (_uiState.value.isCapturing || _uiState.value.isEnhancingPhoto) return

        _uiState.update {
            it.copy(
                isShutterAnimating = true,
                isCapturing = true,
                isEnhancingPhoto = true,
                enhancementStatusText = "Enhancing photo..."
            )
        }

        viewModelScope.launch {
            delay(200)
            _uiState.update { it.copy(isShutterAnimating = false) }
        }

        cameraManager.captureRawBitmap(
            onBitmapCaptured = { rawBitmap ->
                processEnhancementPipeline(rawBitmap)
            },
            onError = { err ->
                Log.w("AiCameraViewModel", "Hardware capture failed ($err). Running capture simulation.")
                simulateCaptureAndEnhance()
            }
        )
    }

    private fun processEnhancementPipeline(rawBitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Step 1: On-device OpenCV pre-processing (Bilateral filter, CLAHE, Unsharp masking)
                _uiState.update {
                    it.copy(enhancementStatusText = "Enhancing photo...\nStep 1/2: On-Device OpenCV Processing")
                }
                val step1Bitmap = OpenCvEnhancer.enhance(rawBitmap)

                // Step 2: Firebase AI (Gemini) enhancement
                _uiState.update {
                    it.copy(enhancementStatusText = "Enhancing photo...\nStep 2/2: Gemini AI Enhancement")
                }
                val (finalBitmap, isAiSuccess) = GeminiEnhancer.enhanceWithAi(step1Bitmap)

                // Save final result to Gallery & App Storage
                val currentSnapshot = _uiState.value
                val photo = saveEnhancedPhoto(
                    finalBitmap,
                    currentSnapshot.sceneType,
                    currentSnapshot.lightingAnalysis.evString,
                    currentSnapshot.focusAnalysis.focusScore,
                    currentSnapshot.lightingAnalysis.luminance.toInt(),
                    isAiSuccess
                )

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            isEnhancingPhoto = false,
                            photos = listOf(photo) + it.photos,
                            infoNotice = if (isAiSuccess) "Saved: Flagship Gemini AI Enhanced" else "Saved: OpenCV Enhanced (Offline Fallback)"
                        )
                    }
                    scheduleClearNotice()
                }
            } catch (e: Exception) {
                Log.e("AiCameraViewModel", "Pipeline error", e)
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            isEnhancingPhoto = false,
                            errorMessage = "Enhancement error: ${e.localizedMessage}"
                        )
                    }
                }
            }
        }
    }

    private fun simulateCaptureAndEnhance() {
        viewModelScope.launch(Dispatchers.Default) {
            val currentState = _uiState.value
            val scene = currentState.sceneType

            // Create a realistic sample photo based on scene
            val width = 1080
            val height = 1920
            val baseBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(baseBitmap)

            val baseColor = when (scene) {
                AiSceneType.LOW_LIGHT -> Color.rgb(20, 24, 38)
                AiSceneType.PORTRAIT -> Color.rgb(180, 130, 110)
                AiSceneType.DAYLIGHT -> Color.rgb(70, 140, 220)
                AiSceneType.HIGH_CONTRAST -> Color.rgb(40, 50, 60)
                AiSceneType.INDOOR_WARM -> Color.rgb(210, 160, 100)
                AiSceneType.BACKLIT -> Color.rgb(120, 150, 180)
            }
            canvas.drawColor(baseColor)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 48f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("AI Camera: ${scene.title}", width / 2f, height / 2f - 40f, paint)

            paint.textSize = 32f
            paint.color = Color.LTGRAY
            canvas.drawText("Shot with AI Auto-Pilot | ${currentState.lightingAnalysis.evString}", width / 2f, height / 2f + 30f, paint)

            processEnhancementPipeline(baseBitmap)
        }
    }

    private suspend fun saveEnhancedPhoto(
        bitmap: Bitmap,
        sceneType: AiSceneType,
        evApplied: String,
        sharpness: Int,
        luminance: Int,
        isAiEnhanced: Boolean
    ): CapturedPhoto = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "AICAM_${timeStamp}.jpg"

        // 1. Save to Device Gallery via MediaStore
        var contentUriString: String? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AICamera")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 96, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                }
                contentUriString = uri.toString()
            }
        } catch (e: Exception) {
            Log.w("AiCameraViewModel", "Gallery save via MediaStore failed: ${e.message}")
        }

        // 2. Save to internal app storage for reliable instant thumbnail loading
        val photoDir = File(context.filesDir, "ai_photos").apply { if (!exists()) mkdirs() }
        val localFile = File(photoDir, fileName)
        FileOutputStream(localFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        // Create fast thumbnail
        val maxThumbDim = 256
        val scale = maxThumbDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        val thumb = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )

        CapturedPhoto(
            id = "photo_${timeStamp}",
            filePath = localFile.absolutePath,
            contentUri = contentUriString,
            timestamp = System.currentTimeMillis(),
            sceneType = sceneType,
            evApplied = evApplied,
            sharpness = sharpness,
            luminance = luminance,
            thumbnailBitmap = thumb,
            isAiEnhanced = isAiEnhanced
        )
    }

    fun simulateSceneCondition(scene: AiSceneType) {
        val simulatedLighting = when (scene) {
            AiSceneType.LOW_LIGHT -> LightingAnalysis(
                luminance = 32f,
                status = "Low Light (Night Mode active)",
                appliedEvIndex = 3,
                evValue = 1.5f,
                isNightAssistActive = true,
                luxEstimate = 45f,
                colorWarmth = "4800K Cool Night",
                sharpnessGrade = "Crisp (92%)",
                flashRecommendation = "Flash Recommended"
            )
            AiSceneType.PORTRAIT -> LightingAnalysis(
                luminance = 125f,
                status = "Optimal Exposure (Portrait Depth)",
                appliedEvIndex = 1,
                evValue = 0.5f,
                luxEstimate = 650f,
                colorWarmth = "5200K Warm Portrait",
                sharpnessGrade = "Pin-Sharp (99%)"
            )
            AiSceneType.HIGH_CONTRAST -> LightingAnalysis(
                luminance = 155f,
                status = "High Dynamic Range (Shadow Lift)",
                appliedEvIndex = -1,
                evValue = -0.5f,
                luxEstimate = 1200f,
                contrastBoost = 1.3f
            )
            AiSceneType.BACKLIT -> LightingAnalysis(
                luminance = 175f,
                status = "Backlit (Fill-Light Boost)",
                appliedEvIndex = 2,
                evValue = 1.0f,
                luxEstimate = 1600f
            )
            AiSceneType.INDOOR_WARM -> LightingAnalysis(
                luminance = 110f,
                status = "Warm Ambient (Auto AWB)",
                appliedEvIndex = 0,
                evValue = 0.0f,
                luxEstimate = 420f,
                colorWarmth = "4200K Balanced Warm"
            )
            AiSceneType.DAYLIGHT -> LightingAnalysis(
                luminance = 140f,
                status = "Natural Daylight",
                appliedEvIndex = 0,
                evValue = 0.0f,
                luxEstimate = 850f,
                colorWarmth = "5600K True Daylight"
            )
        }

        _uiState.update {
            it.copy(
                sceneType = scene,
                lightingAnalysis = simulatedLighting,
                infoNotice = "Simulated Scene: ${scene.title}"
            )
        }
        scheduleClearNotice()
    }

    fun openPhotoPreview(photo: CapturedPhoto) {
        _uiState.update { it.copy(selectedPhotoForPreview = photo) }
    }

    fun closePhotoPreview() {
        _uiState.update { it.copy(selectedPhotoForPreview = null) }
    }

    fun deletePhoto(photo: CapturedPhoto) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(photo.filePath)
            if (file.exists()) file.delete()

            photo.contentUri?.let { uriStr ->
                try {
                    val uri = android.net.Uri.parse(uriStr)
                    getApplication<Application>().contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    Log.w("AiCameraViewModel", "Could not delete from MediaStore: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    current.copy(
                        photos = current.photos.filter { it.id != photo.id },
                        selectedPhotoForPreview = if (current.selectedPhotoForPreview?.id == photo.id) null else current.selectedPhotoForPreview,
                        infoNotice = "Photo deleted"
                    )
                }
                scheduleClearNotice()
            }
        }
    }

    fun toggleInfoSheet(show: Boolean) {
        _uiState.update { it.copy(showInfoSheet = show) }
    }

    fun toggleSimulatorSheet(show: Boolean) {
        _uiState.update { it.copy(showSimulatorSheet = show) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun scheduleClearNotice() {
        noticeResetJob?.cancel()
        noticeResetJob = viewModelScope.launch {
            delay(3200L)
            _uiState.update { it.copy(infoNotice = null) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.release()
    }
}
