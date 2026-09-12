package com.tvapp.livetv.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.tvapp.livetv.data.StalkerStreamUri
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
object IptvDataSourceFactory {
    fun create(userAgent: String?, referrer: String?): DataSource.Factory {
        val http = OkHttpDataSource.Factory(HTTP_CLIENT)
            .setUserAgent(userAgent?.takeIf(String::isNotBlank) ?: DEFAULT_USER_AGENT)
        referrer?.takeIf(String::isNotBlank)?.let {
            http.setDefaultRequestProperties(mapOf("Referer" to it))
        }
        return ResolvingDataSource.Factory(http) { dataSpec ->
            val resolved = StalkerStreamUri.resolve(dataSpec.uri)
            if (resolved == null) dataSpec else dataSpec.withUri(Uri.parse(resolved))
        }
    }

    fun createExtractors(): ExtractorsFactory = DefaultExtractorsFactory()
        .setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
        )
        .setTsExtractorTimestampSearchBytes(TS_TIMESTAMP_SEARCH_BYTES)

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val TS_PACKET_SIZE = 188
    private const val TS_TIMESTAMP_SEARCH_BYTES = 1_500 * TS_PACKET_SIZE
    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val HTTP_CLIENT = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
}
