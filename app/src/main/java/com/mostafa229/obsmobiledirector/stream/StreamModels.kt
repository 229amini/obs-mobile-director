package com.mostafa229.obsmobiledirector.stream

data class StreamTarget(
    val host: String,
    val port: Int,
    val protocol: StreamProtocol = StreamProtocol.Srt
) {
    val uri: String
        get() = when (protocol) {
            // RootEncoder's SRT client uses the query string as the streamid when no
            // explicit ?streamid= is given, and it rejects a bare srt://host:port
            // (empty path -> "Endpoint malformed"). Keep a single, meaningful query
            // param: latency in microseconds (200000 us = 200 ms) to match the OBS
            // listener. mode=caller is the RootEncoder default and timeout is ignored,
            // so both are dropped to avoid a noisy streamid.
            StreamProtocol.Srt -> "srt://$host:$port?latency=$LATENCY_MICROS"
        }

    val listenerUri: String
        get() = when (protocol) {
            StreamProtocol.Srt -> "srt://0.0.0.0:$port?mode=listener&latency=$LATENCY_MICROS&timeout=5000000"
        }

    private companion object {
        const val LATENCY_MICROS = 200000
    }
}

enum class StreamProtocol {
    Srt
}
