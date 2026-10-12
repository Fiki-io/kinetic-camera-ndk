package com.example.camera.pipeline

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.provider.MediaStore
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.camera.model.CameraConfiguration
import com.example.camera.nativeengine.NativeStabilizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class CameraPipelineManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var supportedFpsRanges: List<Range<Int>> = emptyList()
    private var currentConfig: CameraConfiguration = CameraConfiguration()

    var onRecordingStatusChanged: ((isRecording: Boolean, durationNanos: Long) -> Unit)? = null
    var onStabilizationStatusConfirmed: ((oisActive: Boolean, previewStabilizationActive: Boolean) -> Unit)? = null
    var onVideoSaved: ((Uri) -> Unit)? = null
    var onPhotoSaved: ((Uri) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    init {
        try {
            val details = DeviceHardwareInspector.inspectBackCamera(context)
            supportedFpsRanges = details.supportedFpsRanges
        } catch (_: Throwable) {
            supportedFpsRanges = listOf(Range(30, 30))
        }

        // Aktifkan C++ Native Stabilizer Engine
        try {
            NativeStabilizer.initialize()
            NativeStabilizer.setEnabled(true)
        } catch (_: Throwable) {}
    }

    fun initialize(
        previewView: PreviewView,
        config: CameraConfiguration,
        onInitialized: (minZoom: Float, maxZoom: Float) -> Unit
    ) {
        currentConfig = config
        NativeStabilizer.setEnabled(config.isEisStabilizationEnabled)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(previewView, config, onInitialized)
            } catch (t: Throwable) {
                onError?.invoke("Gagal menginisialisasi provider kamera: ${t.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun reconfigure(
        previewView: PreviewView,
        config: CameraConfiguration,
        onInitialized: (minZoom: Float, maxZoom: Float) -> Unit
    ) {
        currentConfig = config
        NativeStabilizer.setEnabled(config.isEisStabilizationEnabled)

        val provider = cameraProvider ?: return
        try {
            bindCameraUseCases(previewView, config, onInitialized)
        } catch (t: Throwable) {
            onError?.invoke("Gagal rekonfigurasi kamera: ${t.message}")
        }
    }

    private fun bindCameraUseCases(
        previewView: PreviewView,
        config: CameraConfiguration,
        onInitialized: (minZoom: Float, maxZoom: Float) -> Unit
    ) {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()
        } catch (_: Throwable) {}

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        // 1. Setup Preview
        val previewBuilder = Preview.Builder()
        var isPreviewStabilizationSupported = false

        if (config.isEisStabilizationEnabled) {
            try {
                val cameraInfo = provider.getCameraInfo(cameraSelector)
                val previewCapabilities = Preview.getPreviewCapabilities(cameraInfo)
                if (previewCapabilities.isStabilizationSupported) {
                    previewBuilder.setPreviewStabilizationEnabled(true)
                    isPreviewStabilizationSupported = true
                }
            } catch (_: Throwable) {}
        }

        try {
            val previewExtender = Camera2Interop.Extender(previewBuilder)
            applyHardwareControls(previewExtender, config)
        } catch (_: Throwable) {}

        preview = previewBuilder.build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        // 2. Setup Image Capture
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        // 3. Setup Video Recorder
        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.FHD, Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)
        )

        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()

        videoCapture = try {
            val vcBuilder = VideoCapture.Builder(recorder)
            if (config.isEisStabilizationEnabled) {
                try {
                    vcBuilder.setVideoStabilizationEnabled(true)
                } catch (_: Throwable) {}
            }
            vcBuilder.build()
        } catch (_: Throwable) {
            VideoCapture.withOutput(recorder)
        }

        val activePreview = preview ?: return
        val activeImage = imageCapture ?: return
        val activeVideo = videoCapture ?: return

        try {
            val boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                activePreview,
                activeImage,
                activeVideo
            )
            camera = boundCamera
            applyGlobalSessionControls(boundCamera, config)

            val zoomState = boundCamera.cameraInfo.zoomState.value
            val minZoom = zoomState?.minZoomRatio ?: 0.6f
            val maxZoom = zoomState?.maxZoomRatio ?: 10.0f
            onInitialized(minZoom, maxZoom)

            onStabilizationStatusConfirmed?.invoke(
                config.isOisLocked,
                NativeStabilizer.isNativeReady() || isPreviewStabilizationSupported || config.isEisStabilizationEnabled
            )
        } catch (t: Throwable) {
            onError?.invoke("Gagal mengikat use case kamera: ${t.message}")
        }
    }

    private fun applyGlobalSessionControls(boundCamera: Camera, config: CameraConfiguration) {
        try {
            val camera2Control = Camera2CameraControl.from(boundCamera.cameraControl)
            val requestOptionsBuilder = CaptureRequestOptions.Builder()

            // 1. Kunci motor suspensi fisik OIS pada modul 50MP
            if (config.isOisLocked) {
                requestOptionsBuilder.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
            } else {
                requestOptionsBuilder.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF
                )
            }

            // 2. Kunci stabilisasi video hardware
            if (config.isEisStabilizationEnabled) {
                requestOptionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
            } else {
                requestOptionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                )
            }

            // 3. Kunci frame pacing
            val safeFpsRange = DeviceHardwareInspector.findBestFpsRange(config.targetFps, supportedFpsRanges)
            requestOptionsBuilder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                safeFpsRange
            )

            requestOptionsBuilder.setCaptureRequestOption(
                CaptureRequest.NOISE_REDUCTION_MODE,
                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
            )
            requestOptionsBuilder.setCaptureRequestOption(
                CaptureRequest.EDGE_MODE,
                CaptureRequest.EDGE_MODE_HIGH_QUALITY
            )

            camera2Control.setCaptureRequestOptions(requestOptionsBuilder.build())
        } catch (_: Throwable) {}
    }

    private fun applyHardwareControls(
        extender: Camera2Interop.Extender<*>,
        config: CameraConfiguration
    ) {
        try {
            if (config.isOisLocked) {
                extender.setCaptureRequestOption(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
            }

            if (config.isEisStabilizationEnabled) {
                extender.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
            }

            val safeFpsRange = DeviceHardwareInspector.findBestFpsRange(config.targetFps, supportedFpsRanges)
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                safeFpsRange
            )

            extender.setCaptureRequestOption(
                CaptureRequest.NOISE_REDUCTION_MODE,
                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
            )
            extender.setCaptureRequestOption(
                CaptureRequest.EDGE_MODE,
                CaptureRequest.EDGE_MODE_HIGH_QUALITY
            )
        } catch (_: Throwable) {}
    }

    fun setZoomRatio(ratio: Float) {
        try {
            camera?.cameraControl?.setZoomRatio(ratio)
        } catch (_: Throwable) {}
    }

    fun triggerFocusAndMetering(x: Float, y: Float, previewView: PreviewView) {
        try {
            val factory = SurfaceOrientedMeteringPointFactory(
                previewView.width.toFloat(),
                previewView.height.toFloat()
            )
            val point = factory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(point).build()
            camera?.cameraControl?.startFocusAndMetering(action)
        } catch (_: Throwable) {}
    }

    @SuppressLint("MissingPermission")
    fun startRecording(hasAudioPermission: Boolean) {
        val capture = videoCapture ?: run {
            onError?.invoke("Subsistem perekam video belum siap.")
            return
        }

        try {
            val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_$name.mp4")
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/KineticCamera")
            }

            val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()

            val pendingRecording = capture.output
                .prepareRecording(context, mediaStoreOutput)
                .apply {
                    if (hasAudioPermission) {
                        withAudioEnabled()
                    }
                }

            activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        camera?.let { boundCam ->
                            applyGlobalSessionControls(boundCam, currentConfig)
                        }
                        onRecordingStatusChanged?.invoke(true, 0L)
                    }
                    is VideoRecordEvent.Status -> {
                        val durationNanos = recordEvent.recordingStats.recordedDurationNanos
                        onRecordingStatusChanged?.invoke(true, durationNanos)
                    }
                    is VideoRecordEvent.Finalize -> {
                        onRecordingStatusChanged?.invoke(false, 0L)
                        activeRecording = null
                        if (!recordEvent.hasError()) {
                            val outputUri = recordEvent.outputResults.outputUri
                            onVideoSaved?.invoke(outputUri)
                        } else {
                            onError?.invoke("Perekaman selesai dengan kode: ${recordEvent.error}")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            onError?.invoke("Gagal memulai perekaman: ${t.message}")
        }
    }

    fun stopRecording() {
        try {
            activeRecording?.stop()
        } catch (_: Throwable) {}
        activeRecording = null
    }

    fun isRecordingActive(): Boolean {
        return activeRecording != null
    }

    fun capturePhoto() {
        val capture = imageCapture ?: run {
            onError?.invoke("Subsistem foto belum siap.")
            return
        }

        try {
            val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_$name.jpg")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KineticCamera")
            }

            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        outputFileResults.savedUri?.let { onPhotoSaved?.invoke(it) }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        onError?.invoke("Gagal menyimpan foto: ${exception.message}")
                    }
                }
            )
        } catch (t: Throwable) {
            onError?.invoke("Gagal mengambil foto: ${t.message}")
        }
    }

    fun release() {
        stopRecording()
        try {
            NativeStabilizer.release()
            cameraProvider?.unbindAll()
        } catch (_: Throwable) {}
    }
}
