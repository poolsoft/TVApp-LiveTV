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
    private val channelRepository by lazy { com.tvapp.livetv.data.ChannelRepository(context) }
    private val parentalControlStore by lazy { com.tvapp.livetv.settings.ParentalControlStore(context) }
    private var server: IptvWebServer? = null
    private var expiry: Job? = null
    private val sessionTimeout = WebSessionTimeout()
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
            IptvWebServer(address, page, onPaired = sessionTimeout::touch, api = ::api).also {
                synchronized(this@IptvWebManager) {
                    if (stopped) { it.close(); throw CancellationException("Screen closed") }
                    server = it
                }
            }
        }
        started.start()
        sessionTimeout.touch()
        expiry = scope.launch {
            while (true) {
                delay(1_000)
                if (sessionTimeout.isExpired(operationGate.isBusy)) {
                    stop()
                    onExpired()
                    break
                }
            }
        }
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
                    .put("kind", source.kind).put("enabled", source.enabled).put("channels", summary.channelCount)
                    .put("selected", summary.selectedChannelCount).put("updated", source.lastUpdatedAt)
                    .put("url", if (source.kind == IptvRepository.KIND_URL) source.location else ""))
            }
            return WebReply(200, JSONObject().put("sources", rows).put("busy", operationGate.isBusy).put("message", message).toString())
        }
        val action = data.optString("action")
        if (action == "github_manifest") {
            sessionTimeout.touch()
            val user = data.optString("username", com.tvapp.livetv.data.GithubManifestRepository.DEFAULT_USER)
            val file = data.optString("file", com.tvapp.livetv.data.GithubManifestRepository.DEFAULT_FILE)
            val result = com.tvapp.livetv.data.GithubManifestRepository().fetchManifest(user, file)
            val itemsArray = JSONArray()
            result.getOrNull()?.forEach { item ->
                itemsArray.put(
                    JSONObject()
                        .put("name", item.name)
                        .put("url", item.url)
                        .put("poster", item.poster.orEmpty())
                        .put("description", item.description.orEmpty())
                        .put("format", item.sourceFormat.orEmpty())
                )
            }
            val replyJson = JSONObject()
                .put("success", result.isSuccess)
                .put("items", itemsArray)
            if (result.isFailure) {
                replyJson.put("error", result.exceptionOrNull()?.message.orEmpty())
            }
            return WebReply(200, replyJson.toString())
        }
        if (action == "toggle_source_enabled") {
            sessionTimeout.touch()
            val id = data.optLong("id")
            val targetSource = repository.sources().firstOrNull { it.source.id == id }?.source
            if (targetSource != null) {
                withContext(Dispatchers.IO) {
                    repository.setSourceEnabled(id, !targetSource.enabled)
                    sources = repository.sources()
                }
                onState(false, message, true)
                return WebReply(200, JSONObject().put("success", true).put("enabled", !targetSource.enabled).toString())
            }
            return WebReply(404, JSONObject().put("error", "Source not found").toString())
        }
        if (action == "source_categories") {
            sessionTimeout.touch()
            val sourceId = data.optLong("sourceId")
            val cats = withContext(Dispatchers.IO) { repository.sourceCategories(sourceId) }
            val catArr = JSONArray()
            cats.forEach { catArr.put(it) }
            return WebReply(200, JSONObject().put("categories", catArr).toString())
        }
        if (action == "source_channels") {
            sessionTimeout.touch()
            val sourceId = data.optLong("sourceId")
            val rawCategory = data.optString("category")
            val category = if (rawCategory.isBlank() || rawCategory == "ALL") null else rawCategory
            val query = data.optString("query").trim()
            val selectedOnly = data.optBoolean("selectedOnly", false)
            val page = data.optInt("page", 0).coerceAtLeast(0)
            val pageSize = 100

            val (total, channels) = withContext(Dispatchers.IO) {
                val count = repository.selectionCount(sourceId, category, query, selectedOnly)
                val list = repository.selectionPage(sourceId, category, query, selectedOnly, pageSize, page * pageSize)
                count to list
            }

            val arr = JSONArray()
            channels.forEach { c ->
                arr.put(
                    JSONObject()
                        .put("key", c.sourceKey)
                        .put("name", c.displayName)
                        .put("group", c.groupTitle.orEmpty())
                        .put("selected", c.selected)
                        .put("logo", c.logoUrl.orEmpty())
                )
            }
            return WebReply(200, JSONObject()
                .put("total", total)
                .put("page", page)
                .put("pageSize", pageSize)
                .put("channels", arr)
                .toString())
        }
        if (action == "set_channel_selection") {
            sessionTimeout.touch()
            val selected = data.optBoolean("selected", true)
            val keysJson = data.optJSONArray("keys")
            val keys = mutableListOf<String>()
            if (keysJson != null) {
                for (i in 0 until keysJson.length()) {
                    val k = keysJson.optString(i)
                    if (k.isNotBlank()) keys.add(k)
                }
            }
            if (keys.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    repository.setChannelsSelected(keys, selected)
                    sources = repository.sources()
                }
                onState(false, message, true)
            }
            return WebReply(200, JSONObject().put("success", true).put("count", keys.size).toString())
        }
        if (action == "set_filtered_channel_selection") {
            sessionTimeout.touch()
            val sourceId = data.optLong("sourceId")
            val rawCategory = data.optString("category")
            val category = if (rawCategory.isBlank() || rawCategory == "ALL") null else rawCategory
            val query = data.optString("query").trim()
            val selected = data.optBoolean("selected", true)
            val count = withContext(Dispatchers.IO) {
                val affected = repository.setFilteredChannelsSelected(sourceId, category, query, selected)
                sources = repository.sources()
                affected
            }
            onState(false, message, true)
            return WebReply(200, JSONObject().put("success", true).put("count", count).toString())
        }
        if (action == "editor_categories") {
            sessionTimeout.touch()
            val allChannels = withContext(Dispatchers.IO) {
                channelRepository.channels(includeHidden = true).getOrElse { emptyList() }
            }
            val cats = allChannels.mapNotNull { it.groupTitle?.takeIf(String::isNotBlank) }
                .distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
            val arr = JSONArray()
            cats.forEach { arr.put(it) }
            return WebReply(200, JSONObject().put("categories", arr).toString())
        }
        if (action == "editor_channels") {
            sessionTimeout.touch()
            val rawCategory = data.optString("category")
            val category = if (rawCategory.isBlank() || rawCategory == "ALL") null else rawCategory
            val query = data.optString("query").trim().lowercase()
            val filter = data.optString("filter", "all")
            val page = data.optInt("page", 0).coerceAtLeast(0)
            val pageSize = 100

            val allChannels = withContext(Dispatchers.IO) {
                channelRepository.channels(includeHidden = true).getOrElse { emptyList() }
            }
            val lockedKeys = parentalControlStore.snapshot().lockedSourceKeys

            val filtered = allChannels.filter { ch ->
                if (category != null && ch.groupTitle != category) return@filter false
                if (query.isNotEmpty()) {
                    val inName = ch.displayName.lowercase().contains(query)
                    val inNum = ch.displayNumber.lowercase().contains(query)
                    val inGroup = ch.groupTitle?.lowercase()?.contains(query) == true
                    if (!inName && !inNum && !inGroup) return@filter false
                }
                when (filter) {
                    "fav" -> ch.favorite
                    "hidden" -> ch.hidden
                    "locked" -> ch.locked || (ch.sourceKey in lockedKeys)
                    else -> true
                }
            }

            val total = filtered.size
            val paged = filtered.drop(page * pageSize).take(pageSize)
            val arr = JSONArray()
            paged.forEach { ch ->
                arr.put(
                    JSONObject()
                        .put("key", ch.sourceKey)
                        .put("name", ch.displayName)
                        .put("number", ch.displayNumber)
                        .put("group", ch.groupTitle.orEmpty())
                        .put("source", ch.source.name)
                        .put("isIptv", ch.source == com.tvapp.livetv.model.LiveChannel.Source.IPTV)
                        .put("favorite", ch.favorite)
                        .put("hidden", ch.hidden)
                        .put("locked", ch.locked || (ch.sourceKey in lockedKeys))
                        .put("logo", ch.logoUrl.orEmpty())
                )
            }
            return WebReply(200, JSONObject()
                .put("total", total)
                .put("page", page)
                .put("pageSize", pageSize)
                .put("channels", arr)
                .toString())
        }
        if (action == "editor_update_channel") {
            sessionTimeout.touch()
            val key = data.optString("key")
            if (key.isBlank()) return WebReply(400)

            withContext(Dispatchers.IO) {
                if (data.has("customName")) {
                    val newName = data.optString("customName").trim().takeIf { it.isNotBlank() }
                    channelRepository.setCustomName(key, newName)
                }
                if (data.has("customNumber")) {
                    val num = data.optInt("customNumber", -1).takeIf { it > 0 }
                    channelRepository.setCustomNumber(key, num)
                }
                if (data.has("favorite")) {
                    channelRepository.setFavorite(key, data.getBoolean("favorite"))
                }
                if (data.has("hidden")) {
                    channelRepository.setHidden(key, data.getBoolean("hidden"))
                }
                if (data.has("locked")) {
                    parentalControlStore.setLocked(key, data.getBoolean("locked"))
                }
                if (data.optBoolean("removeFromMain", false)) {
                    repository.setChannelSelected(key, false)
                    sources = repository.sources()
                }
            }
            onState(false, message, true)
            return WebReply(200, JSONObject().put("success", true).toString())
        }
        return withContext(Dispatchers.Main.immediate) {
            if (operationGate.isBusy || server == null) return@withContext WebReply(409)
            val kind = data.optString("kind")
            if (action !in listOf("add", "update", "delete", "rename", "add_bulk") ||
                (action == "rename" && (data.optString("name").isBlank() || data.optString("name").length > 200)) ||
                (action == "add" && (kind !in listOf("URL", "DOCUMENT", "XTREAM", "STALKER") ||
                    data.optString("name").isBlank() || data.optString("name").length > 200)) ||
                (action == "add_bulk" && (data.optJSONArray("items") == null || data.optJSONArray("items")!!.length() == 0))) {
                return@withContext WebReply(400)
            }
            if (!operationGate.tryAcquire()) return@withContext WebReply(409)
            sessionTimeout.touch()
            val bulkItems = data.optJSONArray("items")
            val sourceName = when (action) {
                "add" -> data.optString("name")
                "add_bulk" -> context.getString(R.string.github_bulk_title)
                else -> sources.firstOrNull { it.source.id == data.optLong("id") }?.source?.name.orEmpty()
            }
            message = if (action == "add_bulk") {
                context.getString(R.string.iptv_source_bulk_progress, 0, bulkItems?.length() ?: 0, context.getString(R.string.iptv_import_connecting))
            } else {
                context.getString(R.string.iptv_source_progress, sourceName, context.getString(R.string.iptv_import_connecting))
            }
            onState(true, message, false)
            scope.launch {
                val progressUi = launch {
                    while (true) { delay(500); onState(true, message, false) }
                }
                var changed = false
                try {
                    val resultMessage = withContext(Dispatchers.IO) {
                        if (action == "add_bulk" && bulkItems != null) {
                            executeBulkAdd(bulkItems).also { sources = repository.sources() }
                        } else {
                            execute(action, kind, data, sourceName).also { sources = repository.sources() }
                        }
                    }
                    message = resultMessage
                    changed = true
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) {
                    CrashReportStore(context).recordDebug("IPTV_WEB FAILURE | ${error.javaClass.simpleName}")
                    message = context.getString(R.string.web_operation_failed)
                } finally {
                    progressUi.cancel()
                    sessionTimeout.touch()
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

    private suspend fun executeBulkAdd(itemsJson: JSONArray): String {
        val total = itemsJson.length()
        var successCount = 0
        for (i in 0 until total) {
            val item = itemsJson.optJSONObject(i) ?: continue
            val name = item.optString("name").trim()
            val url = item.optString("url").trim()
            if (name.isEmpty() || url.isEmpty()) continue

            // Zaten kayitli olan adresi atla veya guncelle
            val alreadySaved = repository.sources().any { it.source.location == url }
            if (alreadySaved) {
                successCount++
                continue
            }

            val progress: (IptvImportProgress) -> Unit = { p ->
                val stage = when (p.stage) {
                    IptvImportStage.CONNECTING -> context.getString(R.string.iptv_import_connecting)
                    IptvImportStage.READING -> context.getString(R.string.iptv_import_reading, p.processedChannels)
                    IptvImportStage.SAVING -> context.getString(R.string.iptv_import_saving)
                    IptvImportStage.FINISHING -> context.getString(R.string.iptv_import_finishing)
                }
                message = context.getString(R.string.iptv_source_bulk_progress, i + 1, total, "$name ($stage)")
            }
            message = context.getString(R.string.iptv_source_bulk_progress, i + 1, total, name)
            runCatching {
                repository.importUrl(url, name, progress)
                successCount++
            }.onFailure { error ->
                CrashReportStore(context).recordDebug("BULK_IMPORT FAILURE | $name | ${error.javaClass.simpleName}")
            }
        }
        return context.getString(R.string.iptv_source_bulk_import_complete, successCount)
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
            "github" to R.string.import_github_manifest,
            "githubUser" to R.string.github_username_hint,
            "githubFetch" to R.string.github_fetch_lists,
            "githubLive" to R.string.github_live_lists,
            "githubVod" to R.string.github_vod_lists,
            "githubAddSelected" to R.string.github_add_selected,
            "githubSelectAll" to R.string.github_select_all,
            "githubDeselectAll" to R.string.github_deselect_all,
            "githubEmpty" to R.string.github_manifest_empty,
            "githubError" to R.string.github_manifest_error,
            "githubNote" to R.string.github_guide_note,
            "enable" to R.string.enable_iptv_source,
            "disable" to R.string.disable_iptv_source,
            "enabled" to R.string.iptv_source_enabled,
            "disabled" to R.string.iptv_source_disabled,
            "tabSources" to R.string.web_tab_sources,
            "tabEditor" to R.string.web_tab_channel_editor,
            "selectChannels" to R.string.web_select_channels,
            "selectChannelsTitle" to R.string.web_select_channels_title,
            "editorTitle" to R.string.web_channel_editor_title,
        )
    }
}
