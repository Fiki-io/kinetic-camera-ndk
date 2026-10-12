package com.example.camera.model

enum class CaptureMode {
    VIDEO,
    PHOTO
}

data class CameraConfiguration(
    val captureMode: CaptureMode = CaptureMode.VIDEO,
    val targetFps: Int = 60,
    val videoBitrateMbps: Int = 50,
    val audioBitrateKbps: Int = 320,
    val isOisLocked: Boolean = true,
    val isEisStabilizationEnabled: Boolean = true
)
