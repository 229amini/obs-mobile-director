package com.mostafa229.obsmobiledirector.camera

data class CameraReport(
    val cameras: List<CameraDescriptor>,
    val concurrentCameraSets: List<List<String>>
)

data class CameraDescriptor(
    val id: String,
    val facing: String,
    val isLogicalMultiCamera: Boolean,
    val physicalCameraIds: List<String>,
    val physicalLenses: List<PhysicalLensDescriptor>,
    val focalLengths: List<Float>,
    val zoomRange: String?,
    val zoomMin: Float?,
    val zoomMax: Float?,
    val videoStabilizationModes: List<String>,
    val opticalStabilizationModes: List<String>
)

data class PhysicalLensDescriptor(
    val id: String,
    val focalLengths: List<Float>,
    val lensRole: String
)
