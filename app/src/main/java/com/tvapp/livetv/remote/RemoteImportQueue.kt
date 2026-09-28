package com.tvapp.livetv.remote

import android.content.Context
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.data.XmlTvRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * REMOTEEDIT-006: remote source import queue. A phone/web client can request
 * an IPTV playlist or XMLTV EPG import; the request is queued and executed by
 * the TV app with its existing pipelines ([IptvRepository.importUrl] and
 * [XmlTvRepository.importUrl]), so large catalogs (15k+) flow through the
 * tested paths. The queue is in-memory: a pending request dies with the
 * process, which is acceptable for v1 because the client can simply resend;
 * executing/finished state is observable.
 *
 * Source URLs and any credentials inside them are NEVER logged.
 *
 * Listener contract: [listener] fires on the IO dispatcher for every state
 * change so the TV can surface a live OSD progress card without polling.
 */
class RemoteImportQueue(context: Context) {

    data class Request(
        val id: Long,
        val name: String,
        val kind: Kind,
        val url: String,
        val status: Status,
        val requestedAt: Long,
        val importedChannels: Int,
        val error: String?,
    ) {
        enum class Status { PENDING, RUNNING, DONE, FAILED, CANCELLED }
        enum class Kind { IPTV, XMLTV }
    }

    /** Listener contract: fires on every state transition with the latest state. */
    fun interface Listener {
        fun onImportStateChanged(request: Request)
    }

    @Volatile
    var listener: Listener? = null

    private val repository = IptvRepository(context)
    private val xmlTvRepository = XmlTvRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextId = AtomicLong(0)
    private val states = AtomicReference<Map<Long, MutableState>>(emptyMap())

    private class MutableState(
        val name: String,
        val kind: Request.Kind,
        val url: String,
        val requestedAt: Long,
        @Volatile var status: Request.Status,
        @Volatile var importedChannels: Int,
        @Volatile var error: String?,
        @Volatile var cancelRequested: Boolean = false,
    )

    /** Queues an import request and starts executing it. Returns the request id. */
    fun enqueue(name: String, url: String, kind: Request.Kind = Request.Kind.IPTV): Long {
        val trimmedName = name.trim().take(64).ifBlank { defaultName(kind) }
        val trimmedUrl = url.trim()
        require(trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            "Yalnız http(s) adresleri kabul edilir."
        }
        val id = nextId.incrementAndGet()
        states.set(
            states.get() + (
                id to MutableState(
                    trimmedName,
                    kind,
                    trimmedUrl,
                    System.currentTimeMillis(),
                    Request.Status.PENDING,
                    0,
                    null,
                )
                ),
        )
        notify(id)
        scope.launch {
            val state = states.get()[id] ?: return@launch
            if (state.status == Request.Status.CANCELLED) return@launch
            state.status = Request.Status.RUNNING
            notify(id)
            runCatching {
                val imported = when (kind) {
                    Request.Kind.IPTV -> repository.importUrl(trimmedUrl, trimmedName) { progress ->
                        state.importedChannels = progress.processedChannels
                        ensureActive(id)
                        notify(id)
                    }.channelCount

                    Request.Kind.XMLTV -> xmlTvRepository.importUrl(trimmedUrl, trimmedName) { progress ->
                        state.importedChannels = progress.programsImported
                        ensureActive(id)
                        notify(id)
                    }
                }
                state.importedChannels = imported
                state.status = Request.Status.DONE
            }.onFailure { error ->
                if (error is ImportCancelledException) {
                    state.error = null
                    state.status = Request.Status.CANCELLED
                } else {
                    state.error = error.message?.take(200) ?: error.javaClass.simpleName
                    state.status = Request.Status.FAILED
                }
            }
            notify(id)
        }
        return id
    }

    /** Requests cancellation. Queued and running requests move to CANCELLED;
     *  the running pipeline stops at its next progress callback. Finished
     *  requests are unaffected and return false. */
    fun cancel(id: Long): Boolean {
        val state = states.get()[id] ?: return false
        when (state.status) {
            Request.Status.PENDING, Request.Status.RUNNING -> {
                state.cancelRequested = true
                state.status = Request.Status.CANCELLED
                notify(id)
                return true
            }

            else -> return false
        }
    }

    /** True when [cancel] marked this id after the last notify. */
    private fun ensureActive(id: Long) {
        val state = states.get()[id] ?: return
        if (state.cancelRequested && state.status == Request.Status.CANCELLED) {
            throw ImportCancelledException()
        }
    }

    fun snapshot(id: Long): Request? = states.get()[id]?.let { state ->
        Request(id, state.name, state.kind, REDACTED, state.status, state.requestedAt, state.importedChannels, state.error)
    }

    fun all(): List<Request> = states.get().entries
        .sortedBy { it.key }
        .map { (id, state) ->
            Request(id, state.name, state.kind, REDACTED, state.status, state.requestedAt, state.importedChannels, state.error)
        }

    fun cancelAll() {
        scope.cancel()
    }

    private fun notify(id: Long) {
        val request = snapshot(id) ?: return
        listener?.onImportStateChanged(request)
    }

    private fun defaultName(kind: Request.Kind): String = when (kind) {
        Request.Kind.IPTV -> "Uzak liste"
        Request.Kind.XMLTV -> "Uzak EPG"
    }

    /** Thrown into the import pipeline to unwind it after a cancel request. */
    class ImportCancelledException : Exception("İptal edildi")

    private companion object {
        /** Snapshot payloads never carry the playlist URL: it may embed credentials. */
        const val REDACTED = "(gizli)"
    }
}
