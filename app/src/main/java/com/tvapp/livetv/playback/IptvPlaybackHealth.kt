package com.tvapp.livetv.playback

enum class IptvPlaybackPhase { IDLE, PREPARING, BUFFERING, READY, ENDED, FAILED, RELEASED }

enum class IptvPlaybackFailureClass { NETWORK, HTTP, DECODER, SOURCE, TIMEOUT, UNKNOWN }

data class IptvPlaybackHealthSnapshot(
    val phase: IptvPlaybackPhase = IptvPlaybackPhase.IDLE,
    val contentKind: IptvContentKind = IptvContentKind.UNKNOWN,
    val isPlaying: Boolean = false,
    val firstFrameRendered: Boolean = false,
    val startupDurationMillis: Long? = null,
    val timeSinceLastFrameMillis: Long? = null,
    val timeSinceLastProgressMillis: Long? = null,
    val positionMillis: Long = 0L,
    val bufferedDurationMillis: Long = 0L,
    val bitrateBps: Int? = null,
    val estimatedBandwidthBps: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val droppedFrames: Int = 0,
    val retryAttempt: Int = 0,
    val lastErrorCode: String? = null,
    val lastFailureClass: IptvPlaybackFailureClass? = null,
)

internal fun classifyIptvPlaybackFailure(errorCodeName: String): IptvPlaybackFailureClass {
    val code = errorCodeName.uppercase()
    return when {
        "TIMEOUT" in code -> IptvPlaybackFailureClass.TIMEOUT
        "BAD_HTTP" in code || "HTTP_STATUS" in code -> IptvPlaybackFailureClass.HTTP
        "NETWORK" in code || "CONNECTION" in code || "CLEARTEXT" in code ->
            IptvPlaybackFailureClass.NETWORK
        "DECOD" in code || "FORMAT_EXCEEDS" in code || "FORMAT_UNSUPPORTED" in code ->
            IptvPlaybackFailureClass.DECODER
        "SOURCE" in code || "FILE_NOT_FOUND" in code || "NO_PERMISSION" in code ||
            "READ_POSITION" in code || "PARSING" in code -> IptvPlaybackFailureClass.SOURCE
        else -> IptvPlaybackFailureClass.UNKNOWN
    }
}
