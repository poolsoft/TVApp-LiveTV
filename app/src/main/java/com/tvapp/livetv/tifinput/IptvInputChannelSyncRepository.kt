package com.tvapp.livetv.tifinput

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.tv.TvContract
import com.tvapp.livetv.data.local.TVAppDatabase

data class IptvInputSyncResult(val synced: Int, val removed: Int)

class IptvInputChannelSyncRepository(context: Context) {
    private val appContext = context.applicationContext

    fun sync(inputId: String): IptvInputSyncResult {
        val dao = TVAppDatabase.getInstance(appContext).iptvDao()
        val resolver = appContext.contentResolver
        val existing = mutableMapOf<String, Long>()
        resolver.query(
            TvContract.buildChannelsUriForInput(inputId),
            arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(TvContract.Channels._ID)
            val dataIndex = cursor.getColumnIndexOrThrow(
                TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA,
            )
            while (cursor.moveToNext()) {
                IptvInputChannelMetadata.decode(cursor.getBlob(dataIndex))?.sourceKey?.let {
                    existing[it] = cursor.getLong(idIndex)
                }
            }
        }

        var synced = 0
        var offset = 0
        while (true) {
            val page = dao.getSharedChannelsPage(SYNC_PAGE_SIZE, offset)
            if (page.isEmpty()) break
            page.forEach { channel ->
                val values = ContentValues().apply {
                    put(TvContract.Channels.COLUMN_INPUT_ID, inputId)
                    put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, (synced + 1).toString())
                    put(TvContract.Channels.COLUMN_DISPLAY_NAME, channel.displayName)
                    put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_OTHER)
                    put(
                        TvContract.Channels.COLUMN_SERVICE_TYPE,
                        TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO,
                    )
                    put(TvContract.Channels.COLUMN_SEARCHABLE, 1)
                    put(
                        TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID,
                        IptvInputChannelMetadata.PROVIDER_ID_PREFIX + channel.sourceKey,
                    )
                    put(
                        TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA,
                        IptvInputChannelMetadata.from(channel).encode(),
                    )
                }
                val channelId = existing.remove(channel.sourceKey)
                if (channelId == null) {
                    resolver.insert(TvContract.Channels.CONTENT_URI, values)
                } else {
                    resolver.update(
                        ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, channelId),
                        values,
                        null,
                        null,
                    )
                }
                synced++
            }
            offset += page.size
        }
        existing.values.forEach { channelId ->
            resolver.delete(
                ContentUris.withAppendedId(TvContract.Channels.CONTENT_URI, channelId),
                null,
                null,
            )
        }
        return IptvInputSyncResult(synced, existing.size)
    }

    private companion object {
        const val SYNC_PAGE_SIZE = 500
    }
}
