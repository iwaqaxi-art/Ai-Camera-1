package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.model.AiSceneType
import com.example.model.CapturedPhoto
import com.example.model.FlashMode
import com.example.model.FocusAnalysis
import com.example.model.LightingAnalysis
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AiCameraManager(private val context: Context) {

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var currentLensFacing = CameraSelector.LENS_FACING_BACK
    private var currentFlashMode = FlashMode.AI_AUTO
    private var isAiControlActive = true
    private var lastEvAppliedIndex = 0
    private var lastEvChangeTime = 0L
    private var lastFocusTriggerTime = 0L
    private var hasInitialSetupCompleted = false
    private var isAdjustmentPendingOnTouch = false
    private var currentFrameAnalyzer: AiFrameAnalyzer? = null

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onAnalysisUpdate: (LightingAnalysis, FocusAnalysis, AiSceneType) -> Unit,
        onError: (String) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val analyzer = AiFrameAnalyzer { lighting, focus, scene ->
                    onAnalysisUpdate(lighting, focus, scene)
                    if (isAiControlActive) {
                        applyAiLightingControl(lighting)
                    }
                }
                currentFrameAnalyzer = analyzer

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, analyzer)
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(currentLensFacing)
                    .build()

                provider.unbindAll()
                val cam = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )

                camera = cam
                cameraControl = cam.cameraControl
                cameraInfo = cam.cameraInfo

                applyFlashMode(currentFlashMode)
            } catch (exc: Exception) {
                Log.e("AiCameraManager", "Binding failed: ${exc.message}", exc)
                onError(exc.message ?: "Failed to start camera")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun applyAiLightingControl(lighting: LightingAnalysis) {
        val control = cameraControl ?: return
        val info = cameraInfo ?: return

        if (hasInitialSetupCompleted && !isAdjustmentPendingOnTouch) return

        val now = System.currentTimeMillis()
        if (now - lastEvChangeTime < 800L) return

        val exposureState = info.exposureState
        if (!exposureState.isExposureCompensationSupported) return

        val range = exposureState.exposureCompensationRange
        val targetIndex = lighting.appliedEvIndex.coerceIn(range.lower, range.upper)

        if (targetIndex != lastEvAppliedIndex) {
            control.setExposureCompensationIndex(targetIndex)
            lastEvAppliedIndex = targetIndex
            lastEvChangeTime = now
            if (isAdjustmentPendingOnTouch) {
                isAdjustmentPendingOnTouch = false
            }
        }
    }

    fun triggerTapToFocus(normalizedX: Float, normalizedY: Float) {
        val control = cameraControl ?: return
        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val point = factory.createPoint(normalizedX, normalizedY)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(4, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        control.startFocusAndMetering(action)
        isAdjustmentPendingOnTouch = true
        currentFrameAnalyzer?.resetFocusToTarget(normalizedX, normalizedY)
    }

    fun setAiControlEnabled(enabled: Boolean) {
        isAiControlActive = enabled
        if (enabled) {
            isAdjustmentPendingOnTouch = true
        }
    }

    fun setManualExposure(evValue: Float) {
        val control = cameraControl ?: return
        val info = cameraInfo ?: return
        val exposureState = info.exposureState
        if (!exposureState.isExposureCompensationSupported) return

        val step = exposureState.exposureCompensationStep.toFloat()
        if (step > 0f) {
            val targetIdx = (evValue / step).toInt()
            val range = exposureState.exposureCompensationRange
            control.setExposureCompensationIndex(targetIdx.coerceIn(range.lower, range.upper))
        }
    }

    fun setZoom(ratio: Float) {
        cameraControl?.setLinearZoom(ratio.coerceIn(0f, 1f))
    }

    fun toggleCameraLens(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onAnalysisUpdate: (LightingAnalysis, FocusAnalysis, AiSceneType) -> Unit,
        onError: (String) -> Unit
    ) {
        currentLensFacing = if (currentLensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindCamera(lifecycleOwner, previewView, onAnalysisUpdate, onError)
    }

    fun setFlashMode(mode: FlashMode) {
        currentFlashMode = mode
        applyFlashMode(mode)
    }

    private fun applyFlashMode(mode: FlashMode) {
        val capture = imageCapture ?: return
        capture.flashMode = when (mode) {
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AI_AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    /**
     * Captures photo raw bitmap from CameraX directly into memory for image enhancement pipeline.
     */
    fun captureRawBitmap(
        onBitmapCaptured: (Bitmap) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError("Camera capture is not initialized.")
            return
        }

        capture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val rotation = image.imageInfo.rotationDegrees
                        val rawBitmap = image.toBitmap()
                        val orientedBitmap = if (rotation != 0) {
                            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                        } else {
                            rawBitmap
                        }
                        image.close()
                        onBitmapCaptured(orientedBitmap)
                    } catch (e: Exception) {
                        image.close()
                        onError("Failed to process captured frame: ${e.message}")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onError("Photo capture error: ${exception.message}")
                }
            }
        )
    }

    fun release() {
        cameraExecutor.shutdown()
    }
}
