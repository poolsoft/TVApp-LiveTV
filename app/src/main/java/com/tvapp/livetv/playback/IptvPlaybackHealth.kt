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
    val droppedFrameRate: Float? = null,
    val framesPerSecond: Float? = null,
    val containerFormat: String? = null,
    val videoProfile: String? = null,
    val audioSampleRateHz: Int? = null,
    val audioChannelCount: Int? = null,
    val retryAttempt: Int = 0,
    val lastErrorCode: String? = null,
    val lastFailureClass: IptvPlaybackFailureClass? = null,
    val engine: IptvPlaybackEngine = IptvPlaybackEngine.MEDIA3,
    val fallbackReason: String? = null,
)

internal fun classifyIjkPlaybackFailure(what: Int, extra: Int): IptvPlaybackFailureClass = when {
    what == -110 -> IptvPlaybackFailureClass.TIMEOUT
    what == -1004 -> IptvPlaybackFailureClass.NETWORK
    what == -1010 -> IptvPlaybackFailureClass.DECODER
    what == -1007 || what == 200 -> IptvPlaybackFailureClass.SOURCE
    extra in 400..599 -> IptvPlaybackFailureClass.HTTP
    else -> IptvPlaybackFailureClass.UNKNOWN
}

internal fun ijkPlaybackExceptionCode(failureClass: IptvPlaybackFailureClass): Int = when (failureClass) {
    IptvPlaybackFailureClass.NETWORK -> androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    IptvPlaybackFailureClass.HTTP -> androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    IptvPlaybackFailureClass.DECODER -> androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED
    IptvPlaybackFailureClass.SOURCE -> androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
    IptvPlaybackFailureClass.TIMEOUT -> androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT
    IptvPlaybackFailureClass.UNKNOWN -> androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED
}

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

internal fun shouldRecommendExternalFallback(
    failureClass: IptvPlaybackFailureClass?,
): Boolean = failureClass == IptvPlaybackFailureClass.DECODER ||
    failureClass == IptvPlaybackFailureClass.SOURCE

internal fun shouldUseIjkFallback(
    enabled: Boolean,
    alreadyAttempted: Boolean,
    failureClass: IptvPlaybackFailureClass?,
): Boolean = enabled && !alreadyAttempted && shouldRecommendExternalFallback(failureClass)
