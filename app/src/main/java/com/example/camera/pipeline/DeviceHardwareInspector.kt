package com.example.camera.pipeline

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Range

data class CameraHardwareDetails(
    val hasOpticalStabilization: Boolean,
    val hasVideoStabilization: Boolean,
    val bestVideoStabilizationMode: Int,
    val hardwareLevelName: String,
    val supportedFpsRanges: List<Range<Int>>,
    val minZoomRatio: Float,
    val maxZoomRatio: Float,
    val hasUltraWideSensor: Boolean
)

object DeviceHardwareInspector {

    fun inspectBackCamera(context: Context): CameraHardwareDetails {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return fallbackDetails()

        return try {
            val cameraIds = manager.cameraIdList
            var mainBackId: String? = null
            var hasUltraWide = false

            for (id in cameraIds) {
                val chars = manager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    if (mainBackId == null) {
                        mainBackId = id
                    }
                    val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    if (focalLengths != null && focalLengths.any { it < 3.0f }) {
                        hasUltraWide = true
                    }
                }
            }

            val targetId = mainBackId ?: cameraIds.firstOrNull() ?: return fallbackDetails()
            val chars = manager.getCameraCharacteristics(targetId)

            val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            val hasOis = oisModes?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true

            val eisModes = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            val hasPreviewStab = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                eisModes?.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION) == true
            } else false

            val hasStandardEis = eisModes?.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true

            val bestEisMode = when {
                hasPreviewStab && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                hasStandardEis ->
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                else ->
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF
            }

            val hwLevel = chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            val hwLevelName = when (hwLevel) {
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                else -> "EXTERNAL"
            }

            val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList()
                ?: listOf(Range(30, 30))

            val zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            } else null

            val minZoom = zoomRange?.lower ?: 0.6f
            val maxZoom = zoomRange?.upper ?: 10.0f

            CameraHardwareDetails(
                hasOpticalStabilization = hasOis,
                hasVideoStabilization = hasPreviewStab || hasStandardEis,
                bestVideoStabilizationMode = bestEisMode,
                hardwareLevelName = hwLevelName,
                supportedFpsRanges = fpsRanges,
                minZoomRatio = minZoom,
                maxZoomRatio = maxZoom,
                hasUltraWideSensor = hasUltraWide
            )
        } catch (_: Throwable) {
            fallbackDetails()
        }
    }

    fun findBestFpsRange(targetFps: Int, supportedRanges: List<Range<Int>>): Range<Int> {
        if (supportedRanges.isEmpty()) return Range(30, 30)

        val exactMatch = supportedRanges.firstOrNull { it.lower == targetFps && it.upper == targetFps }
        if (exactMatch != null) return exactMatch

        val matchingUpper = supportedRanges.filter { it.upper == targetFps }.maxByOrNull { it.lower }
        if (matchingUpper != null) return matchingUpper

        val fallback30 = supportedRanges.firstOrNull { it.upper == 30 }
        if (fallback30 != null) return fallback30

        return supportedRanges.first()
    }

    private fun fallbackDetails(): CameraHardwareDetails {
        return CameraHardwareDetails(
            hasOpticalStabilization = true,
            hasVideoStabilization = true,
            bestVideoStabilizationMode = CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON,
            hardwareLevelName = "FULL",
            supportedFpsRanges = listOf(Range(30, 30)),
            minZoomRatio = 0.6f,
            maxZoomRatio = 10.0f,
            hasUltraWideSensor = true
        )
    }
}
