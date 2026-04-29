package com.parkingscanner.data.service

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraService(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val executor: Executor by lazy {
        ContextCompat.getMainExecutor(context)
    }

    fun setFrameAnalyzer(analyzer: ImageAnalysis.Analyzer) {
        imageAnalysis?.setAnalyzer(executor, analyzer)
    }

    fun clearAnalyzer() {
        imageAnalysis?.clearAnalyzer()
    }

    suspend fun initializeCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        cameraProvider = ProcessCameraProvider.getInstance(context).get()

        val preview = androidx.camera.core.Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture,
                imageAnalysis
            )
        } catch (e: Exception) {
            throw Exception("Camera initialization failed: ${e.message}", e)
        }
    }

    suspend fun capturePhoto(): ImageProxy = suspendCancellableCoroutine { continuation ->
        val capture = imageCapture ?: run {
            continuation.resumeWithException(Exception("Camera not initialized"))
            return@suspendCancellableCoroutine
        }

        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
        ).build()

        capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                continuation.resume(image)
            }

            override fun onError(exception: ImageCaptureException) {
                continuation.resumeWithException(exception)
            }
        })
    }

    fun releaseCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
    }
}
