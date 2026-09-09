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
}
