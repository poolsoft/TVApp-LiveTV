package com.tvapp.livetv.playback

/** Only finite, fully buffered live parts may be followed by another connection. */
internal fun shouldQueueLiveContinuation(
    enabled: Boolean,
    contentType: String?,
    playWhenReady: Boolean,
    dynamicLive: Boolean,
    loading: Boolean,
    durationMillis: Long,
    positionMillis: Long,
    bufferedPositionMillis: Long,
    hasNext: Boolean,
    leadMillis: Int = 3_000,
): Boolean = enabled && contentType.equals("LIVE", true) && playWhenReady && !dynamicLive &&
    !loading && !hasNext && durationMillis > 0 &&
    bufferedPositionMillis >= durationMillis - 100 && durationMillis - positionMillis <= leadMillis

internal fun consecutiveShortLiveParts(previous: Int, durationMillis: Long): Int =
    if (durationMillis >= 5_000) 0 else previous + 1
