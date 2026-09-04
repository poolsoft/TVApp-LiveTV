package com.tvapp.livetv.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tvapp.livetv.MainActivity
import com.tvapp.livetv.R
import com.tvapp.livetv.diagnostics.CrashReportStore

/**
 * Fires reminder notifications and restores pending reminders after a reboot.
 * Tapping the notification opens MainActivity through the existing
 * `tvapp://channel/open?sourceKey=…` deep link.
 */
class ProgramReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE_REMINDER -> fire(context, intent)
            Intent.ACTION_BOOT_COMPLETED -> restoreAfterBoot(context)
        }
    }

    private fun fire(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val sourceKey = intent.getStringExtra(EXTRA_SOURCE_KEY) ?: return
        val reminderStart = intent.getLongExtra(EXTRA_START_TIME_MILLIS, 0L)
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty()
        val programTitle = intent.getStringExtra(EXTRA_PROGRAM_TITLE).orEmpty()
        val debugLog = CrashReportStore(context)

        ProgramReminderStore(context).remove(reminderId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Permission was revoked after scheduling; never crash, only record.
            debugLog.recordDebug("REMINDER_FIRE_SKIPPED | notifications permission missing")
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.Builder()
                .scheme("tvapp")
                .authority("channel")
                .appendPath("open")
                .appendQueryParameter("sourceKey", sourceKey)
                .build()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tv)
            .setContentTitle(
                context.getString(R.string.reminder_notification_title, channelName),
            )
            .setContentText(
                context.getString(R.string.reminder_notification_text, programTitle),
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching {
            manager.notify(reminderId.hashCode(), notification)
        }.onFailure { error ->
            debugLog.recordDebug(
                "REMINDER_NOTIFY_FAILURE | ${error.javaClass.simpleName}: ${error.message}",
            )
        }
        debugLog.recordDebug(
            "REMINDER_FIRED | channel=$channelName, startMillis=$reminderStart",
        )
    }

    private fun restoreAfterBoot(context: Context) {
        val store = ProgramReminderStore(context)
        val scheduler = ProgramReminderScheduler(context)
        val future = store.reminders()
        scheduler.rescheduleAll(future)
        CrashReportStore(context).recordDebug(
            "REMINDER_BOOT_RESTORE | count=${future.size}",
        )
    }

    companion object {
        const val ACTION_FIRE_REMINDER = "com.tvapp.livetv.action.FIRE_PROGRAM_REMINDER"
        const val EXTRA_REMINDER_ID = "reminder-id"
        const val EXTRA_SOURCE_KEY = "reminder-source-key"
        const val EXTRA_CHANNEL_NAME = "reminder-channel-name"
        const val EXTRA_PROGRAM_TITLE = "reminder-program-title"
        const val EXTRA_START_TIME_MILLIS = "reminder-start-time-millis"
        private const val CHANNEL_ID = "program-reminders"
    }
}
