package com.mostafa229.obsmobiledirector.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mostafa229.obsmobiledirector.ui.DirectorMode

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraPreview(
    mode: DirectorMode,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val lensFacing = when (mode) {
        DirectorMode.FrontFull,
        DirectorMode.FrontWithBackPip -> CameraSelector.LENS_FACING_FRONT
        DirectorMode.BackFull,
        DirectorMode.BackWithFrontPip,
        DirectorMode.SideBySide -> CameraSelector.LENS_FACING_BACK
    }
    val cameraSelector = remember(lensFacing) {
        CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
    }
    val cameraProviderFuture = remember(context) {
        ProcessCameraProvider.getInstance(context)
    }
    val stabilizationMode = remember(context, lensFacing) {
        preferredVideoStabilizationMode(context, lensFacing)
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
                    val cameraProvider = cameraProviderFuture.get()
                    val previewBuilder = Preview.Builder()
                    if (stabilizationMode != null) {
                        Camera2Interop.Extender(previewBuilder).setCaptureRequestOption(
                            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                            stabilizationMode
                        )
                    }
                    val preview = previewBuilder.build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
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
    lensFacing: Int
): Int? {
    val cameraManager = context.getSystemService(CameraManager::class.java)
    val supportedModes = cameraManager.cameraIdList
        .map { id -> cameraManager.getCameraCharacteristics(id) }
        .filter { characteristics ->
            characteristics.get(CameraCharacteristics.LENS_FACING) == lensFacing
        }
        .flatMap { characteristics ->
            characteristics
                .get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                ?.toList()
                .orEmpty()
        }
        .toSet()

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
        else -> null
    }
}
