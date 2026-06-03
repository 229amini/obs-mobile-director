package com.mostafa229.obsmobiledirector.stream

data class StreamTarget(
    val host: String,
    val port: Int,
    val protocol: StreamProtocol = StreamProtocol.Srt
) {
    val uri: String
        get() = when (protocol) {
            StreamProtocol.Srt -> {
                "srt://$host:$port?mode=caller&latency=200000&timeout=5000000"
            }
        }

    val listenerUri: String
        get() = when (protocol) {
            StreamProtocol.Srt -> "srt://0.0.0.0:$port?mode=listener&latency=200000&timeout=5000000"
        }
}

enum class StreamProtocol {
    Srt
}
