package com.tvapp.livetv.playback

enum class IptvPlaybackPhase { IDLE, PREPARING, BUFFERING, READY, ENDED, FAILED, RELEASED }

enum class IptvPlaybackFailureClass { NETWORK, HTTP, DECODER, SOURCE, TIMEOUT, UNKNOWN }

enum class IptvRecoveryReason {
    FIRST_FRAME_TIMEOUT,
    BUFFERING_TIMEOUT,
    PLAYBACK_STALLED,
    LIVE_STREAM_ENDED,
}

enum class IptvRecoveryAction { REPREPARE, EXHAUSTED }

data class IptvRecoveryEvent(
    val reason: IptvRecoveryReason,
    val action: IptvRecoveryAction,
    val attempt: Int,
)

internal data class IptvWatchdogObservation(
    val expectsVideo: Boolean,
    val firstFrameRendered: Boolean,
    val startupMillis: Long,
    val isIdle: Boolean,
    val isBuffering: Boolean,
    val bufferingMillis: Long,
    val isReadyAndPlaying: Boolean,
    val stalledProgressMillis: Long,
    val staleFrameMillis: Long?,
)

internal fun decideIptvWatchdogReason(
    observation: IptvWatchdogObservation,
    firstFrameTimeoutMillis: Long,
    bufferingTimeoutMillis: Long,
    stallTimeoutMillis: Long,
): IptvRecoveryReason? = when {
    observation.expectsVideo &&
        !observation.firstFrameRendered &&
        !observation.isIdle &&
        observation.startupMillis >= firstFrameTimeoutMillis ->
        IptvRecoveryReason.FIRST_FRAME_TIMEOUT
    observation.isBuffering && observation.bufferingMillis >= bufferingTimeoutMillis ->
        IptvRecoveryReason.BUFFERING_TIMEOUT
    observation.isReadyAndPlaying &&
        observation.stalledProgressMillis >= stallTimeoutMillis &&
        (observation.staleFrameMillis == null || observation.staleFrameMillis >= stallTimeoutMillis) ->
        IptvRecoveryReason.PLAYBACK_STALLED
    else -> null
}

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
