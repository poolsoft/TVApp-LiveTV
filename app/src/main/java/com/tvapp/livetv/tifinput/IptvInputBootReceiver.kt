package com.tvapp.livetv.tifinput

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class IptvInputBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            IptvInputSyncScheduler.schedulePeriodic(context)
            IptvInputSyncScheduler.scheduleImmediate(context)
        }
    }
}
