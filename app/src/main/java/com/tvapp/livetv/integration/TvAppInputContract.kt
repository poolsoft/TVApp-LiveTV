package com.tvapp.livetv.integration

import android.net.Uri

object TvAppInputContract {
    const val AUTHORITY = "com.tvapp.livetv.iptv"
    const val READ_PERMISSION = "com.tvapp.livetv.permission.READ_IPTV_CHANNELS"
    const val CONTRACT_VERSION = 1

    val CHANNELS_URI: Uri = Uri.parse("content://$AUTHORITY/channels")

    const val COLUMN_CONTRACT_VERSION = "contract_version"
    const val COLUMN_SOURCE_KEY = "source_key"
    const val COLUMN_DISPLAY_NAME = "display_name"
    const val COLUMN_STREAM_URL = "stream_url"
    const val COLUMN_LOGO_URL = "logo_url"
    const val COLUMN_GROUP_TITLE = "group_title"
    const val COLUMN_EPG_ID = "epg_id"
    const val COLUMN_USER_AGENT = "user_agent"
    const val COLUMN_REFERRER = "referrer"
}
