package com.tvapp.livetv.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class IptvPlaybackHealthTest {
    @Test
    fun classifiesCommonPlaybackFailures() {
        assertEquals(
            IptvPlaybackFailureClass.NETWORK,
            classifyIptvPlaybackFailure("ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"),
        )
        assertEquals(
            IptvPlaybackFailureClass.HTTP,
            classifyIptvPlaybackFailure("ERROR_CODE_IO_BAD_HTTP_STATUS"),
        )
        assertEquals(
            IptvPlaybackFailureClass.DECODER,
            classifyIptvPlaybackFailure("ERROR_CODE_DECODING_FAILED"),
        )
        assertEquals(
            IptvPlaybackFailureClass.TIMEOUT,
            classifyIptvPlaybackFailure("ERROR_CODE_TIMEOUT"),
        )
        assertEquals(
            IptvPlaybackFailureClass.UNKNOWN,
            classifyIptvPlaybackFailure("ERROR_CODE_UNSPECIFIED"),
        )
    }

    @Test
    fun watchdogSeparatesStartupBufferAndStall() {
        assertEquals(
            IptvRecoveryReason.FIRST_FRAME_TIMEOUT,
            reason(expectsVideo = true, startupMillis = 15_000),
        )
        assertEquals(
            IptvRecoveryReason.BUFFERING_TIMEOUT,
            reason(expectsVideo = false, isBuffering = true, bufferingMillis = 25_000),
        )
        assertEquals(
            IptvRecoveryReason.PLAYBACK_STALLED,
            reason(
                expectsVideo = true,
                firstFrameRendered = true,
                isReadyAndPlaying = true,
                stalledProgressMillis = 12_000,
                staleFrameMillis = 12_000,
            ),
        )
    }

    @Test
    fun watchdogDoesNotTreatRadioOrShortBufferAsMissingVideo() {
        assertEquals(null, reason(expectsVideo = false, startupMillis = 60_000))
        assertEquals(null, reason(expectsVideo = true, isBuffering = true, bufferingMillis = 5_000))
    }

    private fun reason(
        expectsVideo: Boolean,
        firstFrameRendered: Boolean = false,
        startupMillis: Long = 0,
        isBuffering: Boolean = false,
        bufferingMillis: Long = 0,
        isReadyAndPlaying: Boolean = false,
        stalledProgressMillis: Long = 0,
        staleFrameMillis: Long? = null,
    ) = decideIptvWatchdogReason(
        IptvWatchdogObservation(
            expectsVideo = expectsVideo,
            firstFrameRendered = firstFrameRendered,
            startupMillis = startupMillis,
            isIdle = false,
            isBuffering = isBuffering,
            bufferingMillis = bufferingMillis,
            isReadyAndPlaying = isReadyAndPlaying,
            stalledProgressMillis = stalledProgressMillis,
            staleFrameMillis = staleFrameMillis,
        ),
        firstFrameTimeoutMillis = 15_000,
        bufferingTimeoutMillis = 25_000,
        stallTimeoutMillis = 12_000,
    )
}
