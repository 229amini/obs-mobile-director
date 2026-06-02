package com.mostafa229.obsmobiledirector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mostafa229.obsmobiledirector.camera.CameraCapabilityScanner
import com.mostafa229.obsmobiledirector.camera.CameraPreview
import com.mostafa229.obsmobiledirector.camera.CameraReport
import com.mostafa229.obsmobiledirector.ui.DirectorMode
import com.mostafa229.obsmobiledirector.ui.DirectorModeSelector
import com.mostafa229.obsmobiledirector.ui.StreamTargetCard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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
    var selectedMode by remember { mutableStateOf(DirectorMode.BackFull) }
    var report by remember { mutableStateOf<CameraReport?>(null) }
    var streamStatus by remember {
        mutableStateOf("Preview build. SRT output is not implemented yet.")
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "OBS Mobile Director",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Android camera director for Samsung S24/S25, dual-camera PiP, stabilization checks, and low-latency OBS output.",
            style = MaterialTheme.typography.bodyMedium
        )

        if (!hasCameraPermission) {
            Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera permission")
            }
            return@Column
        }

        DirectorModeSelector(
            selectedMode = selectedMode,
            onModeSelected = { selectedMode = it }
        )

        CameraPreview(mode = selectedMode)

        StreamTargetCard()

        Text(
            text = streamStatus,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        CameraReportView(report = report)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { report = CameraCapabilityScanner(context).scan() }) {
                Text("Refresh capabilities")
            }
            Button(
                onClick = {
                    streamStatus = "SRT streaming is not active in this build. Next milestone is encoder + SRT transport."
                }
            ) {
                Text("Check stream readiness")
            }
        }
    }
}

@Composable
private fun CameraReportView(report: CameraReport?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Device capability report",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (report == null) {
            Text("Scanning camera hardware...")
            return
        }

        Text("Concurrent camera sets: ${report.concurrentCameraSets.size}")
        Text("Front cameras: ${report.cameras.count { it.facing == "FRONT" }}")
        Text("Back cameras: ${report.cameras.count { it.facing == "BACK" }}")
        Text("Logical multi-camera entries: ${report.cameras.count { it.isLogicalMultiCamera }}")

        Spacer(modifier = Modifier.height(8.dp))
        report.cameras.forEach { camera ->
            Text(
                text = "Camera ${camera.id}: ${camera.facing}, stabilization=${camera.videoStabilizationModes.joinToString()}, focal=${camera.focalLengths.joinToString()}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
