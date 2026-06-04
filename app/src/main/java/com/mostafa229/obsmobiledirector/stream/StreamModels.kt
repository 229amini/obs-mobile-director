package com.mostafa229.obsmobiledirector.stream

data class StreamTarget(
    val host: String,
    val port: Int,
    // SRT receiver buffer in milliseconds. This is the single largest tunable chunk of
    // glass-to-glass latency: lower = snappier but less tolerant of Wi-Fi packet loss.
    // 120 ms is a good default for a Wi-Fi 6 LAN; bump it up if the feed stutters.
    val latencyMillis: Int = DEFAULT_LATENCY_MILLIS,
    val protocol: StreamProtocol = StreamProtocol.Srt
) {
    private val latencyMicros: Int
        get() = latencyMillis.coerceIn(MIN_LATENCY_MILLIS, MAX_LATENCY_MILLIS) * 1000

    val uri: String
        get() = when (protocol) {
            // RootEncoder's SRT client uses the query string as the streamid when no
            // explicit ?streamid= is given, and it rejects a bare srt://host:port
            // (empty path -> "Endpoint malformed"). Keep a single, meaningful query
            // param: latency in microseconds to match the OBS listener. mode=caller is
            // the RootEncoder default and timeout is ignored, so both are dropped to
            // avoid a noisy streamid.
            StreamProtocol.Srt -> "srt://$host:$port?latency=$latencyMicros"
        }

    val listenerUri: String
        get() = when (protocol) {
            StreamProtocol.Srt -> "srt://0.0.0.0:$port?mode=listener&latency=$latencyMicros&timeout=5000000"
        }

    companion object {
        const val DEFAULT_LATENCY_MILLIS = 120
        const val MIN_LATENCY_MILLIS = 20
        const val MAX_LATENCY_MILLIS = 1000
    }
}

enum class StreamProtocol {
    Srt
}
