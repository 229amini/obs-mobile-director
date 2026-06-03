package com.mostafa229.obsmobiledirector.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraPreview(
    cameraId: String?,
    stabilizationEnabled: Boolean,
    modifier: Modifier = Modifier,
    onError: (Throwable) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnError = rememberUpdatedState(onError)
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val cameraSelector = remember(cameraId) {
        if (cameraId == null) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->
                    cameraInfos.filter { cameraInfo ->
                        Camera2CameraInfo.from(cameraInfo).cameraId == cameraId
                    }
                }
                .build()
        }
    }
    val cameraProviderFuture = remember(context) {
        ProcessCameraProvider.getInstance(context)
    }
    val stabilizationMode = remember(context, cameraId, stabilizationEnabled) {
        if (stabilizationEnabled && cameraId != null) {
            preferredVideoStabilizationMode(context, cameraId)
        } else {
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
        },
        update = { previewView ->
            cameraProviderFuture.addListener(
                {
                    runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        val previewBuilder = Preview.Builder()
                        Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(
                            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                            stabilizationMode
                        )
                        val preview = previewBuilder.build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                    }.onFailure { error ->
                        currentOnError.value(error)
                    }
                },
                executor
            )
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            if (cameraProviderFuture.isDone) {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }
}

private fun preferredVideoStabilizationMode(
    context: Context,
    cameraId: String
): Int {
    val cameraManager = context.getSystemService(CameraManager::class.java)
    val supportedModes = cameraManager
        .getCameraCharacteristics(cameraId)
        .get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
        ?.toSet()
        .orEmpty()

    val previewStabilization = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
    } else {
        null
    }

    return when {
        previewStabilization != null && supportedModes.contains(previewStabilization) -> {
            previewStabilization
        }
        supportedModes.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON) -> {
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
        }
        else -> CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
    }
}
