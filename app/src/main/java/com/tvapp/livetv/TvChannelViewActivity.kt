package com.tvapp.livetv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.tvapp.livetv.diagnostics.CrashReportStore

/** Dedicated system Live TV chooser entry that keeps channel VIEW intents off MainActivity. */
class TvChannelViewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val channelUri = intent?.data?.takeIf { uri ->
            uri.scheme == "content" && uri.authority == "android.media.tv"
        }
        CrashReportStore(this).recordDebug(
            "TV_CHANNEL_VIEW_INTENT | action=${intent?.action}, type=${intent?.type}, data=$channelUri",
        )

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                channelUri?.let { putExtra(EXTRA_TIF_CHANNEL_URI, it.toString()) }
            },
        )
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_TIF_CHANNEL_URI = "com.tvapp.livetv.extra.TIF_CHANNEL_URI"
    }
}
