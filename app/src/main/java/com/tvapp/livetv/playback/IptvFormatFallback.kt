package com.tvapp.livetv.playback

import androidx.media3.common.PlaybackException

object IptvFormatFallback {
    fun allowed(errorCode: Int, path: String?, attempted: Boolean, hasDrm: Boolean): Boolean =
        !attempted && !hasDrm &&
            (errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
                errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) &&
            (path?.endsWith(".m3u8", true) == true || path?.endsWith(".m3u", true) == true)
}
