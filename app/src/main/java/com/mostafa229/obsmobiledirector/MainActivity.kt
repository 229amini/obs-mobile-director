package com.mostafa229.obsmobiledirector

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
    var selectedMode by remember { mutableStateOf(DirectorMode.BackFull) }
    var report by remember { mutableStateOf<CameraReport?>(null) }
    var showReport by remember { mutableStateOf(false) }
    var streamStatus by remember {
        mutableStateOf("Local preview is active. OBS/SRT output is not implemented in this build.")
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

    if (!hasCameraPermission) {
        PermissionScreen(onGrantPermission = {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        })
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            mode = selectedMode,
            modifier = Modifier.fillMaxSize()
        )

        TopStatusOverlay(selectedMode = selectedMode)

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = Color(0xEE111318),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Director controls",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${selectedMode.label} is shown in the app preview",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB7C0C8)
                        )
                    }
                    StatusPill(text = "PREVIEW")
                }

                DirectorModeSelector(
                    selectedMode = selectedMode,
                    onModeSelected = {
                        selectedMode = it
                        streamStatus = "${it.label} selected for local preview."
                    }
                )

                StreamTargetCard()

                Text(
                    text = streamStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            showReport = !showReport
                            if (showReport) {
                                report = CameraCapabilityScanner(context).scan()
                            }
                        }
                    ) {
                        Text(if (showReport) "Hide capabilities" else "Capabilities")
                    }
                    Button(
                        onClick = {
                            streamStatus = "SRT output is not active yet. This build is for camera preview and device capability validation."
                        }
                    ) {
                        Text("Check OBS output")
                    }
                }

                if (showReport) {
                    CameraReportView(report = report)
                }
            }
        }
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
private fun TopStatusOverlay(selectedMode: DirectorMode) {
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
                    text = "Local preview: ${selectedMode.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFC8D0D8)
                )
            }
        }
        StatusPill(text = "LOCAL")
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
                text = "Camera ${camera.id}: ${camera.facing}, stabilization modes=${camera.videoStabilizationModes.joinToString()}, focal=${camera.focalLengths.joinToString()}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFCBD3DA)
            )
        }
    }
}
