package com.mostafa229.obsmobiledirector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.mostafa229.obsmobiledirector.camera.CameraPreview
import com.mostafa229.obsmobiledirector.camera.CameraCapabilityScanner
import com.mostafa229.obsmobiledirector.camera.CameraDescriptor
import com.mostafa229.obsmobiledirector.camera.CameraReport
import com.mostafa229.obsmobiledirector.stream.AntibandingMode
import com.mostafa229.obsmobiledirector.stream.StreamingPipeline
import com.mostafa229.obsmobiledirector.stream.StreamTarget
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF66D9EF),
                    secondary = Color(0xFFFFD166),
                    background = Color(0xFF050608),
                    surface = Color(0xFF111318),
                    onSurface = Color(0xFFF4F6F8)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen()
                }
            }
        }
    }
}

@Composable
private fun AppScreen() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var micEnabled by remember { mutableStateOf(true) }
    var selectedCameraId by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<CameraReport?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var stabilizationEnabled by remember { mutableStateOf(true) }
    var antibanding by remember { mutableStateOf(AntibandingMode.Auto) }
    var pipEnabled by remember { mutableStateOf(false) }
    var cameraSwitching by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var host by remember { mutableStateOf("192.168.1.9") }
    var port by remember { mutableStateOf("9001") }
    var latencyMs by remember { mutableStateOf(StreamTarget.DEFAULT_LATENCY_MILLIS.toString()) }
    var streamRunning by remember { mutableStateOf(false) }
    var streamStatus by remember {
        mutableStateOf("Preview active. Configure OBS as an SRT listener, then start output.")
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val streamingPipeline = remember {
        StreamingPipeline { message, streaming ->
            mainHandler.post {
                streamStatus = message
                if (streaming != null) {
                    streamRunning = streaming
                }
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { result ->
            hasCameraPermission = result[Manifest.permission.CAMERA] ?: hasCameraPermission
            hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
        }
    )

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            report = CameraCapabilityScanner(context).scan()
        }
    }

    // On an in-place update the camera grant carries over, so the permission screen is
    // skipped and the mic would never be requested. Ask for it proactively whenever the
    // camera is allowed but the mic isn't, so audio works without a reinstall.
    LaunchedEffect(hasCameraPermission, hasAudioPermission) {
        if (hasCameraPermission && !hasAudioPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    // Permission controls whether an audio track exists; the mic toggle mutes/unmutes it
    // live. Kept separate so toggling during a stream mutes instantly instead of waiting
    // for the next Go Live.
    LaunchedEffect(hasAudioPermission) {
        streamingPipeline.setAudioPermitted(hasAudioPermission)
    }
    LaunchedEffect(micEnabled) {
        streamingPipeline.setMicOn(micEnabled)
    }

    LaunchedEffect(report) {
        val cameras = report?.cameras.orEmpty()
        if (cameras.isNotEmpty() && cameras.none { it.id == selectedCameraId }) {
            selectedCameraId = cameras.firstOrNull { it.facing == "BACK" }?.id ?: cameras.first().id
        }
    }

    LaunchedEffect(host, port, latencyMs) {
        if (streamRunning) {
            streamingPipeline.stop()
            streamRunning = false
            streamStatus = "SRT settings changed. Start output again with the new target."
        }
    }

    LaunchedEffect(selectedCameraId) {
        cameraSwitching = selectedCameraId != null
        zoomRatio = 1f
        runCatching {
            streamingPipeline.selectCamera(selectedCameraId)
            streamingPipeline.setZoomRatio(1f)
        }.onFailure { error ->
            streamStatus = "Unable to switch camera: ${error.message ?: "camera API error"}"
        }
        delay(350)
        cameraSwitching = false
    }

    LaunchedEffect(stabilizationEnabled) {
        runCatching {
            streamingPipeline.setStabilization(stabilizationEnabled)
        }.onFailure { error ->
            streamStatus = "Unable to change stabilization: ${error.message ?: "camera API error"}"
        }
    }

    LaunchedEffect(antibanding) {
        runCatching {
            streamingPipeline.setAntibanding(antibanding)
        }.onFailure { error ->
            streamStatus = "Unable to set anti-flicker: ${error.message ?: "camera API error"}"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            streamingPipeline.release()
        }
    }

    if (!hasCameraPermission) {
        PermissionScreen(onGrantPermission = {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        })
        return
    }

    val cameras = report?.cameras.orEmpty()
    val selectedCameraIdValue = selectedCameraId
    val selectedCamera = cameras.firstOrNull { it.id == selectedCameraIdValue }
    val zoomMin = selectedCamera?.zoomMin ?: 1f
    val zoomMax = selectedCamera?.zoomMax ?: 1f
    val concurrentCameraSets = report?.concurrentCameraSets.orEmpty()
    val pipSelectableCameraIds = if (pipEnabled) {
        cameras.filter { camera ->
            selectSecondaryCamera(
                cameras = cameras,
                selectedCameraId = camera.id,
                selectedFacing = camera.facing,
                concurrentCameraSets = concurrentCameraSets
            ) != null
        }.map { it.id }.toSet()
    } else {
        cameras.map { it.id }.toSet()
    }
    val secondaryCamera = if (pipEnabled && selectedCameraIdValue != null) {
        selectSecondaryCamera(
            cameras = cameras,
            selectedCameraId = selectedCameraIdValue,
            selectedFacing = selectedCamera?.facing,
            concurrentCameraSets = concurrentCameraSets
        )
    } else {
        null
    }
    val parsedPort = port.toIntOrNull()
    val parsedLatency = latencyMs.toIntOrNull()
        ?.coerceIn(StreamTarget.MIN_LATENCY_MILLIS, StreamTarget.MAX_LATENCY_MILLIS)
        ?: StreamTarget.DEFAULT_LATENCY_MILLIS
    val streamTarget = StreamTarget(
        host = host.trim(),
        port = parsedPort ?: 9001,
        latencyMillis = parsedLatency
    )

    // Shared zoom entry point used by the pinch gesture, the vertical slider, and the
    // preset chips so they all stay in sync and report status the same way.
    val applyZoom: (Float, Boolean) -> Unit = applyZoom@{ nextZoom, preferOptical ->
        val constrainedZoom = nextZoom.coerceIn(zoomMin, zoomMax)
        if (constrainedZoom == zoomRatio) return@applyZoom
        zoomRatio = constrainedZoom
        runCatching {
            streamingPipeline.setZoomRatio(zoom = constrainedZoom, preferOptical = preferOptical)
        }.onSuccess {
            streamStatus = if (preferOptical && constrainedZoom >= 3f) {
                "Zoom ${constrainedZoom.formatZoom()}x — tele lens used when the device exposes it."
            } else {
                "Zoom ${constrainedZoom.formatZoom()}x"
            }
        }.onFailure { error ->
            streamStatus = "Unable to zoom: ${error.message ?: "camera API error"}"
        }
    }
    // rememberUpdatedState lets the long-lived pinch gesture detector read the latest
    // zoom without being torn down and relaunched on every zoom change.
    val zoomRatioState = rememberUpdatedState(zoomRatio)
    val canZoom = zoomMax > zoomMin

    Box(modifier = Modifier.fillMaxSize()) {
        StreamingPreview(
            streamingPipeline = streamingPipeline,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (canZoom) {
                        Modifier.pointerInput(zoomMin, zoomMax) {
                            detectTransformGestures { _, _, gestureZoom, _ ->
                                if (gestureZoom != 1f) {
                                    applyZoom(zoomRatioState.value * gestureZoom, false)
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        )

        TopStatusOverlay(streamRunning = streamRunning)

        secondaryCamera?.let { camera ->
            SecondaryCameraPip(
                camera = camera,
                stabilizationEnabled = stabilizationEnabled,
                onBindError = { error ->
                    pipEnabled = false
                    streamStatus = "PiP camera unavailable: ${error.message ?: "camera API error"}"
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 72.dp, bottom = 92.dp)
                    .fillMaxWidth(0.32f)
                    .aspectRatio(16f / 9f)
            )
        }

        if (canZoom) {
            // Reserve the top status zone and the bottom bar zone so the slider always
            // sits in the clear middle band and never slides under the control bar.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 10.dp, top = 56.dp, bottom = 96.dp),
                contentAlignment = Alignment.Center
            ) {
                VerticalZoomSlider(
                    zoomRatio = zoomRatio,
                    zoomMin = zoomMin,
                    zoomMax = zoomMax,
                    onZoomChanged = { applyZoom(it, false) }
                )
            }
        }

        CameraHudOverlay(
            cameras = cameras,
            selectedCameraId = selectedCameraId,
            stabilizationEnabled = stabilizationEnabled,
            antibanding = antibanding,
            pipEnabled = pipEnabled,
            micEnabled = micEnabled,
            audioAvailable = hasAudioPermission,
            streamRunning = streamRunning,
            streamStatus = streamStatus,
            targetLabel = "${host.trim().ifBlank { "set IP" }}:$port",
            isCameraEnabled = { camera ->
                !cameraSwitching && camera.id in pipSelectableCameraIds
            },
            onCameraSelected = { camera ->
                if (cameraSwitching) {
                    streamStatus = "Camera switch is still settling."
                } else if (camera.id !in pipSelectableCameraIds) {
                    streamStatus = "That camera is disabled while PiP is on because it has no supported secondary pair."
                } else {
                    selectedCameraId = camera.id
                    streamStatus = "Camera ${camera.displayName()} selected for preview and SRT output."
                }
            },
            onToggleStabilization = {
                val next = !stabilizationEnabled
                stabilizationEnabled = next
                val state = if (next) "enabled" else "disabled"
                streamStatus = "Hardware stabilization $state for the selected camera when supported."
            },
            onAntibandingSelected = { mode ->
                antibanding = mode
                streamStatus = "Anti-flicker set to ${mode.label}."
            },
            onTogglePip = {
                val next = !pipEnabled
                val hasSupportedSecondary = selectedCameraIdValue != null &&
                    selectSecondaryCamera(
                        cameras = cameras,
                        selectedCameraId = selectedCameraIdValue,
                        selectedFacing = selectedCamera?.facing,
                        concurrentCameraSets = concurrentCameraSets
                    ) != null
                pipEnabled = next && hasSupportedSecondary
                streamStatus = if (next && hasSupportedSecondary) {
                    "PiP overlay enabled. The next camera is shown as a rounded 1/4 preview."
                } else if (next) {
                    "PiP needs a supported concurrent camera pair on this device."
                } else {
                    "PiP overlay disabled."
                }
            },
            onToggleSettings = { showSettings = !showSettings },
            onToggleMic = {
                if (!hasAudioPermission) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    streamStatus = "Grant microphone permission to send audio to OBS."
                } else {
                    micEnabled = !micEnabled
                    // While streaming, the pipeline posts the live mute/unmute status.
                    if (!streamRunning) {
                        streamStatus = if (micEnabled) "Microphone on." else "Microphone off."
                    }
                }
            },
            onToggleReport = {
                val next = !showReport
                showReport = next
                if (next) {
                    report = CameraCapabilityScanner(context).scan()
                }
            },
            onToggleStream = {
                if (streamRunning) {
                    streamingPipeline.stop()
                    streamRunning = false
                    streamStatus = "SRT output stopped. OBS can remain listening on ${streamTarget.listenerUri}."
                } else if (selectedCameraId == null) {
                    streamStatus = "Camera scan is still loading. Wait for the camera buttons, then start SRT."
                } else if (streamTarget.host.isBlank()) {
                    streamStatus = "Enter the OBS PC LAN IP before starting SRT output."
                } else if (parsedPort == null || parsedPort !in 1..65535) {
                    streamStatus = "Enter a valid UDP port from 1 to 65535."
                } else {
                    runCatching {
                        streamingPipeline.start(streamTarget)
                    }.onSuccess {
                        streamRunning = true
                        streamStatus = "SRT target armed: ${streamTarget.uri}. In OBS, use Media Source URL ${streamTarget.listenerUri}."
                    }.onFailure { error ->
                        streamRunning = false
                        streamStatus = "Unable to start SRT: ${error.message ?: "unknown encoder error"}"
                    }
                }
            }
        )

        if (showSettings) {
            SettingsPanel(
                host = host,
                port = port,
                latencyMs = latencyMs,
                streamTarget = streamTarget,
                onHostChanged = { host = it },
                onPortChanged = { port = it },
                onLatencyChanged = { latencyMs = it },
                onClose = { showSettings = false },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .widthIn(min = 320.dp, max = 420.dp)
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .padding(12.dp)
            )
        }

        if (showReport) {
            CameraReportPanel(
                report = report,
                onRefresh = { report = CameraCapabilityScanner(context).scan() },
                onClose = { showReport = false },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .widthIn(min = 360.dp, max = 520.dp)
                    .navigationBarsPadding()
                    .statusBarsPadding()
                    .padding(12.dp)
            )
        }
    }
}

@Composable
private fun StreamingPreview(
    streamingPipeline: StreamingPipeline,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            OpenGlView(viewContext).also { openGlView ->
                streamingPipeline.attachView(openGlView)
            }
        },
        update = { openGlView ->
            streamingPipeline.attachView(openGlView)
        }
    )
}

@Composable
private fun SecondaryCameraPip(
    camera: CameraDescriptor,
    stabilizationEnabled: Boolean,
    onBindError: (Throwable) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier,
        color = Color.Black,
        shape = shape,
        shadowElevation = 14.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color.Black)
                .border(width = 2.dp, color = Color(0x66FFFFFF), shape = shape)
        ) {
            CameraPreview(
                cameraId = camera.id,
                stabilizationEnabled = stabilizationEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
                onError = onBindError
            )
        }
    }
}

/**
 * Camera-style vertical zoom slider. Top of the track is max zoom, bottom is min.
 * Tap to jump, drag the thumb to scrub. Reports the absolute zoom ratio.
 */
@Composable
private fun VerticalZoomSlider(
    zoomRatio: Float,
    zoomMin: Float,
    zoomMax: Float,
    onZoomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val range = (zoomMax - zoomMin).coerceAtLeast(0.0001f)
    val fraction = ((zoomRatio - zoomMin) / range).coerceIn(0f, 1f)
    Column(
        // Fill the reserved vertical band (capped) so the track scales to the screen
        // and never overlaps the bottom control bar.
        modifier = modifier
            .heightIn(max = 280.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(color = Color(0xB0000000), shape = RoundedCornerShape(999.dp)) {
            Text(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                text = "${zoomRatio.formatZoom()}x",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF66D9EF)
            )
        }
        Canvas(
            modifier = Modifier
                .width(40.dp)
                .weight(1f)
                .pointerInput(zoomMin, zoomMax) {
                    detectTapGestures { offset ->
                        val f = (1f - offset.y / size.height).coerceIn(0f, 1f)
                        onZoomChanged(zoomMin + f * range)
                    }
                }
                .pointerInput(zoomMin, zoomMax) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val f = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        onZoomChanged(zoomMin + f * range)
                    }
                }
        ) {
            val cx = size.width / 2f
            val stroke = 6.dp.toPx()
            val thumbY = (1f - fraction) * size.height
            drawLine(
                color = Color(0x44FFFFFF),
                start = Offset(cx, 0f),
                end = Offset(cx, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFF66D9EF),
                start = Offset(cx, thumbY),
                end = Offset(cx, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawCircle(color = Color.White, radius = 9.dp.toPx(), center = Offset(cx, thumbY))
        }
    }
}

@Composable
private fun CompactSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFDCE4EA)
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = enabled
        )
    }
}

@Composable
private fun PermissionScreen(onGrantPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "OBS Mobile Director",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Camera permission is required for live preview and device capability checks.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onGrantPermission) {
            Text("Grant camera permission")
        }
    }
}

@Composable
private fun TopStatusOverlay(streamRunning: Boolean) {
    // Minimal: just a small streaming-state pill in the top-right corner. The old
    // "OBS Mobile Director" title block was removed to keep the preview unobstructed.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(14.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusPill(text = if (streamRunning) "SRT" else "LOCAL")
    }
}

@Composable
private fun CameraHudOverlay(
    cameras: List<CameraDescriptor>,
    selectedCameraId: String?,
    stabilizationEnabled: Boolean,
    antibanding: AntibandingMode,
    pipEnabled: Boolean,
    micEnabled: Boolean,
    audioAvailable: Boolean,
    streamRunning: Boolean,
    streamStatus: String,
    targetLabel: String,
    isCameraEnabled: (CameraDescriptor) -> Boolean,
    onCameraSelected: (CameraDescriptor) -> Unit,
    onToggleStabilization: () -> Unit,
    onAntibandingSelected: (AntibandingMode) -> Unit,
    onTogglePip: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleSettings: () -> Unit,
    onToggleReport: () -> Unit,
    onToggleStream: () -> Unit
) {
    var optionsOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tap-anywhere-outside scrim so the panel is always easy to dismiss.
        AnimatedVisibility(
            visible = optionsOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .pointerInput(Unit) {
                        detectTapGestures { optionsOpen = false }
                    }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            AnimatedVisibility(
                visible = optionsOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                OptionsCard(
                    cameras = cameras,
                    selectedCameraId = selectedCameraId,
                    stabilizationEnabled = stabilizationEnabled,
                    antibanding = antibanding,
                    pipEnabled = pipEnabled,
                    micEnabled = micEnabled,
                    audioAvailable = audioAvailable,
                    isCameraEnabled = isCameraEnabled,
                    onCameraSelected = onCameraSelected,
                    onToggleStabilization = onToggleStabilization,
                    onAntibandingSelected = onAntibandingSelected,
                    onTogglePip = onTogglePip,
                    onToggleMic = onToggleMic,
                    onOpenSettings = onToggleSettings,
                    onOpenReport = onToggleReport,
                    onClose = { optionsOpen = false }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Minimal bottom bar: status + camera flip + options (gear) + the one primary
            // Go Live / Stop action. Everything else lives behind the gear.
            Surface(
                color = Color(0xB8000000),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Always-visible SRT target. Tap to open SRT setup and change the OBS
                    // PC IP or the port (give each phone its own port for multi-cam).
                    Surface(
                        color = Color(0x3366D9EF),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable { onToggleSettings() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SettingsEthernet,
                                contentDescription = "SRT setup",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF66D9EF)
                            )
                            Text(
                                text = targetLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF66D9EF),
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 2.dp),
                        text = streamStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC8D0D8),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    FilledTonalIconButton(
                        onClick = {
                            flipCamera(cameras, selectedCameraId, isCameraEnabled, onCameraSelected)
                        },
                        enabled = cameras.size > 1
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cameraswitch,
                            contentDescription = "Switch camera"
                        )
                    }
                    FilledTonalIconButton(onClick = { optionsOpen = !optionsOpen }) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "More options"
                        )
                    }
                    StreamButton(streamRunning = streamRunning, onClick = onToggleStream)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsCard(
    cameras: List<CameraDescriptor>,
    selectedCameraId: String?,
    stabilizationEnabled: Boolean,
    antibanding: AntibandingMode,
    pipEnabled: Boolean,
    micEnabled: Boolean,
    audioAvailable: Boolean,
    isCameraEnabled: (CameraDescriptor) -> Boolean,
    onCameraSelected: (CameraDescriptor) -> Unit,
    onToggleStabilization: () -> Unit,
    onAntibandingSelected: (AntibandingMode) -> Unit,
    onTogglePip: () -> Unit,
    onToggleMic: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReport: () -> Unit,
    onClose: () -> Unit
) {
    // Cap the panel to the screen so the (now taller) content scrolls instead of
    // overflowing off the top of a short landscape screen.
    val maxCardHeight = (LocalConfiguration.current.screenHeightDp - 100).coerceAtLeast(180).dp
    Surface(
        color = Color(0xF20E1014),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 380.dp)
                .heightIn(max = maxCardHeight)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Hide options"
                    )
                }
            }

            SectionLabel("Camera")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                cameras.forEach { camera ->
                    FilterChip(
                        selected = camera.id == selectedCameraId,
                        onClick = { onCameraSelected(camera) },
                        enabled = isCameraEnabled(camera),
                        label = { Text(camera.shortName()) }
                    )
                }
            }

            SectionLabel("Anti-flicker (lights)")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AntibandingMode.values().forEach { mode ->
                    FilterChip(
                        selected = antibanding == mode,
                        onClick = { onAntibandingSelected(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }

            SectionLabel("Toggles")
            CompactSwitchRow(
                label = if (audioAvailable) "Microphone" else "Microphone (tap to allow)",
                checked = micEnabled && audioAvailable,
                enabled = true,
                onToggle = onToggleMic
            )
            CompactSwitchRow(
                label = "Picture in picture",
                checked = pipEnabled,
                enabled = cameras.size > 1,
                onToggle = onTogglePip
            )
            CompactSwitchRow(
                label = "Stabilization",
                checked = stabilizationEnabled,
                enabled = true,
                onToggle = onToggleStabilization
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onOpenSettings
                ) {
                    Text("SRT setup")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onOpenReport
                ) {
                    Text("Cameras")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF8C97A1)
    )
}

@Composable
private fun StreamButton(streamRunning: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (streamRunning) Color(0xFFE5484D) else Color(0xFF66D9EF),
            contentColor = if (streamRunning) Color.White else Color(0xFF052730)
        )
    ) {
        Icon(
            imageVector = if (streamRunning) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (streamRunning) "Stop" else "Go Live",
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun flipCamera(
    cameras: List<CameraDescriptor>,
    selectedCameraId: String?,
    isCameraEnabled: (CameraDescriptor) -> Boolean,
    onCameraSelected: (CameraDescriptor) -> Unit
) {
    val current = cameras.firstOrNull { it.id == selectedCameraId }
    val candidates = cameras.filter { isCameraEnabled(it) && it.id != selectedCameraId }
    val target = candidates.firstOrNull { it.facing != current?.facing } ?: candidates.firstOrNull()
    target?.let(onCameraSelected)
}

@Composable
private fun SettingsPanel(
    host: String,
    port: String,
    latencyMs: String,
    streamTarget: StreamTarget,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onLatencyChanged: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xF2111318),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onClose) {
                    Text("Close")
                }
            }

            Text(
                text = "SRT target",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = host,
                onValueChange = onHostChanged,
                label = { Text("OBS PC LAN IP") },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = port,
                onValueChange = onPortChanged,
                label = { Text("UDP port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Text(
                text = "IP = your OBS PC's current Wi-Fi address (Windows: run ipconfig, use " +
                    "IPv4). If the PC's IP changes, update it here.\n" +
                    "Two phones? Give each a different port (e.g. 9001 and 9002) and add one " +
                    "Media Source per port in OBS.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAEB8C0)
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = latencyMs,
                onValueChange = onLatencyChanged,
                label = { Text("Latency (ms)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Text(
                text = "Lower = less delay, higher = smoother on weak Wi-Fi. On a Wi-Fi 6 " +
                    "LAN try 80–120 ms. The OBS Media Source latency must match this value.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAEB8C0)
            )
            Text(
                text = "OBS listener URL",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = streamTarget.listenerUri,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF66D9EF)
            )
            Text(
                text = "In OBS add a Media Source, paste this URL, and for the lowest delay " +
                    "set Network Buffering to 0 MB and uncheck \"Restart playback when source " +
                    "becomes active\". Then start SRT from the HUD.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD3DA)
            )
        }
    }
}

@Composable
private fun CameraReportPanel(
    report: CameraReport?,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xF2111318),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Camera Hardware",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRefresh) {
                        Text("Refresh")
                    }
                    TextButton(onClick = onClose) {
                        Text("Close")
                    }
                }
            }

            if (report == null) {
                Text("Scanning camera hardware...")
                return@Column
            }

            Text("Concurrent camera sets: ${report.concurrentCameraSets.size}")
            Text("Front cameras: ${report.cameras.count { it.facing == "FRONT" }}")
            Text("Back cameras: ${report.cameras.count { it.facing == "BACK" }}")
            Text("Logical multi-camera entries: ${report.cameras.count { it.isLogicalMultiCamera }}")

            report.cameras.forEach { camera ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xAA1A1E24),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = camera.displayName(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "focal=${camera.focalLengths.joinToString()} zoom=${camera.zoomRange ?: "n/a"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD3DA)
                        )
                        if (camera.physicalLenses.isNotEmpty()) {
                            Text(
                                text = "physical lenses=${camera.physicalLenses.joinToString { it.displayName() }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD3DA)
                            )
                        }
                        Text(
                            text = "video stab=${camera.videoStabilizationModes.joinToString()} optical=${camera.opticalStabilizationModes.joinToString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD3DA)
                        )
                        if (camera.physicalCameraIds.isNotEmpty()) {
                            Text(
                                text = "physical IDs=${camera.physicalCameraIds.joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD3DA)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(
        color = Color(0xCC0E2227),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF66D9EF)
        )
    }
}

private fun CameraDescriptor.displayName(): String {
    val focal = focalLengths.firstOrNull()?.let { " ${"%.1f".format(it)}mm" } ?: ""
    val logical = if (isLogicalMultiCamera) " logical" else ""
    return "$facing $id$focal$logical"
}

private fun CameraDescriptor.shortName(): String {
    val facingName = when (facing) {
        "BACK" -> "Rear"
        "FRONT" -> "Front"
        else -> facing.lowercase().replaceFirstChar { it.uppercase() }
    }
    return "$facingName $id"
}

private fun com.mostafa229.obsmobiledirector.camera.PhysicalLensDescriptor.displayName(): String {
    val focal = focalLengths.maxOrNull()?.let { "${"%.1f".format(it)}mm" } ?: "unknown"
    return "$lensRole $id $focal"
}

private fun Float.formatZoom(): String {
    return if (this % 1f == 0f) {
        toInt().toString()
    } else {
        "%.1f".format(this)
    }
}

private fun selectSecondaryCamera(
    cameras: List<CameraDescriptor>,
    selectedCameraId: String,
    selectedFacing: String?,
    concurrentCameraSets: List<List<String>>
): CameraDescriptor? {
    val supportedCameraIds = concurrentCameraSets
        .filter { it.contains(selectedCameraId) }
        .flatten()
        .filter { it != selectedCameraId }
        .toSet()
    if (supportedCameraIds.isEmpty()) return null

    return cameras.firstOrNull { camera ->
        camera.id in supportedCameraIds && camera.facing != selectedFacing
    } ?: cameras.firstOrNull { it.id in supportedCameraIds }
}
