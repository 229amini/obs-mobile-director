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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.mostafa229.obsmobiledirector.camera.CameraPreview
import com.mostafa229.obsmobiledirector.camera.CameraCapabilityScanner
import com.mostafa229.obsmobiledirector.camera.CameraDescriptor
import com.mostafa229.obsmobiledirector.camera.CameraReport
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
    var selectedCameraId by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<CameraReport?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var stabilizationEnabled by remember { mutableStateOf(true) }
    var pipEnabled by remember { mutableStateOf(false) }
    var controlsHidden by remember { mutableStateOf(false) }
    var cameraSwitching by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var host by remember { mutableStateOf("192.168.1.9") }
    var port by remember { mutableStateOf("9001") }
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
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            report = CameraCapabilityScanner(context).scan()
        }
    }

    LaunchedEffect(report) {
        val cameras = report?.cameras.orEmpty()
        if (cameras.isNotEmpty() && cameras.none { it.id == selectedCameraId }) {
            selectedCameraId = cameras.firstOrNull { it.facing == "BACK" }?.id ?: cameras.first().id
        }
    }

    LaunchedEffect(host, port) {
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

    DisposableEffect(Unit) {
        onDispose {
            streamingPipeline.release()
        }
    }

    if (!hasCameraPermission) {
        PermissionScreen(onGrantPermission = {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
    val streamTarget = StreamTarget(
        host = host.trim(),
        port = parsedPort ?: 9001
    )

    Box(modifier = Modifier.fillMaxSize()) {
        StreamingPreview(
            streamingPipeline = streamingPipeline,
            modifier = Modifier.fillMaxSize()
        )

        TopStatusOverlay(
            selectedCamera = selectedCamera,
            streamRunning = streamRunning
        )

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

        CameraHudOverlay(
            cameras = cameras,
            selectedCameraId = selectedCameraId,
            stabilizationEnabled = stabilizationEnabled,
            pipEnabled = pipEnabled,
            controlsHidden = controlsHidden,
            streamRunning = streamRunning,
            streamStatus = streamStatus,
            zoomRatio = zoomRatio,
            zoomMin = zoomMin,
            zoomMax = zoomMax,
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
            onToggleControls = { controlsHidden = !controlsHidden },
            onZoomChanged = { nextZoom, preferOptical ->
                val constrainedZoom = nextZoom.coerceIn(zoomMin, zoomMax)
                zoomRatio = constrainedZoom
                runCatching {
                    streamingPipeline.setZoomRatio(
                        zoom = constrainedZoom,
                        preferOptical = preferOptical
                    )
                }.onSuccess {
                    streamStatus = if (preferOptical && constrainedZoom >= 3f) {
                        "Zoom ${constrainedZoom.formatZoom()}x selected. Tele lens is used automatically when the device exposes it."
                    } else {
                        "Zoom ${constrainedZoom.formatZoom()}x selected."
                    }
                }.onFailure { error ->
                    streamStatus = "Unable to zoom: ${error.message ?: "camera API error"}"
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
                streamTarget = streamTarget,
                onHostChanged = { host = it },
                onPortChanged = { port = it },
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
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier,
        color = Color.Black,
        shape = shape,
        shadowElevation = 12.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color.Black)
        ) {
            CameraPreview(
                cameraId = camera.id,
                stabilizationEnabled = stabilizationEnabled,
                modifier = Modifier.fillMaxSize(),
                onError = onBindError
            )
        }
    }
}

@Composable
private fun ArrowTab(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xB0000000),
        shape = RoundedCornerShape(999.dp)
    ) {
        TextButton(
            modifier = Modifier
                .width(36.dp)
                .height(52.dp),
            onClick = onClick
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoomControl(
    zoomRatio: Float,
    zoomMin: Float,
    zoomMax: Float,
    onZoomChanged: (zoom: Float, preferOptical: Boolean) -> Unit
) {
    val presets = listOf(0.6f, 1f, 2f, 3f, 5f)
    Column(
        modifier = Modifier.width(172.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                enabled = zoomRatio > zoomMin,
                onClick = { onZoomChanged((zoomRatio - 0.5f).coerceAtLeast(zoomMin), false) }
            ) {
                Text("-")
            }
            Text(
                text = "${zoomRatio.formatZoom()}x",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFDCE4EA)
            )
            TextButton(
                enabled = zoomRatio < zoomMax,
                onClick = { onZoomChanged((zoomRatio + 0.5f).coerceAtMost(zoomMax), false) }
            ) {
                Text("+")
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = kotlin.math.abs(zoomRatio - preset) < 0.15f,
                    enabled = preset in zoomMin..zoomMax,
                    onClick = { onZoomChanged(preset, true) },
                    label = { Text("${preset.formatZoom()}x") }
                )
            }
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
        modifier = Modifier.width(132.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
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
private fun TopStatusOverlay(
    selectedCamera: CameraDescriptor?,
    streamRunning: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color(0x99000000),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = "OBS Mobile Director",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = selectedCamera?.let { "Preview: ${it.displayName()}" } ?: "Scanning cameras",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC8D0D8)
                )
            }
        }
        StatusPill(text = if (streamRunning) "SRT" else "LOCAL")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CameraHudOverlay(
    cameras: List<CameraDescriptor>,
    selectedCameraId: String?,
    stabilizationEnabled: Boolean,
    pipEnabled: Boolean,
    controlsHidden: Boolean,
    streamRunning: Boolean,
    streamStatus: String,
    zoomRatio: Float,
    zoomMin: Float,
    zoomMax: Float,
    isCameraEnabled: (CameraDescriptor) -> Boolean,
    onCameraSelected: (CameraDescriptor) -> Unit,
    onToggleStabilization: () -> Unit,
    onTogglePip: () -> Unit,
    onToggleSettings: () -> Unit,
    onToggleControls: () -> Unit,
    onZoomChanged: (zoom: Float, preferOptical: Boolean) -> Unit,
    onToggleReport: () -> Unit,
    onToggleStream: () -> Unit
) {
    val controlsOffset by animateDpAsState(
        targetValue = if (controlsHidden) 196.dp else 0.dp,
        label = "controlsOffset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(1.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Surface(
                color = Color(0xB0000000),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 260.dp, max = 460.dp)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = streamStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFDCE4EA)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cameras.forEach { camera ->
                            val enabled = isCameraEnabled(camera)
                            FilterChip(
                                selected = camera.id == selectedCameraId,
                                onClick = { onCameraSelected(camera) },
                                enabled = enabled,
                                label = { Text(camera.shortName()) }
                            )
                        }
                    }
                }
            }

            Box(contentAlignment = Alignment.BottomEnd) {
                Row(
                    modifier = Modifier.offset(x = controlsOffset),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArrowTab(
                        label = if (controlsHidden) "<" else ">",
                        onClick = onToggleControls
                    )
                    Surface(
                        color = Color(0xB0000000),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Button(onClick = onToggleStream) {
                                Text(if (streamRunning) "Stop" else "Start")
                            }
                            ZoomControl(
                                zoomRatio = zoomRatio,
                                zoomMin = zoomMin,
                                zoomMax = zoomMax,
                                onZoomChanged = onZoomChanged
                            )
                            CompactSwitchRow(
                                label = "PiP",
                                checked = pipEnabled,
                                enabled = cameras.size > 1,
                                onToggle = onTogglePip
                            )
                            CompactSwitchRow(
                                label = "Stab",
                                checked = stabilizationEnabled,
                                enabled = true,
                                onToggle = onToggleStabilization
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = onToggleSettings) {
                                    Text("Settings")
                                }
                                TextButton(onClick = onToggleReport) {
                                    Text("Cameras")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    host: String,
    port: String,
    streamTarget: StreamTarget,
    onHostChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
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
                text = "Use this URL in OBS Media Source or VLC on the PC, then start SRT from the HUD.",
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
