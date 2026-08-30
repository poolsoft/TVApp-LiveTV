package com.tvapp.livetv.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.tvprovider.media.tv.TvContractCompat

class WatchNextRemovedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TvContractCompat.ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED) return
        val programId = intent.getLongExtra(TvContractCompat.EXTRA_WATCH_NEXT_PROGRAM_ID, -1L)
        if (programId > 0L) HomeRecentChannelsPublisher.suppressWatchNext(context, programId)
    }
}
