package com.example.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.model.CaptureMode
import com.example.ui.theme.DeepCharcoal
import com.example.ui.theme.IndicatorActive
import com.example.ui.theme.IndicatorInactive
import com.example.ui.theme.MatteBlack
import com.example.ui.theme.MutedSlate
import com.example.ui.theme.NeutralGrey
import com.example.ui.theme.RecordingIndicator
import com.example.ui.theme.SoftOffWhite
import com.example.ui.theme.SurfaceSlate
import com.example.ui.theme.SurfaceSlateBorder

@Composable
fun TopTelemetryHeader(
    fps: Int,
    bitrateMbps: Int,
    isOisLocked: Boolean,
    isEisLocked: Boolean,
    isRecording: Boolean,
    durationText: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status metrics capsule
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceSlate.copy(alpha = 0.85f))
                .border(1.dp, SurfaceSlateBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // FPS Indicator
            Text(
                text = "${fps} FPS",
                color = SoftOffWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("metric_fps")
            )

            Box(
                modifier = Modifier
                    .size(3.dp)
                    .background(MutedSlate, CircleShape)
            )

            // Bitrate Indicator
            Text(
                text = "${bitrateMbps} Mbps",
                color = NeutralGrey,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("metric_bitrate")
            )

            if (isOisLocked) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .background(MutedSlate, CircleShape)
                )

                Text(
                    text = "OIS",
                    color = SoftOffWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("metric_ois")
                )
            }

            if (isEisLocked) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .background(MutedSlate, CircleShape)
                )

                Text(
                    text = "EIS",
                    color = SoftOffWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("metric_eis")
                )
            }
        }

        // Recording timer or Standby badge
        if (isRecording) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceSlate.copy(alpha = 0.95f))
                    .border(1.dp, RecordingIndicator, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(RecordingIndicator, CircleShape)
                )
                Text(
                    text = durationText,
                    color = SoftOffWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("recording_timer")
                )
            }
        }

        // Settings Button
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SurfaceSlate.copy(alpha = 0.85f))
                .border(1.dp, SurfaceSlateBorder, CircleShape)
                .testTag("settings_button")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Pengaturan",
                tint = SoftOffWhite,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ZoomPresetSelector(
    currentRatio: Float,
    targetPreset: Float,
    onSelectPreset: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val presets = listOf(0.6f, 1.0f, 2.0f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceSlate.copy(alpha = 0.85f))
            .border(1.dp, SurfaceSlateBorder, RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { preset ->
            val isSelected = kotlin.math.abs(targetPreset - preset) < 0.15f
            val label = when (preset) {
                0.6f -> ".6"
                1.0f -> "1x"
                2.0f -> "2x"
                else -> "${preset}x"
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) IndicatorActive else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onSelectPreset(preset)
                    }
                    .testTag("zoom_preset_${preset}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) MatteBlack else NeutralGrey,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun BottomCaptureControls(
    captureMode: CaptureMode,
    isRecording: Boolean,
    onShutterClick: () -> Unit,
    onModeToggle: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Mode Switcher
        Row(
            modifier = Modifier
                .padding(bottom = 18.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceSlate.copy(alpha = 0.8f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeItem(
                title = "Video",
                isSelected = captureMode == CaptureMode.VIDEO,
                onClick = { onModeToggle(CaptureMode.VIDEO) },
                testTag = "mode_video"
            )
            ModeItem(
                title = "Foto",
                isSelected = captureMode == CaptureMode.PHOTO,
                onClick = { onModeToggle(CaptureMode.PHOTO) },
                testTag = "mode_photo"
            )
        }

        // Shutter Trigger Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode icon indicator
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SurfaceSlate.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (captureMode == CaptureMode.VIDEO) Icons.Default.Videocam else Icons.Default.CameraAlt,
                    contentDescription = "Mode aktif",
                    tint = NeutralGrey,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Primary Shutter Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(3.dp, SoftOffWhite, CircleShape)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) SurfaceSlate else SoftOffWhite)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onShutterClick()
                    }
                    .testTag("shutter_button"),
                contentAlignment = Alignment.Center
            ) {
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SoftOffWhite)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(SoftOffWhite)
                    )
                }
            }

            // Balancing placeholder for layout symmetry
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun ModeItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) IndicatorActive else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) MatteBlack else NeutralGrey,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
