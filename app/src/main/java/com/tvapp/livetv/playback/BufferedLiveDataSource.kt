package com.tvapp.livetv.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InputStream

/** Each open owns its producer and upstream, so old channel downloads cannot feed a new channel. */
@UnstableApi
internal class BufferedLiveDataSource(
    private val upstream: DataSource.Factory,
    private val capacityBytes: Int,
    private val diagnostics: (String) -> Unit,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    @Volatile private var source: DataSource? = null
    @Volatile private var buffer: BufferedLiveInputStream? = null
    @Volatile private var closed = false
    private var openedUri: Uri? = null
    private var headers: Map<String, List<String>> = emptyMap()

    override fun open(dataSpec: DataSpec): Long {
        closed = false
        val connection = upstream.createDataSource()
        source = connection
        listeners.forEach(connection::addTransferListener)
        try {
            val length = connection.open(dataSpec)
            openedUri = connection.uri
            headers = connection.responseHeaders
            if (closed) throw IOException("Live download cancelled")
            val contentType = headers.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.firstOrNull()
            if (canReconnectLiveRequest(contentType, dataSpec.position, dataSpec.length, dataSpec.httpMethod)) {
                val input = object : InputStream() {
                    override fun read(): Int {
                        val one = ByteArray(1)
                        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
                    }
                    override fun read(bytes: ByteArray, offset: Int, count: Int) = connection.read(bytes, offset, count)
                    override fun close() = connection.close()
                }
                val prefetched = BufferedLiveInputStream(input, capacityBytes)
                buffer = prefetched
                if (closed) { prefetched.close(); throw IOException("Live download cancelled") }
                diagnostics("IPTV_LIVE_PREFETCH | capacityBytes=$capacityBytes")
            }
            return length
        } catch (error: Exception) {
            try { close() } catch (closeError: Exception) { error.addSuppressed(closeError) }
            throw error
        }
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (closed) throw IOException("Live download closed")
        return buffer?.read(bytes, offset, length)
            ?: (source ?: throw IOException("Live download not opened")).read(bytes, offset, length)
    }

    override fun close() {
        closed = true
        val oldBuffer = buffer
        val oldSource = source
        buffer = null
        source = null
        openedUri = null
        headers = emptyMap()
        try { oldBuffer?.close() } finally { oldSource?.close() }
    }

    override fun getUri(): Uri? = openedUri
    override fun getResponseHeaders() = headers
    override fun addTransferListener(listener: TransferListener) {
        if (listener !in listeners) {
            listeners += listener
            source?.addTransferListener(listener)
        }
    }
}
