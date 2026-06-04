package com.mostafa229.obsmobiledirector.stream

import android.os.Handler
import android.os.Looper
import com.pedro.common.ConnectChecker
import com.pedro.library.srt.SrtCamera2
import com.pedro.library.view.OpenGlView

data class StreamSettings(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val videoBitrate: Int = 6_000_000,
    val keyframeInterval: Int = 2,
    val rotation: Int = 0
)

class StreamingPipeline(
    private val onStatus: (message: String, streaming: Boolean?) -> Unit = { _, _ -> }
) {
    var activeTarget: StreamTarget? = null
        private set

    private var srtCamera: SrtCamera2? = null
    private var attachedView: OpenGlView? = null
    private var prepared = false
    private var currentCameraId: String? = null
    private var stabilizationEnabled = false
    private var currentZoom = 1f
    private var connectionAttempt = 0
    private var connected = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settings = StreamSettings()

    fun attachView(openGlView: OpenGlView) {
        if (attachedView == openGlView && srtCamera != null) {
            prepareIfNeeded()
            currentCameraId?.let { startPreview(it) }
            applyStabilization()
            return
        }
        attachedView = openGlView
        ensureCamera()
        prepareIfNeeded()
        currentCameraId?.let { startPreview(it) }
        applyStabilization()
    }

    fun selectCamera(cameraId: String?) {
        if (cameraId == null || cameraId == currentCameraId) return
        currentCameraId = cameraId
        val camera = srtCamera ?: return
        prepareIfNeeded()
        if (camera.isOnPreview || camera.isStreaming) {
            camera.switchCamera(cameraId)
        } else {
            startPreview(cameraId)
        }
        setZoomRatio(currentZoom)
        applyStabilization()
    }

    fun setStabilization(enabled: Boolean) {
        stabilizationEnabled = enabled
        applyStabilization()
    }

    fun setZoomRatio(zoom: Float, preferOptical: Boolean = false) {
        currentZoom = zoom.coerceIn(0.5f, 20f)
        val camera = srtCamera ?: return
        runCatching {
            val opticalTarget = if (preferOptical) {
                camera.opticalZooms?.filterNotNull().orEmpty()
                    .filter { it > 1f }
                    .minByOrNull { kotlin.math.abs(it - currentZoom) }
                    ?.takeIf { kotlin.math.abs(it - currentZoom) <= 0.75f }
            } else {
                null
            }
            if (opticalTarget != null) {
                camera.setOpticalZoom(opticalTarget)
            } else {
                camera.setZoom(currentZoom)
            }
        }.onFailure {
            runCatching { camera.setZoom(currentZoom) }
        }
    }

    fun start(target: StreamTarget) {
        check(target.host.isNotBlank()) { "Stream host is required." }
        check(target.port in 1..65535) { "Stream port must be between 1 and 65535." }
        try {
            val camera = ensureCamera()
            prepareIfNeeded()
            currentCameraId?.let { startPreview(it) }
            setZoomRatio(currentZoom)
            applyStabilization()
            if (!camera.isStreaming) {
                camera.startStream(target.uri)
            }
            activeTarget = target
            connected = false
            scheduleSlowConnectionHint(++connectionAttempt)
        } catch (error: Throwable) {
            activeTarget = null
            resetCameraForNextStream()
            throw error
        }
    }

    fun stop() {
        connectionAttempt++
        connected = false
        srtCamera?.takeIf { it.isStreaming }?.stopStream()
        activeTarget = null
        resetCameraForNextStream()
    }

    fun release() {
        runCatching {
            srtCamera?.takeIf { it.isStreaming }?.stopStream()
            srtCamera?.stopPreview()
        }
        activeTarget = null
        connectionAttempt++
        connected = false
        srtCamera = null
        attachedView = null
        prepared = false
        currentCameraId = null
    }

    private fun ensureCamera(): SrtCamera2 {
        val openGlView = attachedView ?: error("Stream preview is not ready yet.")
        return srtCamera?.also { it.replaceView(openGlView) } ?: SrtCamera2(
            openGlView,
            connectChecker
        ).also { camera ->
            srtCamera = camera
            prepared = false
        }
    }

    private fun resetCameraForNextStream() {
        val openGlView = attachedView ?: run {
            prepared = false
            return
        }
        runCatching {
            srtCamera?.takeIf { it.isStreaming }?.stopStream()
            srtCamera?.stopPreview()
        }
        srtCamera = SrtCamera2(openGlView, connectChecker)
        prepared = false
        runCatching {
            prepareIfNeeded()
            currentCameraId?.let { startPreview(it) }
            setZoomRatio(currentZoom)
            applyStabilization()
        }.onFailure { error ->
            onStatus("Preview recovery failed: ${error.message ?: "camera API error"}", false)
        }
    }

    // Previously this tore down the encoder after 8s, which killed first-time SRT
    // handshakes that legitimately take longer than that and left the app stuck on
    // "connecting" with the stream silently dead. Now it only surfaces a hint and
    // leaves the SRT caller running so it can keep retrying the handshake. Genuine
    // failures still arrive through ConnectChecker.onConnectionFailed.
    private fun scheduleSlowConnectionHint(attempt: Int) {
        mainHandler.postDelayed(
            {
                val camera = srtCamera ?: return@postDelayed
                if (attempt == connectionAttempt && !connected && camera.isStreaming) {
                    onStatus(
                        "Still negotiating SRT. Confirm OBS has a Media Source listening on " +
                            "${activeTarget?.listenerUri ?: "the selected port"} and that UDP is " +
                            "open in Windows Firewall. Leave it running; it keeps retrying.",
                        null
                    )
                }
            },
            10_000L
        )
    }

    private fun prepareIfNeeded() {
        val camera = srtCamera ?: return
        if (prepared) return
        val videoPrepared = camera.prepareVideo(
            settings.width,
            settings.height,
            settings.fps,
            settings.videoBitrate,
            settings.keyframeInterval,
            settings.rotation
        )
        check(videoPrepared) { "Unable to prepare H.264 video encoder." }
        camera.disableAudio()
        prepared = true
    }

    private fun startPreview(cameraId: String) {
        val camera = srtCamera ?: return
        if (!camera.isOnPreview && !camera.isStreaming) {
            camera.startPreview(
                cameraId,
                settings.width,
                settings.height,
                settings.fps,
                settings.rotation
            )
        }
    }

    private fun applyStabilization() {
        val camera = srtCamera ?: return
        if (stabilizationEnabled) {
            val enabled = camera.enableVideoStabilization()
            if (!enabled) {
                camera.enableOpticalVideoStabilization()
            }
        } else {
            camera.disableVideoStabilization()
            camera.disableOpticalVideoStabilization()
        }
    }

    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            connected = false
            onStatus("Connecting to OBS: $url", null)
        }

        override fun onConnectionSuccess() {
            connected = true
            onStatus("SRT live. OBS should now show the phone feed.", true)
        }

        override fun onConnectionFailed(reason: String) {
            activeTarget = null
            connectionAttempt++
            connected = false
            resetCameraForNextStream()
            onStatus("SRT connection failed: $reason", false)
        }

        override fun onDisconnect() {
            activeTarget = null
            connectionAttempt++
            connected = false
            onStatus("SRT disconnected.", false)
        }

        override fun onAuthError() {
            connectionAttempt++
            connected = false
            onStatus("SRT authentication failed.", false)
        }

        override fun onAuthSuccess() {
            onStatus("SRT authentication accepted.", null)
        }

        override fun onNewBitrate(bitrate: Long) {
            connected = true
            onStatus("SRT live: ${bitrate / 1000} Kbps upload.", true)
        }
    }
}
