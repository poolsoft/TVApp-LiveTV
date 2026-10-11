package com.tvapp.livetv.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException

/** Wraps an HTTP [DataSource] so a source that closes the connection on a
 *  regular cadence (PHP live endpoints often end the response after a fixed
 *  byte or time budget) is transparently reconnected. The progressive
 *  extractor then sees one byte stream without a new MediaSource period.
 *
 *  Reconnects reuse the original [DataSpec]; any upstream resolver (for
 *  example Stalker) runs again for schemes that need a fresh link. A
 *  connection that fails or delivers no bytes consumes the reconnect
 *  budget; once the budget is exhausted the original error is rethrown so
 *  ExoPlayer retry and watchdog paths take over. The wrapper itself never
 *  inspects MPEG-TS packets, so no claim of exact duplicate removal is
 *  made: overlap or gaps at the source live edge remain source behavior. */
@UnstableApi
class ReconnectingHttpDataSource private constructor(
    private val upstream: DataSource.Factory,
    private val diagnostics: (String) -> Unit,
) : DataSource {

    private class Factory(
        private val upstream: DataSource.Factory,
        private val diagnostics: (String) -> Unit,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ReconnectingHttpDataSource(upstream, diagnostics)
    }

    companion object {
        fun factory(
            upstream: DataSource.Factory,
            diagnostics: (String) -> Unit,
        ): DataSource.Factory = Factory(upstream, diagnostics)
    }

    @Volatile private var delegate: DataSource? = null
    private val transferListeners = mutableListOf<TransferListener>()
    private var spec: DataSpec? = null
    @Volatile
    private var closed = false
    private var reconnectAllowed = false
    private var connectionBytes = 0L
    private var connectionOpenedAt = 0L
    private var consecutiveFailures = 0
    private var reconnects = 0

    override fun open(dataSpec: DataSpec): Long {
        closed = false
        spec = dataSpec
        consecutiveFailures = 0
        reconnects = 0
        val available = openConnection()
        val contentType = responseHeaders.entries.firstOrNull { it.key.equals("Content-Type", true) }?.value?.firstOrNull()
        reconnectAllowed = canReconnectLiveRequest(contentType, dataSpec.position, dataSpec.length, dataSpec.httpMethod)
        if (available == 0L) {
            closeConnection("empty")
            throw IOException("Live source opened with an empty body")
        }
        return if (reconnectAllowed) C.LENGTH_UNSET.toLong() else available
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        while (true) {
            val source = delegate ?: throw IOException("ReconnectingHttpDataSource is closed")
            val read = try {
                source.read(buffer, offset, length)
            } catch (error: IOException) {
                closeConnection("error")
                if (closed) return C.RESULT_END_OF_INPUT
                if (!reconnectAllowed) throw error
                consecutiveFailures++
                if (consecutiveFailures >= LIVE_RECONNECT_MAX_CONSECUTIVE_FAILURES) throw error
                log(
                    "read_error=${error.javaClass.simpleName} " +
                        "delayMs=${liveReconnectBackoffMillis(consecutiveFailures)}",
                )
                if (!reconnect(deliveredBytes = false, fatalError = error)) return C.RESULT_END_OF_INPUT
                continue
            }
            if (read != C.RESULT_END_OF_INPUT) {
                if (read > 0) {
                    connectionBytes += read
                    consecutiveFailures = 0
                }
                return read
            }
            val deliveredBytes = connectionBytes > 0
            closeConnection("eof")
            if (closed || !reconnectAllowed) return C.RESULT_END_OF_INPUT
            if (!reconnect(deliveredBytes, fatalError = null)) return C.RESULT_END_OF_INPUT
        }
    }

    override fun close() {
        closed = true
        closeConnection("close")
        spec = null
    }

    override fun getUri() = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        delegate?.responseHeaders ?: emptyMap()

    override fun addTransferListener(transferListener: TransferListener) {
        if (transferListener !in transferListeners) {
            transferListeners += transferListener
            delegate?.addTransferListener(transferListener)
        }
    }

    /** Attaches a fresh connection within the reconnect budget. EOF reconnects
     *  (fatalError == null) end the stream normally when the budget runs out;
     *  error reconnects rethrow so ExoPlayer retry and watchdog handle it. */
    private fun reconnect(deliveredBytes: Boolean, fatalError: IOException?): Boolean {
        if (fatalError == null && !shouldReconnectAfterEndOfInput(consecutiveFailures, deliveredBytes)) {
            if (deliveredBytes) log("exhausted") else log("eof_without_bytes")
            return false
        }
        while (consecutiveFailures < LIVE_RECONNECT_MAX_CONSECUTIVE_FAILURES) {
            if (!sleepBeforeReconnect()) return false
            try {
                openConnection()
                return true
            } catch (error: IOException) {
                closeConnection("error")
                if (closed) return false
                consecutiveFailures++
                if (consecutiveFailures >= LIVE_RECONNECT_MAX_CONSECUTIVE_FAILURES) {
                    if (fatalError != null) throw error
                    log("exhausted")
                    return false
                }
            }
        }
        return false
    }

    private fun sleepBeforeReconnect(): Boolean {
        val delay = liveReconnectBackoffMillis(consecutiveFailures)
        if (delay <= 0L) return !closed
        return try {
            Thread.sleep(delay)
            !closed
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }
    private fun openConnection(): Long {
        if (closed) throw IOException("Live connection cancelled")
        val request = spec ?: throw IOException("ReconnectingHttpDataSource has no spec")
        val source = upstream.createDataSource()
        transferListeners.forEach(source::addTransferListener)
        val startedAt = System.currentTimeMillis()
        return try {
            val available = source.open(request)
            synchronized(this) {
                if (closed) { source.close(); throw IOException("Live connection cancelled") }
                delegate = source
            }
            connectionBytes = 0
            connectionOpenedAt = startedAt
            reconnects++
            log(
                "opened server=${liveConnectionSummary(request.uri.toString())} " +
                    "length=$available openMs=${System.currentTimeMillis() - startedAt}",
            )
            available
        } catch (error: IOException) {
            source.close()
            throw error
        }
    }

    private fun closeConnection(reason: String) {
        val source = synchronized(this) { delegate.also { delegate = null } } ?: return
        val durationMs = if (connectionOpenedAt > 0L) System.currentTimeMillis() - connectionOpenedAt else 0L
        source.close()
        log("closed reason=$reason bytes=$connectionBytes durationMs=$durationMs reconnects=$reconnects")
        connectionBytes = 0
        connectionOpenedAt = 0L
    }

    private fun log(message: String) = diagnostics("IPTV_LIVE_RECONNECT | $message")
}
