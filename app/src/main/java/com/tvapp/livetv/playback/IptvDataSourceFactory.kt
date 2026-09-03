package com.tvapp.livetv.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import com.tvapp.livetv.data.StalkerStreamUri

@OptIn(UnstableApi::class)
object IptvDataSourceFactory {
    fun create(userAgent: String?, referrer: String?): DataSource.Factory {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(userAgent?.takeIf(String::isNotBlank) ?: DEFAULT_USER_AGENT)
        referrer?.takeIf(String::isNotBlank)?.let {
            http.setDefaultRequestProperties(mapOf("Referer" to it))
        }
        return ResolvingDataSource.Factory(http) { dataSpec ->
            val resolved = StalkerStreamUri.resolve(dataSpec.uri)
            if (resolved == null) dataSpec else dataSpec.withUri(Uri.parse(resolved))
        }
    }

    private const val DEFAULT_USER_AGENT = "TVApp/0.1 AndroidTV"
}
