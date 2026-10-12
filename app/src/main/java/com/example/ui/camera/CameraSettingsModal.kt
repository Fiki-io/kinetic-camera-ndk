package com.example.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DeepCharcoal
import com.example.ui.theme.IndicatorActive
import com.example.ui.theme.IndicatorInactive
import com.example.ui.theme.MatteBlack
import com.example.ui.theme.NeutralGrey
import com.example.ui.theme.SoftOffWhite
import com.example.ui.theme.SurfaceSlate
import com.example.ui.theme.SurfaceSlateBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSettingsModal(
    uiState: CameraUiState,
    onBitrateSelected: (Int) -> Unit,
    onFpsSelected: (Int) -> Unit,
    onOisToggle: (Boolean) -> Unit,
    onEisToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepCharcoal,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Konfigurasi Pipeline",
                    color = SoftOffWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = NeutralGrey
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: Video Bitrate
            SettingSectionHeader(title = "Kecepatan Bit Video")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val bitrates = listOf(30, 50, 80)
                bitrates.forEach { bitrate ->
                    SelectablePill(
                        text = "${bitrate} Mbps",
                        isSelected = uiState.selectedBitrateMbps == bitrate,
                        onClick = { onBitrateSelected(bitrate) },
                        modifier = Modifier.weight(1f),
                        testTag = "bitrate_pill_${bitrate}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: Target Frame Pacing
            SettingSectionHeader(title = "Target Frame Rate")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val fpsList = listOf(30, 60)
                fpsList.forEach { fps ->
                    SelectablePill(
                        text = "${fps} FPS",
                        isSelected = uiState.activeFps == fps,
                        onClick = { onFpsSelected(fps) },
                        modifier = Modifier.weight(1f),
                        testTag = "fps_pill_${fps}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Hardware Stabilizer Switches
            SettingSectionHeader(title = "Stabilisasi Gambar")

            ToggleRow(
                title = "OIS Hardware Suspensi",
                subtitle = "Mengunci motor optik pada sensor utama 50MP",
                isChecked = uiState.isOisLocked,
                onCheckedChange = onOisToggle,
                testTag = "toggle_ois"
            )

            Spacer(modifier = Modifier.height(10.dp))

            ToggleRow(
                title = "EIS Video Kompensasi",
                subtitle = "Stabilisasi stream video Camera2",
                isChecked = uiState.isEisStabilizationEnabled,
                onCheckedChange = onEisToggle,
                testTag = "toggle_eis"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Hardware Telemetry Information
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceSlate)
                    .border(1.dp, SurfaceSlateBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "STATUS PERANGKAT",
                    color = NeutralGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                InfoItem(label = "Platform", value = "Infinix Note 60 5G")
                InfoItem(label = "Camera2 HAL", value = uiState.hardwareLevelName)
                InfoItem(label = "Sensor Fisik OIS", value = if (uiState.hasHardwareOis) "Terdeteksi" else "Tidak Aktif")
                InfoItem(label = "Lensa Ultrawide", value = "Maksimum 30 FPS")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingSectionHeader(title: String) {
    Text(
        text = title,
        color = NeutralGrey,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SelectablePill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) IndicatorActive else SurfaceSlate)
            .border(
                1.dp,
                if (isSelected) IndicatorActive else SurfaceSlateBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) MatteBlack else SoftOffWhite,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceSlate)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SoftOffWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = NeutralGrey,
                fontSize = 11.sp
            )
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MatteBlack,
                checkedTrackColor = IndicatorActive,
                uncheckedThumbColor = NeutralGrey,
                uncheckedTrackColor = SurfaceSlateBorder
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = NeutralGrey, fontSize = 12.sp)
        Text(
            text = value,
            color = SoftOffWhite,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}
