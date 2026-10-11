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
    fun create(
        userAgent: String?,
        referrer: String?,
        origin: String? = null,
        liveEdge: Boolean = false,
        reconnectLive: Boolean = false,
        livePrefetchBytes: Int = 8 * 1024 * 1024,
        diagnostics: (String) -> Unit = {},
    ): DataSource.Factory {
        // Live MPEG-TS streams can sit silent longer than VOD between reads
        // (pause filler, source hiccups), so they get a longer read timeout
        // instead of failing the open and waiting for watchdog recovery.
        val okHttp = OkHttpDataSource.Factory(if (liveEdge) LIVE_HTTP_CLIENT else HTTP_CLIENT)
            .setUserAgent(userAgent?.takeIf(String::isNotBlank) ?: DEFAULT_USER_AGENT)
        okHttp.setDefaultRequestProperties(buildMap {
            referrer?.takeIf(String::isNotBlank)?.let { put("Referer", it) }
            origin?.takeIf(String::isNotBlank)?.let { put("Origin", it) }
        })
        val resolved = ResolvingDataSource.Factory(okHttp) { dataSpec ->
            val resolved = StalkerStreamUri.resolve(dataSpec.uri)
            if (resolved == null) dataSpec else dataSpec.withUri(Uri.parse(resolved))
        }
        if (!liveEdge || !reconnectLive) return resolved
        val reconnecting = ReconnectingHttpDataSource.factory(resolved, diagnostics)
        return DataSource.Factory { BufferedLiveDataSource(reconnecting, livePrefetchBytes, diagnostics) }
    }

    fun createExtractors(): ExtractorsFactory = DefaultExtractorsFactory()
        .setConstantBitrateSeekingEnabled(true)
        .setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
        )
        .setTsExtractorTimestampSearchBytes(TS_TIMESTAMP_SEARCH_BYTES)
        .setAdtsExtractorFlags(androidx.media3.extractor.ts.AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
        .setMp3ExtractorFlags(
            androidx.media3.extractor.mp3.Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or
                androidx.media3.extractor.mp3.Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING,
        )
        .setFragmentedMp4ExtractorFlags(
            androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_TFDT_BOX or
                androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS,
        )
        .setMatroskaExtractorFlags(androidx.media3.extractor.mkv.MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES)

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val LIVE_READ_TIMEOUT_MS = 30_000
    private const val TS_PACKET_SIZE = 188
    private const val TS_TIMESTAMP_SEARCH_BYTES = 1_500 * TS_PACKET_SIZE
    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val HTTP_CLIENT = sharedClient(READ_TIMEOUT_MS)
    private val LIVE_HTTP_CLIENT = sharedClient(LIVE_READ_TIMEOUT_MS)

    private fun sharedClient(readTimeoutMs: Int) = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
}
