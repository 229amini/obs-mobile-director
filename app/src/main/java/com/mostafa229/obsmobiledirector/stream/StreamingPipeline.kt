package com.mostafa229.obsmobiledirector.stream

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
    private val settings = StreamSettings()

    fun attachView(openGlView: OpenGlView) {
        if (attachedView == openGlView && srtCamera != null) {
            prepareIfNeeded()
            currentCameraId?.let { startPreview(it) }
            applyStabilization()
            return
        }
        attachedView = openGlView
        if (srtCamera == null) {
            srtCamera = SrtCamera2(openGlView, connectChecker)
        } else {
            srtCamera?.replaceView(openGlView)
        }
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
        applyStabilization()
    }

    fun setStabilization(enabled: Boolean) {
        stabilizationEnabled = enabled
        applyStabilization()
    }

    fun start(target: StreamTarget) {
        check(target.host.isNotBlank()) { "Stream host is required." }
        check(target.port in 1..65535) { "Stream port must be between 1 and 65535." }
        val camera = srtCamera ?: error("Stream preview is not ready yet.")
        prepareIfNeeded()
        currentCameraId?.let { startPreview(it) }
        applyStabilization()
        if (!camera.isStreaming) {
            camera.startStream(target.uri)
        }
        activeTarget = target
    }

    fun stop() {
        srtCamera?.takeIf { it.isStreaming }?.stopStream()
        activeTarget = null
    }

    fun release() {
        stop()
        srtCamera?.stopPreview()
        srtCamera = null
        attachedView = null
        prepared = false
        currentCameraId = null
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
            onStatus("Connecting to OBS: $url", null)
        }

        override fun onConnectionSuccess() {
            onStatus("SRT live. OBS should now show the phone feed.", true)
        }

        override fun onConnectionFailed(reason: String) {
            activeTarget = null
            onStatus("SRT connection failed: $reason", false)
        }

        override fun onDisconnect() {
            activeTarget = null
            onStatus("SRT disconnected.", false)
        }

        override fun onAuthError() {
            onStatus("SRT authentication failed.", false)
        }

        override fun onAuthSuccess() {
            onStatus("SRT authentication accepted.", null)
        }

        override fun onNewBitrate(bitrate: Long) {
            onStatus("SRT live: ${bitrate / 1000} Kbps upload.", true)
        }
    }
}
