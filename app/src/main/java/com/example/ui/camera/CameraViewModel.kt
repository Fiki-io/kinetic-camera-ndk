package com.example.ui.camera

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.engine.KinematicZoomEngine
import com.example.camera.model.CameraConfiguration
import com.example.camera.model.CaptureMode
import com.example.camera.pipeline.DeviceHardwareInspector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var onApplyZoomRatio: ((Float) -> Unit)? = null

    val kinematicZoomEngine = KinematicZoomEngine(viewModelScope) { ratio ->
        _uiState.update { it.copy(currentZoomRatio = ratio) }
        onApplyZoomRatio?.invoke(ratio)
    }

    init {
        inspectHardware()
    }

    private fun inspectHardware() {
        val details = DeviceHardwareInspector.inspectBackCamera(getApplication())
        _uiState.update {
            it.copy(
                hasHardwareOis = details.hasOpticalStabilization,
                hardwareLevelName = details.hardwareLevelName,
                minZoomRatio = details.minZoomRatio,
                maxZoomRatio = details.maxZoomRatio
            )
        }
    }

    fun bindZoomExecutor(executor: (Float) -> Unit) {
        this.onApplyZoomRatio = executor
    }

    fun setZoomBoundaries(min: Float, max: Float) {
        _uiState.update { it.copy(minZoomRatio = min, maxZoomRatio = max) }
    }

    fun selectZoomPreset(targetPreset: Float) {
        val state = _uiState.value

        // Otomatis adaptasi FPS: Sensor ultrawide (< 1.0x) pada perangkat ini dibatasi 30 FPS
        val targetFps = if (targetPreset < 0.9f) {
            30
        } else {
            60
        }

        _uiState.update {
            it.copy(
                targetPreset = targetPreset,
                activeFps = targetFps
            )
        }

        kinematicZoomEngine.animateTo(
            targetRatio = targetPreset,
            minZoom = state.minZoomRatio,
            maxZoom = state.maxZoomRatio
        )
    }

    fun onPinchZoom(deltaRatio: Float) {
        val state = _uiState.value
        val newRatio = (state.currentZoomRatio * deltaRatio).coerceIn(state.minZoomRatio, state.maxZoomRatio)
        kinematicZoomEngine.setImmediate(newRatio, state.minZoomRatio, state.maxZoomRatio)
    }

    fun setCaptureMode(mode: CaptureMode) {
        _uiState.update { it.copy(captureMode = mode) }
    }

    fun updateBitrate(mbps: Int) {
        _uiState.update { it.copy(selectedBitrateMbps = mbps) }
    }

    fun updateFps(fps: Int) {
        _uiState.update { it.copy(activeFps = fps) }
    }

    fun toggleOis(enabled: Boolean) {
        _uiState.update { it.copy(isOisLocked = enabled) }
    }

    fun toggleEis(enabled: Boolean) {
        _uiState.update { it.copy(isEisStabilizationEnabled = enabled) }
    }

    fun toggleSettingsDialog(isOpen: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = isOpen) }
    }

    fun updateStabilizationConfirmation(ois: Boolean, eis: Boolean) {
        _uiState.update {
            it.copy(isStabilizationActive = ois || eis)
        }
    }

    fun updateRecordingStatus(isRecording: Boolean, durationNanos: Long) {
        val totalSeconds = durationNanos / 1_000_000_000L
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val formatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)

        _uiState.update {
            it.copy(
                isRecording = isRecording,
                recordingDurationText = formatted
            )
        }
    }

    fun onFileSaved(uri: Uri, isVideo: Boolean) {
        val label = if (isVideo) "Video tersimpan" else "Foto tersimpan"
        _uiState.update {
            it.copy(
                lastSavedUri = uri,
                statusNotice = label
            )
        }
    }

    fun postNotice(message: String) {
        _uiState.update { it.copy(statusNotice = message) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(statusNotice = null) }
    }

    fun getCurrentConfiguration(): CameraConfiguration {
        val s = _uiState.value
        return CameraConfiguration(
            captureMode = s.captureMode,
            targetFps = s.activeFps,
            videoBitrateMbps = s.selectedBitrateMbps,
            isOisLocked = s.isOisLocked,
            isEisStabilizationEnabled = s.isEisStabilizationEnabled
        )
    }

    override fun onCleared() {
        super.onCleared()
        kinematicZoomEngine.cancel()
    }
}
