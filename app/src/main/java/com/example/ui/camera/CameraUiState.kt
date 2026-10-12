package com.example.ui.camera

import android.net.Uri
import com.example.camera.model.CaptureMode

data class CameraUiState(
    val captureMode: CaptureMode = CaptureMode.VIDEO,
    val currentZoomRatio: Float = 1.0f,
    val targetPreset: Float = 1.0f,
    val minZoomRatio: Float = 0.6f,
    val maxZoomRatio: Float = 10.0f,
    val isRecording: Boolean = false,
    val recordingDurationText: String = "00:00",
    val activeFps: Int = 60,
    val selectedBitrateMbps: Int = 50,
    val isOisLocked: Boolean = true,
    val isEisStabilizationEnabled: Boolean = true,
    val hasHardwareOis: Boolean = true,
    val hardwareLevelName: String = "FULL",
    val isStabilizationActive: Boolean = true,
    val isSettingsOpen: Boolean = false,
    val statusNotice: String? = null,
    val lastSavedUri: Uri? = null
)
