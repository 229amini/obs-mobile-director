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
    // 1s GOP: OBS waits for a keyframe before it shows the first frame and re-syncs
    // after packet loss, so a shorter interval cuts startup/recovery latency. The
    // bitrate cost at 1080p30 is negligible on a flagship encoder.
    val keyframeInterval: Int = 1,
    val rotation: Int = 0,
    val audioBitrate: Int = 128 * 1024,
    val audioSampleRate: Int = 48_000,
    val audioStereo: Boolean = true
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
    // audioPermitted: RECORD_AUDIO granted -> an audio track is muxed into the stream.
    // micOn: the live mute state toggled by the user (unmuted vs silence).
    // audioTrackPrepared: an audio track actually exists in the running stream.
    private var audioPermitted = false
    private var micOn = true
    private var audioTrackPrepared = false
    private var currentZoom = 1f
    private var connectionAttempt = 0
    private var connected = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val settings = StreamSettings()

    fun attachView(openGlView: OpenGlView) {
        if (attachedView == openGlView && srtCamera != null) {
            // The Compose update lambda fires on every recomposition (e.g. each per-second
            // bitrate status tick while streaming). Re-running applyStabilization() here
            // reconfigured the Camera2 capture session each time and froze the on-screen
            // preview. The view is already wired up, so do nothing — stabilization is
            // applied on first attach and via setStabilization().
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

    /**
     * Whether RECORD_AUDIO is granted. Controls whether an audio track is muxed into the
     * stream at all. Changing it while idle forces a re-prepare so the track is added (or
     * removed) on the next Go Live; mid-stream it can't add/remove a track.
     */
    fun setAudioPermitted(permitted: Boolean) {
        if (audioPermitted == permitted) return
        audioPermitted = permitted
        if (srtCamera?.isStreaming != true) {
            prepared = false
        }
    }

    /**
     * Live mute/unmute. While streaming this mutes (silence) or unmutes the mic instantly
     * without touching the encoder, so OBS keeps A/V sync. It only has an audible effect
     * if the stream was started with the mic permitted (an audio track exists).
     */
    fun setMicOn(on: Boolean) {
        micOn = on
        val camera = srtCamera ?: return
        if (!camera.isStreaming) return // applied when the next stream is prepared
        if (audioTrackPrepared) {
            runCatching { if (on) camera.enableAudio() else camera.disableAudio() }
            onStatus(if (on) "Microphone unmuted." else "Microphone muted.", null)
        } else if (on) {
            onStatus("This stream has no mic track — stop and Go Live again to add audio.", null)
        }
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
        // Prepare the audio track whenever the mic is permitted (independent of the live
        // mute toggle) so the track always exists and can be unmuted mid-stream.
        val audioReady = audioPermitted && runCatching {
            camera.prepareAudio(
                settings.audioBitrate,
                settings.audioSampleRate,
                settings.audioStereo
            )
        }.getOrDefault(false)
        audioTrackPrepared = audioReady
        if (audioReady) {
            // Honour the current mute state from the very first frame.
            if (micOn) camera.enableAudio() else camera.disableAudio()
        } else {
            // No permission / mic busy -> video-only. disableAudio() is a harmless mute.
            camera.disableAudio()
            if (audioPermitted) {
                onStatus(
                    "Microphone unavailable — streaming video only. Re-grant mic " +
                        "permission, then start output again to send audio to OBS.",
                    null
                )
            }
        }
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

    private fun micLabel(): String = when {
        !audioTrackPrepared -> "no mic"
        micOn -> "mic on"
        else -> "muted"
    }

    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            connected = false
            onStatus("Connecting to OBS: $url", null)
        }

        override fun onConnectionSuccess() {
            connected = true
            onStatus("SRT live (${micLabel()}). OBS should now show the phone feed.", true)
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
            onStatus("SRT live (${micLabel()}): ${bitrate / 1000} Kbps upload.", true)
        }
    }
}
