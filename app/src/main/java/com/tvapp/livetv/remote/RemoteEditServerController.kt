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
        return try {
            server.startServer()
            serverRef.set(server)
            true
        } catch (error: IOException) {
            false
        }
    }

    @Synchronized
    fun stop() {
        serverRef.getAndSet(null)?.stop()
    }

    private class ServerDataProvider(context: Context) : RemoteEditServer.DataProvider {
        private val channelRepository = ChannelRepository(context)
        private val iptvRepository = IptvRepository(context)
        private val xmlTvRepository = XmlTvRepository(context)
        private val channelDao = com.tvapp.livetv.data.local.TVAppDatabase.getInstance(context).channelDao()

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
        override fun channels(afterSourceKey: String?, limit: Int, query: String?): JSONArray {
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
                        put(
                            JSONObject()
                                .put("sourceKey", channel.sourceKey)
                                .put("displayName", channel.displayName)
                                .put("displayNumber", channel.displayNumber)
                                .put("source", channel.source.name)
                                .put("favorite", channel.favorite)
                                .put("hidden", channel.hidden)
                                .put("groupId", channel.groupId ?: JSONObject.NULL)
                                .put(
                                    "iptvContentType",
                                    channel.iptvContentType ?: JSONObject.NULL,
                                ),
                        )
                    }
            }
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

        override fun sources(): JSONArray = JSONArray().apply {
            val iptvSources = runBlocking(Dispatchers.IO) { iptvRepository.sources() }
            iptvSources.forEach { summary ->
                put(
                    JSONObject()
                        .put("kind", "iptv")
                        .put("id", summary.source.id)
                        .put("name", summary.source.name),
                )
            }
            xmlTvRepository.sources().forEach { source ->
                put(
                    JSONObject()
                        .put("kind", "xmltv")
                        .put("id", source.id)
                        .put("name", source.name),
                )
            }
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
