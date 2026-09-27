package com.tvapp.livetv.remote

import android.content.Context
import com.tvapp.livetv.BuildConfig
import com.tvapp.livetv.data.ChannelRepository
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.data.XmlTvRepository
import com.tvapp.livetv.model.LiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

private typealias PatchHandler = (
    sourceKey: String,
    expectedRevision: Long,
    patch: RemoteEditServer.ChannelPatch,
) -> RemoteEditServer.WriteOutcome?

/**
 * Owns the singleton [RemoteEditServer] lifecycle on the TV side. The server
 * runs only while the user has enabled it in Settings; start/stop are safe to
 * call repeatedly. Data endpoints are answered on the server worker threads
 * with blocking repository calls (they are already IO-bound and paged).
 */
class RemoteEditServerController private constructor(
    private val context: Context,
) {
    private val preferencesStore = RemoteEditPreferencesStore(context)
    private val pairingStore = PairingStore(context)
    private val serverRef = AtomicReference<RemoteEditServer?>(null)
    private var nsdAnnouncer: RemoteNsdAnnouncer? = null

    val isEnabled: Boolean get() = preferencesStore.enabled()

    fun setEnabled(enabled: Boolean) {
        preferencesStore.setEnabled(enabled)
        if (enabled) start() else stop()
    }

    fun newPairingCode(): String? {
        if (!isEnabled) return null
        return pairingStore.generateCode()
    }

    fun pairingCode(): String? = pairingStore.currentCode()

    fun pairedDevices(): List<PairedDeviceInfo> = pairingStore.devices().map {
        PairedDeviceInfo(it.id, it.deviceName, it.pairedAt, it.lastSeenAt)
    }

    fun removeDevice(deviceId: Long): Boolean = pairingStore.removeDevice(deviceId)

    /** Public snapshot of a paired device for the settings UI. */
    data class PairedDeviceInfo(
        val id: Long,
        val deviceName: String,
        val pairedAt: Long,
        val lastSeenAt: Long,
    )

    @Synchronized
    fun start(): Boolean {
        if (!com.tvapp.livetv.BuildConfig.REMOTE_EDIT_ENABLED) return false
        if (!preferencesStore.enabled()) return false
        if (serverRef.get() != null) return true
        val server = RemoteEditServer(
            preferencesStore,
            RemoteEditServer.PairingAuthorizer { token -> pairingStore.authorize(token) },
            ServerDataProvider(context),
        )
        server.pairingHandler = { code, deviceName ->
            pairingStore.attemptPair(code, deviceName)?.let { session ->
                Triple(session.deviceId, session.deviceName, session.tokenPlain)
            }
        }
        val writeRepository = ChannelRepository(context)
        server.writeHandler = { sourceKey, expectedRevision, patch ->
            val outcome = runBlocking(Dispatchers.IO) {
                writeRepository.remotePatchChannel(
                    sourceKey = sourceKey,
                    expectedRevision = expectedRevision,
                    favorite = patch.favorite,
                    hidden = patch.hidden,
                    customName = patch.customName,
                    clearCustomName = patch.clearCustomName,
                    customNumber = patch.customNumber,
                    clearCustomNumber = patch.clearCustomNumber,
                    groupId = patch.groupId,
                    clearGroupId = patch.clearGroupId,
                    sortOrder = patch.sortOrder,
                )
            }
            when (outcome) {
                is ChannelRepository.RemoteEditResult.Success ->
                    RemoteEditServer.WriteOutcome.Applied(outcome.newRevision)

                is ChannelRepository.RemoteEditResult.Conflict ->
                    RemoteEditServer.WriteOutcome.Rejected(outcome.currentRevision)

                ChannelRepository.RemoteEditResult.NotFound ->
                    RemoteEditServer.WriteOutcome.NotFound
            }
        }
        // REMOTEEDIT-008: serve the embedded web panel from APK assets.
        server.webPanelLoader = { path ->
            runCatching {
                context.assets.open("webpanel/$path").use { stream -> stream.readBytes() }
            }.getOrNull()
        }
        // REMOTEEDIT-006: remote source imports (IPTV playlist / XMLTV EPG)
        // through the existing pipelines.
        val importQueue = RemoteImportQueue(context)
        server.importQueueHandler = { name, url, kind ->
            val id = importQueue.enqueue(name, url, kind)
            server.bumpDataVersion()
            id
        }
        server.importQueueListHandler = { importQueue.all() }
        // Source management: delete / refresh / selection via RemoteSourceService.
        val sourceService = RemoteSourceService(context)
        server.sourceMutationHandler = { kind, id, operation ->
            val updated = when (operation) {
                RemoteEditServer.OP_DELETE -> sourceService.deleteSource(kind, id)
                RemoteEditServer.OP_REFRESH -> sourceService.refreshSource(kind, id)
                else -> false
            }
            if (updated) server.bumpDataVersion()
            updated
        }
        return try {
            server.startServer()
            serverRef.set(server)
            // Advertise over NSD so phones can discover the address.
            RemoteNsdAnnouncer(context, preferencesStore.port()).also { announcer ->
                nsdAnnouncer = announcer
                announcer.register()
            }
            true
        } catch (error: IOException) {
            false
        }
    }

    @Synchronized
    fun stop() {
        nsdAnnouncer?.unregister()
        nsdAnnouncer = null
        serverRef.getAndSet(null)?.stop()
    }

    private class ServerDataProvider(context: Context) : RemoteEditServer.DataProvider {
        private val channelRepository = ChannelRepository(context)
        private val iptvRepository = IptvRepository(context)
        private val xmlTvRepository = XmlTvRepository(context)
        private val channelDao = com.tvapp.livetv.data.local.TVAppDatabase.getInstance(context).channelDao()
        private val iptvDao = com.tvapp.livetv.data.local.TVAppDatabase.getInstance(context).iptvDao()
        private val sourceService = RemoteSourceService(context)

        override fun serverPing(): JSONObject = JSONObject()
            .put("app", "TVApp")
            .put("versionName", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
            .put("api", API_VERSION)

        /** Keyset paging on (sortOrder, sourceKey): client sends the last seen
         *  row's sourceKey as `after` together with its sortOrder-derived
         *  position; we resume after that row. LiveChannel has no sortOrder
         *  field itself, so ordering follows the merged list order that the
         *  main list already uses, and the cursor is the last sourceKey. */
        override fun channels(
            afterSourceKey: String?,
            limit: Int,
            query: String?,
            sourceId: Long?,
        ): JSONArray {
            // Picker mode: page one IPTV source's full catalog from the DAO
            // (bounded query, no full in-memory load).
            if (sourceId != null) {
                return JSONArray().apply {
                    var anchorIndex: Int = -1
                    var anchorKey: String? = null
                    if (afterSourceKey != null) {
                        val anchor = runBlocking(Dispatchers.IO) {
                            iptvDao.getChannel(afterSourceKey)
                        }
                        if (anchor != null && anchor.sourceId == sourceId) {
                            anchorIndex = anchor.originalIndex
                            anchorKey = anchor.sourceKey
                        }
                    }
                    val page: List<com.tvapp.livetv.data.local.IptvChannelListProjection> = runBlocking(Dispatchers.IO) {
                        val ftsQuery = ftsQueryFrom(query)
                        if (anchorKey != null) {
                            iptvDao.getCatalogPageAfter(sourceId, anchorIndex, anchorKey, ftsQuery, limit)
                        } else {
                            iptvDao.getCatalogPageFirst(sourceId, ftsQuery, limit)
                        }
                    }
                    page.forEach { entity ->
                        put(
                            JSONObject()
                                .put("sourceKey", entity.sourceKey)
                                .put("displayName", entity.displayName)
                                .put("displayNumber", (entity.originalIndex + 1).toString())
                                .put("source", "IPTV")
                                .put("favorite", false)
                                .put("hidden", false)
                                .put("inMainList", entity.selected)
                                .put("revision", JSONObject.NULL)
                                .put("groupId", JSONObject.NULL)
                                .put("iptvContentType", entity.contentType ?: JSONObject.NULL),
                        )
                    }
                }
            }
            val rows: List<LiveChannel> = runBlocking(Dispatchers.IO) {
                channelRepository.channels(includeTif = true).getOrDefault(emptyList())
            }
            val startIndex = if (afterSourceKey == null) {
                0
            } else {
                rows.indexOfFirst { it.sourceKey == afterSourceKey } + 1
            }
            return JSONArray().apply {
                rows.asSequence()
                    .drop(startIndex)
                    .filter { it.source == LiveChannel.Source.TIF || it.source == LiveChannel.Source.IPTV }
                    .filter { query == null || it.displayName.contains(query, ignoreCase = true) }
                    .take(limit)
                    .forEach { channel ->
                        val revision = runBlocking(Dispatchers.IO) {
                            channelDao.revisionOf(channel.sourceKey)
                        } ?: 0L
                        put(
                            JSONObject()
                                .put("sourceKey", channel.sourceKey)
                                .put("displayName", channel.displayName)
                                .put("displayNumber", channel.displayNumber)
                                .put("source", channel.source.name)
                                .put("favorite", channel.favorite)
                                .put("hidden", channel.hidden)
                                .put("revision", revision)
                                .put("groupId", channel.groupId ?: JSONObject.NULL)
                                .put(
                                    "iptvContentType",
                                    channel.iptvContentType ?: JSONObject.NULL,
                                ),
                        )
                    }
            }
        }

        /** REMOTEEDIT-008/R3: single channel with full editable fields. */
        override fun channel(sourceKey: String): JSONObject? {
            val rows: List<LiveChannel> = runBlocking(Dispatchers.IO) {
                channelRepository.channels(includeTif = true).getOrDefault(emptyList())
            }
            val channel = rows.firstOrNull { it.sourceKey == sourceKey } ?: return null
            val revision = runBlocking(Dispatchers.IO) {
                channelDao.revisionOf(channel.sourceKey)
            } ?: 0L
            return JSONObject()
                .put("sourceKey", channel.sourceKey)
                .put("displayName", channel.displayName)
                .put("displayNumber", channel.displayNumber)
                .put("source", channel.source.name)
                .put("favorite", channel.favorite)
                .put("hidden", channel.hidden)
                .put("revision", revision)
                .put("groupId", channel.groupId ?: JSONObject.NULL)
                .put("iptvContentType", channel.iptvContentType ?: JSONObject.NULL)
        }

        override fun groups(): JSONArray {
            val groups: List<com.tvapp.livetv.data.local.ChannelGroupEntity> = runBlocking(
                Dispatchers.IO,
            ) { channelDao.getGroups() }
            return JSONArray().apply {
                groups.forEach { group ->
                    put(
                        JSONObject()
                            .put("id", group.id)
                            .put("name", group.name),
                    )
                }
            }
        }

        override fun sources(): JSONArray = sourceService.sources()

        override fun xmltvCatalog(): JSONArray = sourceService.xmltvCatalog()

        override fun applySelection(sourceId: Long, body: JSONObject): Boolean =
            sourceService.applySelection(sourceId, body) ==
                RemoteSourceService.SelectionOutcome.Applied

        /** Same tokenization contract as IptvFtsQuery (internal to the data
         *  package); duplicated here because the remote package cannot see it. */
        private fun ftsQueryFrom(rawQuery: String?): String {
            if (rawQuery.isNullOrBlank()) return ""
            val terms = Regex("[\\p{L}\\p{N}]+").findAll(rawQuery)
                .map(MatchResult::value)
                .distinct()
                .toList()
            if (terms.isEmpty()) return "tvappnomatchtoken*"
            return terms.joinToString(" ") { term -> "$term*" }
        }

        private companion object {
            const val API_VERSION = 1
        }
    }

    companion object {
        @Volatile
        private var instance: RemoteEditServerController? = null

        fun get(context: Context): RemoteEditServerController = instance ?: synchronized(this) {
            instance ?: RemoteEditServerController(context.applicationContext).also { instance = it }
        }
    }
}
