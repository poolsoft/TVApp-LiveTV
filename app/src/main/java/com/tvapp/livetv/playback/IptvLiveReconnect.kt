package com.tvapp.livetv.playback

import java.net.URI

/** Reconnect policy for live sources that close the HTTP connection on a
 *  regular cadence (byte count or wall clock) instead of failing. A normal
 *  end-of-input reconnects immediately; abnormal failures back off with a
 *  capped exponential delay and give Media3 error handling back after
 *  LIVE_RECONNECT_MAX_CONSECUTIVE_FAILURES so the player retry and watchdog
 *  paths take over instead of a silent infinite loop. */
internal const val LIVE_RECONNECT_BASE_DELAY_MS = 300L
internal const val LIVE_RECONNECT_MAX_DELAY_MS = 4_000L
internal const val LIVE_RECONNECT_MAX_CONSECUTIVE_FAILURES = 4


internal fun shouldReconnectAfterEndOfInput(
    consecutiveFailures: Int,
    connectionDeliveredBytes: Boolean,
): Boolean = connectionDeliveredBytes &&
    consecutiveFailures < LIVE_RECONNECT_MAX_CONSECUTIVE_FAILURES

internal fun liveReconnectBackoffMillis(consecutiveFailures: Int): Long = when {
    consecutiveFailures <= 0 -> 0L
    else -> (LIVE_RECONNECT_BASE_DELAY_MS shl (consecutiveFailures - 1).coerceIn(0, 4))
        .coerceAtMost(LIVE_RECONNECT_MAX_DELAY_MS)
}

/** Diagnostics summary that never carries query parameters such as MAC,
 *  stream or play tokens that live inside private IPTV URLs. */
internal fun liveConnectionSummary(value: String): String {
    val uri = runCatching { URI(value) }.getOrNull() ?: return "unknown"
    val host = uri.host ?: return "scheme=${uri.scheme}"
    return "${uri.scheme}://$host"
}

internal fun canReconnectLiveRequest(contentType: String?, position: Long, length: Long, httpMethod: Int): Boolean =
    contentType?.substringBefore(';')?.trim()?.equals("video/mp2t", ignoreCase = true) == true &&
        position == 0L && length == -1L && httpMethod == 1
