package com.tvapp.livetv.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test

class IjkPlaybackFailureTest {
    @Test
    fun classifiesNativeErrorsWithoutTreatingEverythingAsDecoderFailure() {
        assertEquals(IptvPlaybackFailureClass.TIMEOUT, classifyIjkPlaybackFailure(-110, 0))
        assertEquals(IptvPlaybackFailureClass.NETWORK, classifyIjkPlaybackFailure(-1004, 0))
        assertEquals(IptvPlaybackFailureClass.DECODER, classifyIjkPlaybackFailure(-1010, 0))
        assertEquals(IptvPlaybackFailureClass.SOURCE, classifyIjkPlaybackFailure(-1007, 0))
        assertEquals(IptvPlaybackFailureClass.HTTP, classifyIjkPlaybackFailure(1, 403))
        assertEquals(IptvPlaybackFailureClass.UNKNOWN, classifyIjkPlaybackFailure(1, 0))
    }

    @Test
    fun mapsIjkFailureClassesToMedia3CompatibleErrorCodes() {
        assertEquals(
            PlaybackException.ERROR_CODE_TIMEOUT,
            ijkPlaybackExceptionCode(IptvPlaybackFailureClass.TIMEOUT),
        )
        assertEquals(
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            ijkPlaybackExceptionCode(IptvPlaybackFailureClass.DECODER),
        )
    }
}
