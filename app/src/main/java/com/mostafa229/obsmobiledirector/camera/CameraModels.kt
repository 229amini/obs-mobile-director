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
    val focalLengths: List<Float>,
    val zoomRange: String?,
    val videoStabilizationModes: List<String>,
    val opticalStabilizationModes: List<String>
)

