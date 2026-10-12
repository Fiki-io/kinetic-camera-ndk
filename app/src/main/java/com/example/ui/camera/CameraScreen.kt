package com.example.ui.camera

import android.net.Uri
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.camera.model.CaptureMode
import com.example.camera.pipeline.CameraPipelineManager
import com.example.ui.theme.MatteBlack
import com.example.ui.theme.SoftOffWhite
import com.example.ui.theme.SurfaceSlate

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    hasAudioPermission: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var isPipelineInitialized by remember { mutableStateOf(false) }

    val pipelineManager = remember {
        CameraPipelineManager(context, lifecycleOwner).apply {
            onRecordingStatusChanged = { isRec, durationNanos ->
                viewModel.updateRecordingStatus(isRec, durationNanos)
            }
            onStabilizationStatusConfirmed = { ois, eis ->
                viewModel.updateStabilizationConfirmation(ois, eis)
            }
            onVideoSaved = { uri ->
                viewModel.onFileSaved(uri, isVideo = true)
            }
            onPhotoSaved = { uri ->
                viewModel.onFileSaved(uri, isVideo = false)
            }
            onError = { err ->
                viewModel.postNotice(err)
            }
        }
    }

    // Hubungkan zoom executor dari ViewModel ke Pipeline
    DisposableEffect(pipelineManager) {
        viewModel.bindZoomExecutor { ratio ->
            pipelineManager.setZoomRatio(ratio)
        }
        onDispose {
            pipelineManager.release()
        }
    }

    // Rekonfigurasi pipeline hanya saat setting berubah setelah inisialisasi awal
    LaunchedEffect(uiState.activeFps, uiState.isOisLocked, uiState.isEisStabilizationEnabled) {
        if (!isPipelineInitialized) return@LaunchedEffect
        val pView = previewViewRef ?: return@LaunchedEffect
        pipelineManager.reconfigure(
            previewView = pView,
            config = viewModel.getCurrentConfiguration(),
            onInitialized = { minZoom, maxZoom ->
                viewModel.setZoomBoundaries(minZoom, maxZoom)
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MatteBlack)
            .testTag("camera_screen_container")
    ) {
        // Viewfinder Murni Natif 1:1 (Tanpa pergeseran semu dan bebas distorsi jello)
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    previewViewRef = this

                    pipelineManager.initialize(
                        previewView = this,
                        config = viewModel.getCurrentConfiguration(),
                        onInitialized = { minZoom, maxZoom ->
                            viewModel.setZoomBoundaries(minZoom, maxZoom)
                            isPipelineInitialized = true
                        }
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        viewModel.onPinchZoom(zoom)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        previewViewRef?.let { pView ->
                            pipelineManager.triggerFocusAndMetering(offset.x, offset.y, pView)
                        }
                    }
                }
                .testTag("camera_viewfinder")
        )

        // Overlay UI Controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Status Telemetri Hardware Murni
            TopTelemetryHeader(
                fps = uiState.activeFps,
                bitrateMbps = uiState.selectedBitrateMbps,
                isOisLocked = uiState.isOisLocked,
                isEisLocked = uiState.isEisStabilizationEnabled,
                isRecording = uiState.isRecording,
                durationText = uiState.recordingDurationText,
                onOpenSettings = { viewModel.toggleSettingsDialog(true) }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Notice / Status banner
            AnimatedVisibility(
                visible = uiState.statusNotice != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                uiState.statusNotice?.let { notice ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceSlate.copy(alpha = 0.95f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = notice,
                            color = SoftOffWhite,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Zoom Preset Pill Selector
            ZoomPresetSelector(
                currentRatio = uiState.currentZoomRatio,
                targetPreset = uiState.targetPreset,
                onSelectPreset = { preset ->
                    viewModel.selectZoomPreset(preset)
                },
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Bottom Shutter & Mode Switcher
            BottomCaptureControls(
                captureMode = uiState.captureMode,
                isRecording = uiState.isRecording,
                onShutterClick = {
                    if (uiState.captureMode == CaptureMode.VIDEO) {
                        if (uiState.isRecording) {
                            pipelineManager.stopRecording()
                        } else {
                            pipelineManager.startRecording(hasAudioPermission)
                        }
                    } else {
                        pipelineManager.capturePhoto()
                    }
                },
                onModeToggle = { mode ->
                    viewModel.setCaptureMode(mode)
                },
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        // Settings Bottom Sheet
        if (uiState.isSettingsOpen) {
            CameraSettingsModal(
                uiState = uiState,
                onBitrateSelected = { bitrate ->
                    viewModel.updateBitrate(bitrate)
                },
                onFpsSelected = { fps ->
                    viewModel.updateFps(fps)
                },
                onOisToggle = { ois ->
                    viewModel.toggleOis(ois)
                },
                onEisToggle = { eis ->
                    viewModel.toggleEis(eis)
                },
                onDismiss = {
                    viewModel.toggleSettingsDialog(false)
                }
            )
        }
    }
}
