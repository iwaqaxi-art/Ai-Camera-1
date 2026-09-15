package com.example.enhancement

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object OpenCvEnhancer {
    private const val TAG = "OpenCvEnhancer"
    private var isInitialized = false

    fun init(context: Context? = null): Boolean {
        if (!isInitialized) {
            try {
                isInitialized = OpenCVLoader.initDebug()
                Log.d(TAG, "OpenCV initialized successfully: $isInitialized")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize OpenCV native libraries", e)
                isInitialized = false
            }
        }
        return isInitialized
    }

    /**
     * Step 1: On-device pre-processing using OpenCV for Android:
     * 1. Bilateral filter for edge-preserving noise reduction
     * 2. CLAHE (Contrast Limited Adaptive Histogram Equalization) in LAB space for local contrast enhancement
     * 3. Unsharp masking for fine detail sharpening
     */
    fun enhance(inputBitmap: Bitmap): Bitmap {
        if (!isInitialized) {
            init()
        }

        if (!isInitialized) {
            Log.w(TAG, "OpenCV native loader not ready, running software enhancement fallback")
            return fallbackEnhance(inputBitmap)
        }

        val rgbaMat = Mat()
        val bgrMat = Mat()
        val bilateralMat = Mat()
        val labMat = Mat()
        val labChannels = ArrayList<Mat>()
        val claheBgrMat = Mat()
        val blurredMat = Mat()
        val sharpBgrMat = Mat()
        val resultRgbaMat = Mat()

        try {
            // Convert input Bitmap to RGBA Mat
            Utils.bitmapToMat(inputBitmap, rgbaMat)

            // Convert to BGR for standard OpenCV color-space filters
            Imgproc.cvtColor(rgbaMat, bgrMat, Imgproc.COLOR_RGBA2BGR)

            // 1. Bilateral filter for noise reduction (smooths noise while preserving crisp object edges)
            // d = 9, sigmaColor = 75, sigmaSpace = 75
            Imgproc.bilateralFilter(bgrMat, bilateralMat, 9, 75.0, 75.0)

            // 2. CLAHE for local contrast enhancement (in LAB color space to modify L-luminance only)
            Imgproc.cvtColor(bilateralMat, labMat, Imgproc.COLOR_BGR2Lab)
            Core.split(labMat, labChannels)

            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(labChannels[0], labChannels[0])

            Core.merge(labChannels, labMat)
            Imgproc.cvtColor(labMat, claheBgrMat, Imgproc.COLOR_Lab2BGR)

            // 3. Unsharp masking for sharpening:
            // Gaussian blur -> subtract blurred from original -> add back weighted
            Imgproc.GaussianBlur(claheBgrMat, blurredMat, Size(0.0, 0.0), 2.5)
            Core.addWeighted(claheBgrMat, 1.45, blurredMat, -0.45, 0.0, sharpBgrMat)

            // Convert back to RGBA for Android Bitmap rendering
            Imgproc.cvtColor(sharpBgrMat, resultRgbaMat, Imgproc.COLOR_BGR2RGBA)

            val outputBitmap = Bitmap.createBitmap(
                resultRgbaMat.cols(),
                resultRgbaMat.rows(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(resultRgbaMat, outputBitmap)
            Log.d(TAG, "OpenCV enhancement (Bilateral + CLAHE + Unsharp) applied successfully.")
            return outputBitmap
        } catch (e: Throwable) {
            Log.e(TAG, "Exception during OpenCV enhancement, falling back to software filter", e)
            return fallbackEnhance(inputBitmap)
        } finally {
            rgbaMat.release()
            bgrMat.release()
            bilateralMat.release()
            for (ch in labChannels) ch.release()
            labMat.release()
            claheBgrMat.release()
            blurredMat.release()
            sharpBgrMat.release()
            resultRgbaMat.release()
        }
    }

    private fun fallbackEnhance(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val cm = ColorMatrix(floatArrayOf(
            1.12f, 0f, 0f, 0f, 6f,
            0f, 1.12f, 0f, 0f, 6f,
            0f, 0f, 1.12f, 0f, 6f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }
}
