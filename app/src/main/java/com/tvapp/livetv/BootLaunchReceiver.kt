package com.tvapp.livetv

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.tvapp.livetv.diagnostics.CrashReportStore
import com.tvapp.livetv.settings.DisplayPreferencesStore

class BootLaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val debugLog = CrashReportStore(context)
        val enabled = DisplayPreferencesStore(context).load().launchOnBoot
        debugLog.recordDebug("BOOT_RECEIVED | launchOnBoot=$enabled")
        if (!enabled) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_STARTED_AFTER_BOOT, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            BOOT_LAUNCH_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = SystemClock.elapsedRealtime() + BOOT_LAUNCH_DELAY_MS

        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
                debugLog.recordDebug("BOOT_LAUNCH_SCHEDULED | exact=true, delayMs=$BOOT_LAUNCH_DELAY_MS")
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent,
                )
                debugLog.recordDebug("BOOT_LAUNCH_SCHEDULED | exact=false, delayMs=$BOOT_LAUNCH_DELAY_MS")
            }
        }.onFailure { error ->
            debugLog.recordDebug(
                "BOOT_LAUNCH_FAILURE | ${error.javaClass.name}: ${error.message}",
            )
        }
    }

    companion object {
        const val EXTRA_STARTED_AFTER_BOOT = "com.tvapp.livetv.extra.STARTED_AFTER_BOOT"
        private const val BOOT_LAUNCH_DELAY_MS = 10_000L
        private const val BOOT_LAUNCH_REQUEST_CODE = 4107
    }
}
