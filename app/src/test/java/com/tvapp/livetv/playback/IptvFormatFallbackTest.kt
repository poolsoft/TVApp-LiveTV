package com.tvapp.livetv.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.*
import org.junit.Test

class IptvFormatFallbackTest {
    @Test fun malformedHlsCanRetryOnce() {
        val code = PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
        assertTrue(IptvFormatFallback.allowed(code, "/live/channel.M3U8", false, false))
        assertFalse(IptvFormatFallback.allowed(code, "/live/channel.m3u8", true, false))
    }
    @Test fun networkCodecDrmAndUnrelatedUrlsDoNotFallback() {
        assertFalse(IptvFormatFallback.allowed(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, "/a.m3u8", false, false))
        assertFalse(IptvFormatFallback.allowed(PlaybackException.ERROR_CODE_DECODING_FAILED, "/a.m3u8", false, false))
        assertFalse(IptvFormatFallback.allowed(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, "/a.m3u8", false, true))
        assertFalse(IptvFormatFallback.allowed(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, "/live.php", false, false))
    }
}
