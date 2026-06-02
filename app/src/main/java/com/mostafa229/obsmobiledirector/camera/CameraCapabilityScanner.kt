package com.mostafa229.obsmobiledirector.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

class CameraCapabilityScanner(context: Context) {
    private val cameraManager = context.getSystemService(CameraManager::class.java)

    fun scan(): CameraReport {
        val cameras = cameraManager.cameraIdList.map { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            CameraDescriptor(
                id = id,
                facing = characteristics.facingLabel(),
                isLogicalMultiCamera = characteristics.isLogicalMultiCamera(),
                physicalCameraIds = characteristics.physicalCameraIds.toList().sorted(),
                focalLengths = characteristics
                    .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.toList()
                    .orEmpty(),
                zoomRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.toString()
                } else {
                    null
                },
                videoStabilizationModes = characteristics.videoStabilizationLabels(),
                opticalStabilizationModes = characteristics.opticalStabilizationLabels()
            )
        }

        val concurrentSets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            cameraManager.concurrentCameraIds.map { it.toList().sorted() }
        } else {
            emptyList()
        }

        return CameraReport(
            cameras = cameras,
            concurrentCameraSets = concurrentSets
        )
    }

    private fun CameraCharacteristics.facingLabel(): String {
        return when (get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
            CameraCharacteristics.LENS_FACING_BACK -> "BACK"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }
    }

    private fun CameraCharacteristics.isLogicalMultiCamera(): Boolean {
        val capabilities = get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES).orEmpty()
        return capabilities.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
        )
    }

    private fun CameraCharacteristics.videoStabilizationLabels(): List<String> {
        return get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            ?.map { mode ->
                when (mode) {
                    CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_OFF -> "OFF"
                    CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON -> "ON"
                    CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION -> {
                        "PREVIEW"
                    }
                    else -> "UNKNOWN($mode)"
                }
            }
            .orEmpty()
    }

    private fun CameraCharacteristics.opticalStabilizationLabels(): List<String> {
        return get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.map { mode ->
                when (mode) {
                    CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_OFF -> "OFF"
                    CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON -> "ON"
                    else -> "UNKNOWN($mode)"
                }
            }
            .orEmpty()
    }
}

