package com.example.engine

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.model.AiSceneType
import com.example.model.FocusAnalysis
import com.example.model.LightingAnalysis
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AiFrameAnalyzer(
    private val onAnalysisResult: (LightingAnalysis, FocusAnalysis, AiSceneType) -> Unit
) : ImageAnalysis.Analyzer {

    private var frameCounter = 0
    private var lastFocusCheckTime = 0L
    private var lastSharpness = 75
    private var isCurrentlySearching = false
    private var isInitialLockAchieved = false
    private var focusLockTimestamp = 0L
    private var currentFocusX = 0.5f
    private var currentFocusY = 0.5f

    fun resetFocusToTarget(normX: Float, normY: Float) {
        currentFocusX = normX
        currentFocusY = normY
        isCurrentlySearching = true
        lastFocusCheckTime = System.currentTimeMillis()
    }

    override fun analyze(image: ImageProxy) {
        frameCounter++
        if (frameCounter % 2 != 0) {
            image.close()
            return
        }

        try {
            val planes = image.planes
            if (planes.isEmpty()) {
                image.close()
                return
            }

            val yPlane = planes[0]
            val buffer = yPlane.buffer
            val width = image.width
            val height = image.height
            val rowStride = yPlane.rowStride

            val sampleStepX = max(1, width / 120)
            val sampleStepY = max(1, height / 90)

            var totalLuminance = 0L
            var sampleCount = 0
            val histogram = IntArray(16)
            val zoneEdgeEnergy = LongArray(9)
            val zoneSampleCount = IntArray(9)

            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            for (y in 0 until (height - sampleStepY) step sampleStepY) {
                val rowOffset = y * rowStride
                val nextRowOffset = (y + sampleStepY) * rowStride

                val gridY = min(2, (y * 3) / height)

                for (x in 0 until (width - sampleStepX) step sampleStepX) {
                    val idx = rowOffset + x
                    if (idx >= bytes.size) continue

                    val pixelVal = bytes[idx].toInt() and 0xFF
                    totalLuminance += pixelVal
                    sampleCount++

                    val bin = min(15, pixelVal / 16)
                    histogram[bin]++

                    val rightIdx = idx + sampleStepX
                    val downIdx = nextRowOffset + x

                    if (rightIdx < bytes.size && downIdx < bytes.size) {
                        val rightVal = bytes[rightIdx].toInt() and 0xFF
                        val downVal = bytes[downIdx].toInt() and 0xFF
                        val grad = abs(pixelVal - rightVal) + abs(pixelVal - downVal)

                        val gridX = min(2, (x * 3) / width)
                        val zone = gridY * 3 + gridX
                        zoneEdgeEnergy[zone] += grad
                        zoneSampleCount[zone]++
                    }
                }
            }

            val avgLuminance = if (sampleCount > 0) totalLuminance.toFloat() / sampleCount else 128f
            val targetZone = (min(2, (currentFocusY * 3).toInt()) * 3 + min(2, (currentFocusX * 3).toInt())).coerceIn(0, 8)
            val targetZoneAvgEdge = if (zoneSampleCount[targetZone] > 0) {
                zoneEdgeEnergy[targetZone].toFloat() / zoneSampleCount[targetZone]
            } else 20f

            val sharpnessScore = ((targetZoneAvgEdge / 45f) * 100f).roundToInt().coerceIn(10, 100)

            val now = System.currentTimeMillis()
            val focusStatus: String
            val isFocused: Boolean

            if (isCurrentlySearching) {
                if (now - lastFocusCheckTime > 750L) {
                    isCurrentlySearching = false
                    isInitialLockAchieved = true
                    focusLockTimestamp = now
                    lastSharpness = sharpnessScore
                    focusStatus = "Target Locked ($sharpnessScore%)"
                    isFocused = true
                } else {
                    focusStatus = "Focusing ($sharpnessScore%)..."
                    isFocused = false
                }
            } else if (!isInitialLockAchieved) {
                if (now - lastFocusCheckTime > 1200L) {
                    isInitialLockAchieved = true
                    focusLockTimestamp = now
                    lastSharpness = sharpnessScore
                    focusStatus = "Auto Locked ($sharpnessScore%)"
                    isFocused = true
                } else {
                    focusStatus = "Auto Detecting ($sharpnessScore%)..."
                    isFocused = false
                }
            } else {
                focusStatus = "Locked ($sharpnessScore%)"
                isFocused = true
            }

            // Scene Detection Logic
            val darkBins = histogram[0] + histogram[1] + histogram[2]
            val brightBins = histogram[13] + histogram[14] + histogram[15]
            val totalBins = max(1, sampleCount)

            val darkRatio = darkBins.toFloat() / totalBins
            val brightRatio = brightBins.toFloat() / totalBins

            val sceneType = when {
                avgLuminance < 60f || darkRatio > 0.45f -> AiSceneType.LOW_LIGHT
                darkRatio > 0.25f && brightRatio > 0.25f -> AiSceneType.HIGH_CONTRAST
                brightRatio > 0.4f -> AiSceneType.BACKLIT
                currentFocusX in 0.35f..0.65f && currentFocusY in 0.3f..0.7f && avgLuminance in 80f..180f -> AiSceneType.PORTRAIT
                avgLuminance > 160f -> AiSceneType.DAYLIGHT
                else -> AiSceneType.INDOOR_WARM
            }

            val lightingStatus = when {
                avgLuminance < 50f -> "Low Light (Night Mode active)"
                avgLuminance in 50f..90f -> "Dim (Lifting Shadows)"
                avgLuminance in 90f..160f -> "Optimal Exposure"
                avgLuminance in 160f..210f -> "Bright (Highlight Protection)"
                else -> "Overexposed (Reducing EV)"
            }

            val evIndex = when {
                avgLuminance < 40f -> 4
                avgLuminance < 75f -> 2
                avgLuminance < 110f -> 1
                avgLuminance in 110f..160f -> 0
                avgLuminance in 160f..200f -> -1
                else -> -2
            }
            val evValue = evIndex * 0.5f

            val histogramNorm = histogram.map { it.toFloat() / totalBins }

            val lightingAnalysis = LightingAnalysis(
                luminance = avgLuminance,
                status = lightingStatus,
                appliedEvIndex = evIndex,
                evValue = evValue,
                isNightAssistActive = sceneType.isNightMode,
                histogramBins = histogramNorm,
                luxEstimate = (avgLuminance * 3.5f).coerceIn(10f, 2500f),
                contrastBoost = if (sceneType.requiresHdr) 1.25f else 1.05f,
                colorWarmth = when (sceneType) {
                    AiSceneType.PORTRAIT -> "5200K Portrait Tone"
                    AiSceneType.LOW_LIGHT -> "4800K Cool Night"
                    AiSceneType.DAYLIGHT -> "5600K True Daylight"
                    AiSceneType.INDOOR_WARM -> "4200K Balanced Warm"
                    else -> "5500K Standard"
                },
                saturationBoost = if (sceneType == AiSceneType.PORTRAIT) 1.05f else 1.10f,
                sharpnessGrade = if (sharpnessScore > 80) "Flagship ($sharpnessScore%)" else "Good ($sharpnessScore%)",
                flashRecommendation = if (avgLuminance < 40f) "Flash Recommended" else "Natural Ambient (No Flash)"
            )

            val focusAnalysis = FocusAnalysis(
                focusScore = sharpnessScore,
                isFocused = isFocused,
                targetX = currentFocusX,
                targetY = currentFocusY,
                status = focusStatus
            )

            onAnalysisResult(lightingAnalysis, focusAnalysis, sceneType)

        } catch (e: Exception) {
            // Safe fallback
        } finally {
            image.close()
        }
    }
}
