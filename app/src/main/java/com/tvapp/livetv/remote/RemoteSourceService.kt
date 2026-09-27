package com.tvapp.livetv.remote

import android.content.Context
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.data.XmlTvRepository
import com.tvapp.livetv.data.local.TVAppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * REMOTEEDIT source management (web panel "source management" sprint):
 * executes source operations requested by the web panel or the phone client.
 * All network/database work runs on Dispatchers.IO; the server worker thread
 * blocks on the result so the HTTP response carries the real outcome.
 * Playlist/XMLTV URLs and any credentials inside them are NEVER logged or
 * echoed back to clients.
 */
class RemoteSourceService(context: Context) {

    private val appContext = context.applicationContext
    private val iptvRepository = IptvRepository(appContext)
    private val xmlTvRepository = XmlTvRepository(appContext)
    private val iptvDao = TVAppDatabase.getInstance(appContext).iptvDao()

    /** All IPTV + XMLTV sources as the wire shape clients render. */
    fun sources(): JSONArray = JSONArray().apply {
        runBlocking(Dispatchers.IO) {
            iptvRepository.sources().forEach { summary ->
                put(
                    JSONObject()
                        .put("kind", "iptv")
                        .put("id", summary.source.id)
                        .put("name", summary.source.name)
                        .put("urlKind", summary.source.kind == IptvRepository.KIND_URL || summary.source.kind == IptvRepository.KIND_XTREAM)
                        .put("channelCount", summary.channelCount)
                        .put("selectedCount", summary.selectedChannelCount)
                        .put("error", JSONObject.NULL),
                )
            }
            xmlTvRepository.sourceSummaries().forEach { summary ->
                put(
                    JSONObject()
                        .put("kind", "xmltv")
                        .put("id", summary.source.id)
                        .put("name", summary.source.name)
                        .put("urlKind", summary.source.kind == XmlTvRepository.KIND_URL)
                        .put("channelCount", summary.channelCount)
                        .put("selectedCount", JSONObject.NULL)
                        .put("error", summary.source.lastError ?: JSONObject.NULL),
                )
            }
        }
    }

    /**
     * Updates the playlist/EPG URL of an existing URL-kind source and re-imports
     * it through the existing pipeline. Returns null when the source does not
     * exist or is a file-kind source (not editable remotely).
     */
    fun updateSource(kind: String, sourceId: Long, url: String): Boolean = runBlocking(Dispatchers.IO) {
        runCatching {
            when (kind) {
                KIND_IPTV -> {
                    val source = iptvDao.getSource(sourceId) ?: return@runBlocking false
                    iptvRepository.updateUrl(source, url)
                    true
                }

                KIND_XMLTV -> {
                    val source = xmlTvRepository.sources().firstOrNull { it.id == sourceId }
                        ?: return@runBlocking false
                    xmlTvRepository.updateUrl(source, url)
                    true
                }

                else -> false
            }
        }.getOrDefault(false)
    }

    /** Deletes the source together with its channels/programs. */
    fun deleteSource(kind: String, sourceId: Long): Boolean = runBlocking(Dispatchers.IO) {
        runCatching {
            when (kind) {
                KIND_IPTV -> {
                    val source = iptvDao.getSource(sourceId) ?: return@runBlocking false
                    iptvRepository.delete(source)
                    true
                }

                KIND_XMLTV -> {
                    val exists = xmlTvRepository.sources().any { it.id == sourceId }
                    if (!exists) return@runBlocking false
                    xmlTvRepository.deleteSource(sourceId)
                    true
                }

                else -> false
            }
        }.getOrDefault(false)
    }

    /** Re-downloads an existing URL-kind source in place. */
    fun refreshSource(kind: String, sourceId: Long): Boolean = runBlocking(Dispatchers.IO) {
        runCatching {
            when (kind) {
                KIND_IPTV -> {
                    val source = iptvDao.getSource(sourceId) ?: return@runBlocking false
                    if (source.kind != IptvRepository.KIND_URL) return@runBlocking false
                    iptvRepository.updateUrl(source, source.location)
                    true
                }

                KIND_XMLTV -> {
                    val source = xmlTvRepository.sources().firstOrNull { it.id == sourceId }
                        ?: return@runBlocking false
                    if (source.kind != XmlTvRepository.KIND_URL) return@runBlocking false
                    xmlTvRepository.refreshSource(source)
                    true
                }

                else -> false
            }
        }.getOrDefault(false)
    }

    /** XMLTV EPG channel catalog for mapping channels to EPG data. */
    fun xmltvCatalog(): JSONArray = JSONArray().apply {
        runBlocking(Dispatchers.IO) {
            xmlTvRepository.channelCatalog().forEach { option ->
                put(
                    JSONObject()
                        .put("sourceId", option.sourceId)
                        .put("channelId", option.channelId)
                        .put("channelName", option.channelName)
                        .put("programCount", option.programCount),
                )
            }
        }
    }

    /**
     * Applies a full or delta selection change for one IPTV source. The
     * "full" shape replaces the whole selection (like the on-TV picker);
     * "delta" adds or removes individual channels without touching the rest —
     * the shape the web panel uses for checkbox toggling.
     */
    fun applySelection(sourceId: Long, body: JSONObject): SelectionOutcome = runBlocking(Dispatchers.IO) {
        val add = body.optJSONArray("add")
        val remove = body.optJSONArray("remove")
        val replaceAll = body.has("selected")
        if (!replaceAll && add == null && remove == null) {
            return@runBlocking SelectionOutcome.SourceMissing
        }
        val source = iptvDao.getSource(sourceId)
        if (source == null) {
            return@runBlocking SelectionOutcome.SourceMissing
        }
        runCatching {
            when {
                replaceAll -> {
                    val keys = buildSet {
                        val array = body.getJSONArray("selected")
                        for (index in 0 until array.length()) add(array.getString(index))
                    }
                    iptvRepository.setSelectedChannels(sourceId, keys)
                }

                else -> {
                    val addKeys = buildSet {
                        if (add != null) {
                            for (index in 0 until add.length()) add(add.getString(index))
                        }
                    }
                    val removeKeys = buildSet {
                        if (remove != null) {
                            for (index in 0 until remove.length()) add(remove.getString(index))
                        }
                    }
                    if (addKeys.isEmpty() && removeKeys.isEmpty()) {
                        return@runBlocking SelectionOutcome.SourceMissing
                    }
                    iptvRepository.updateSelectedChannels(sourceId, addKeys, removeKeys)
                }
            }
            SelectionOutcome.Applied
        }.getOrDefault(SelectionOutcome.SourceMissing)
    }

    /** Result of a selection mutation. */
    enum class SelectionOutcome { Applied, SourceMissing }

    companion object {
        const val KIND_IPTV = "iptv"
        const val KIND_XMLTV = "xmltv"
    }
}
