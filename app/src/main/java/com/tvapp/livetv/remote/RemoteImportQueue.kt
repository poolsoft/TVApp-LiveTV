package com.tvapp.livetv.remote

import android.content.Context
import com.tvapp.livetv.data.IptvRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * REMOTEEDIT-006: remote IPTV source import queue. A phone/web client can
 * request a playlist import; the request is queued and executed by the TV app
 * with its existing paged import pipeline ([IptvRepository.importUrl]), so
 * large catalogs (15k+) flow through the tested path. The queue is in-memory:
 * a pending request dies with the process, which is acceptable for v1 because
 * the client can simply resend; executing/finished state is observable.
 *
 * Source URLs and any credentials inside them are NEVER logged.
 */
class RemoteImportQueue(context: Context) {

    data class Request(
        val id: Long,
        val name: String,
        val url: String,
        val status: Status,
        val requestedAt: Long,
        val importedChannels: Int,
        val error: String?,
    ) {
        enum class Status { PENDING, RUNNING, DONE, FAILED }
    }

    private val repository = IptvRepository(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextId = AtomicLong(0)
    private val states = AtomicReference<Map<Long, MutableState>>(emptyMap())

    private class MutableState(
        val name: String,
        val url: String,
        val requestedAt: Long,
        @Volatile var status: Request.Status,
        @Volatile var importedChannels: Int,
        @Volatile var error: String?,
    )

    /** Queues an import request and starts executing it. Returns the request id. */
    fun enqueue(name: String, url: String): Long {
        val trimmedName = name.trim().take(64).ifBlank { "Uzak liste" }
        val trimmedUrl = url.trim()
        require(trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            "Yalnız http(s) adresleri kabul edilir."
        }
        val id = nextId.incrementAndGet()
        states.set(states.get() + (id to MutableState(trimmedName, trimmedUrl, System.currentTimeMillis(), Request.Status.PENDING, 0, null)))
        scope.launch {
            val state = states.get()[id] ?: return@launch
            state.status = Request.Status.RUNNING
            runCatching {
                val result = repository.importUrl(trimmedUrl, trimmedName) { progress ->
                    state.importedChannels = progress.processedChannels
                }
                state.importedChannels = result.channelCount
                state.status = Request.Status.DONE
            }.onFailure { error ->
                state.error = error.message?.take(200) ?: error.javaClass.simpleName
                state.status = Request.Status.FAILED
            }
        }
        return id
    }

    fun snapshot(id: Long): Request? = states.get()[id]?.let { state ->
        Request(id, state.name, REDACTED, state.status, state.requestedAt, state.importedChannels, state.error)
    }

    fun all(): List<Request> = states.get().entries
        .sortedBy { it.key }
        .map { (id, state) ->
            Request(id, state.name, REDACTED, state.status, state.requestedAt, state.importedChannels, state.error)
        }

    fun cancelAll() {
        scope.cancel()
    }

    private companion object {
        /** Snapshot payloads never carry the playlist URL: it may embed credentials. */
        const val REDACTED = "(gizli)"
    }
}
