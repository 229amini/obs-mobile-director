package com.mostafa229.obsmobiledirector.stream

data class StreamTarget(
    val host: String,
    val port: Int,
    val protocol: StreamProtocol = StreamProtocol.Srt
) {
    val uri: String
        get() = when (protocol) {
            StreamProtocol.Srt -> "srt://$host:$port"
        }
}

enum class StreamProtocol {
    Srt
}

