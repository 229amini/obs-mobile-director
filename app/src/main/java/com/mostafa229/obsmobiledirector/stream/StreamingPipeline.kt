package com.mostafa229.obsmobiledirector.stream

/**
 * Pipeline target for the first functional streaming milestone:
 * CameraX/Camera2 surfaces -> OpenGL compositor -> MediaCodec H.264 -> SRT sender.
 */
class StreamingPipeline {
    fun start(target: StreamTarget) {
        check(target.host.isNotBlank()) { "Stream host is required." }
    }

    fun stop() = Unit
}

