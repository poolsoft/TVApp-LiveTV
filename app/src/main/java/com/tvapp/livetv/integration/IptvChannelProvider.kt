package com.tvapp.livetv.integration

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.tvapp.livetv.data.local.TVAppDatabase

class IptvChannelProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        require(MATCHER.match(uri) == CHANNELS) { "Unsupported URI: $uri" }
        val appContext = checkNotNull(context).applicationContext
        val channels = TVAppDatabase.getInstance(appContext).iptvDao().getSharedChannels()
        return MatrixCursor(COLUMNS, channels.size).apply {
            channels.forEach { channel ->
                addRow(
                    arrayOf(
                        TvAppInputContract.CONTRACT_VERSION,
                        channel.sourceKey,
                        channel.displayName,
                        channel.streamUrl,
                        channel.logoUrl,
                        channel.groupTitle,
                        channel.epgId,
                        channel.userAgent,
                        channel.referrer,
                    ),
                )
            }
            setNotificationUri(appContext.contentResolver, TvAppInputContract.CHANNELS_URI)
        }
    }

    override fun getType(uri: Uri): String? = when (MATCHER.match(uri)) {
        CHANNELS -> "vnd.android.cursor.dir/vnd.tvapp.iptv-channel"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri = readOnly()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        readOnly()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = readOnly()

    private fun <T> readOnly(): T = throw UnsupportedOperationException("Read-only provider")

    private companion object {
        const val CHANNELS = 1
        val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(TvAppInputContract.AUTHORITY, "channels", CHANNELS)
        }
        val COLUMNS = arrayOf(
            TvAppInputContract.COLUMN_CONTRACT_VERSION,
            TvAppInputContract.COLUMN_SOURCE_KEY,
            TvAppInputContract.COLUMN_DISPLAY_NAME,
            TvAppInputContract.COLUMN_STREAM_URL,
            TvAppInputContract.COLUMN_LOGO_URL,
            TvAppInputContract.COLUMN_GROUP_TITLE,
            TvAppInputContract.COLUMN_EPG_ID,
            TvAppInputContract.COLUMN_USER_AGENT,
            TvAppInputContract.COLUMN_REFERRER,
        )
    }
}
