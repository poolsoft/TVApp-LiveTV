package com.tvapp.livetv.web

import android.content.Context
import android.net.Uri
import com.tvapp.livetv.R
import com.tvapp.livetv.data.IptvImportProgress
import com.tvapp.livetv.data.IptvImportStage
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.data.IptvSourceSummary
import com.tvapp.livetv.diagnostics.CrashReportStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

internal class IptvWebManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val operationGate: IptvOperationGate,
    private val onState: (busy: Boolean, message: String, changed: Boolean) -> Unit,
    private val onExpired: () -> Unit,
) {
    private val repository = IptvRepository(context)
    private var server: IptvWebServer? = null
    private var expiry: Job? = null
    private var stopped = false
    @Volatile private var message = context.getString(R.string.web_ready)
    @Volatile private var sources: List<IptvSourceSummary> = emptyList()

    fun updateLocalSources(loaded: List<IptvSourceSummary>) {
        sources = loaded
    }

    fun updateLocalProgress(status: String) {
        message = status
    }

    suspend fun start(): Pair<String, String> {
        val started = withContext(Dispatchers.IO) {
            sources = repository.sources()
            val addresses = NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
            val address = addresses.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?: error(context.getString(R.string.web_no_network))
            val labels = JSONObject()
            LABELS.forEach { (key, id) -> labels.put(key, context.getString(id)) }
            val page = context.resources.openRawResource(R.raw.iptv_web).bufferedReader().use { it.readText() }
                .replace("__LABELS__", labels.toString().replace("<", "\\u003c"))
            IptvWebServer(address, page, ::api).also {
                synchronized(this@IptvWebManager) {
                    if (stopped) { it.close(); throw CancellationException("Screen closed") }
                    server = it
                }
            }
        }
        started.start()
        expiry = scope.launch { delay(15 * 60_000L); stop(); onExpired() }
        return started.url to started.pairingCode
    }

    fun stop() {
        synchronized(this) { stopped = true; server?.close(); server = null }
        expiry?.cancel()
        expiry = null
    }

    private suspend fun api(path: String, data: JSONObject): WebReply {
        if (path == "/state") {
            val rows = JSONArray()
            sources.forEach { summary ->
                val source = summary.source
                rows.put(JSONObject().put("id", source.id).put("name", source.name)
                    .put("kind", source.kind).put("channels", summary.channelCount)
                    .put("selected", summary.selectedChannelCount).put("updated", source.lastUpdatedAt)
                    .put("url", if (source.kind == IptvRepository.KIND_URL) source.location else ""))
            }
            return WebReply(200, JSONObject().put("sources", rows).put("busy", operationGate.isBusy).put("message", message).toString())
        }
        return withContext(Dispatchers.Main.immediate) {
            if (operationGate.isBusy || server == null) return@withContext WebReply(409)
            val action = data.optString("action")
            val kind = data.optString("kind")
            if (action !in listOf("add", "update", "delete", "rename") ||
                (action == "rename" && (data.optString("name").isBlank() || data.optString("name").length > 200)) ||
                (action == "add" && (kind !in listOf("URL", "DOCUMENT", "XTREAM", "STALKER") ||
                    data.optString("name").isBlank() || data.optString("name").length > 200))) {
                return@withContext WebReply(400)
            }
            if (!operationGate.tryAcquire()) return@withContext WebReply(409)
            val sourceName = if (action == "add") data.optString("name") else
                sources.firstOrNull { it.source.id == data.optLong("id") }?.source?.name.orEmpty()
            message = context.getString(R.string.iptv_source_progress, sourceName,
                context.getString(R.string.iptv_import_connecting))
            onState(true, message, false)
            scope.launch {
                val progressUi = launch {
                    while (true) { delay(500); onState(true, message, false) }
                }
                var changed = false
                try {
                    val resultMessage = withContext(Dispatchers.IO) {
                        execute(action, kind, data, sourceName).also { sources = repository.sources() }
                    }
                    message = resultMessage
                    changed = true
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) {
                    CrashReportStore(context).recordDebug("IPTV_WEB FAILURE | ${error.javaClass.simpleName}")
                    message = context.getString(R.string.web_operation_failed)
                } finally {
                    progressUi.cancel()
                    operationGate.release()
                    onState(false, message, changed)
                }
            }
            WebReply(202)
        }
    }

    private suspend fun execute(action: String, kind: String, data: JSONObject, sourceName: String): String {
        val progress: (IptvImportProgress) -> Unit = { value ->
            val stage = when (value.stage) {
                IptvImportStage.CONNECTING -> context.getString(R.string.iptv_import_connecting)
                IptvImportStage.READING -> context.getString(R.string.iptv_import_reading, value.processedChannels)
                IptvImportStage.SAVING -> context.getString(R.string.iptv_import_saving)
                IptvImportStage.FINISHING -> context.getString(R.string.iptv_import_finishing)
            }
            message = context.getString(R.string.iptv_source_progress, sourceName, stage)
        }
        val imported = if (action == "add") {
            val name = data.getString("name").trim()
            when (kind) {
                IptvRepository.KIND_URL -> repository.importUrl(httpUrl(data), name, progress)
                IptvRepository.KIND_XTREAM -> repository.importXtream(httpUrl(data),
                    data.getString("username"), data.getString("password"), name, onProgress = progress)
                IptvRepository.KIND_STALKER -> repository.importStalker(httpUrl(data),
                    data.getString("mac"), name, onProgress = progress)
                else -> {
                    val directory = File(context.filesDir, "web-playlists").apply { mkdirs() }
                    val file = File(directory, "${UUID.randomUUID()}.m3u")
                    try {
                        file.writeText(data.getString("content"))
                        repository.importDocument(Uri.fromFile(file), name, onProgress = progress)
                    } catch (error: Exception) {
                        // Cancellation can arrive after commit; never remove a referenced playlist.
                        withContext(NonCancellable) {
                            val saved = runCatching {
                                repository.sources().any { it.source.location == Uri.fromFile(file).toString() }
                            }.getOrDefault(true)
                            if (!saved) file.delete()
                        }
                        throw error
                    }
                }
            }
        } else {
            val source = sources.firstOrNull { it.source.id == data.optLong("id", -1) }?.source
                ?: error("Missing source")
            if (action == "delete") {
                repository.delete(source)
                return context.getString(R.string.iptv_source_deleted)
            }
            if (action == "rename") {
                repository.rename(source, data.getString("name"))
                return context.getString(R.string.iptv_source_renamed)
            }
            require(source.kind == IptvRepository.KIND_URL)
            repository.refresh(source, progress)
        }
        return context.getString(R.string.iptv_source_import_complete, imported.sourceName, imported.channelCount)
    }

    private fun httpUrl(data: JSONObject): String = data.getString("url").trim().also {
        val uri = java.net.URI(it)
        require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank())
    }

    companion object {
        private val LABELS = mapOf(
            "title" to R.string.web_manage, "pair" to R.string.web_pair,
            "code" to R.string.web_code, "security" to R.string.web_security,
            "name" to R.string.source_name_hint, "url" to R.string.iptv_url,
            "username" to R.string.username_hint, "password" to R.string.password_hint,
            "mac" to R.string.mac_address_hint, "add" to R.string.import_action,
            "saved" to R.string.saved_iptv_sources, "update" to R.string.update,
            "rename" to R.string.rename_iptv_source, "save" to R.string.save, "cancel" to R.string.cancel,
            "delete" to R.string.delete, "file" to R.string.source_type_m3u_file,
            "URL" to R.string.source_type_m3u_url, "DOCUMENT" to R.string.source_type_m3u_file,
            "XTREAM" to R.string.source_type_xtream, "STALKER" to R.string.source_type_stalker,
            "confirm" to R.string.web_delete_confirm, "failed" to R.string.web_operation_failed,
            "offline" to R.string.web_disconnected, "empty" to R.string.web_empty,
            "counts" to R.string.web_counts, "uploadLimit" to R.string.web_upload_limit,
        )
    }
}
