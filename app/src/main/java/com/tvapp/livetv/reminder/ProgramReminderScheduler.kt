package com.tvapp.livetv.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tvapp.livetv.diagnostics.CrashReportStore

/**
 * Schedules reminder alarms with [AlarmManager]. Falls back to inexact
 * delivery when the device no longer grants exact alarms, matching the
 * behaviour already used by BootLaunchReceiver.
 */
class ProgramReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val debugLog = CrashReportStore(appContext)

    fun schedule(reminder: ProgramReminder) {
        val pendingIntent = pendingIntent(reminder)
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.startTimeMillis,
                    pendingIntent,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.startTimeMillis,
                    pendingIntent,
                )
            }
        }.onFailure { error ->
            debugLog.recordDebug(
                "REMINDER_SCHEDULE_FAILURE | ${error.javaClass.simpleName}: ${error.message}",
            )
        }
    }

    fun cancel(reminderId: String) {
        runCatching {
            alarmManager.cancel(pendingIntentPlaceholder(reminderId))
        }
    }

    /** Re-schedules all future reminders, e.g. after a device reboot. */
    fun rescheduleAll(reminders: List<ProgramReminder>, now: Long = System.currentTimeMillis()) {
        reminders.pruneExpired(now).forEach(::schedule)
    }

    private fun pendingIntent(reminder: ProgramReminder): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            reminder.id.hashCode(),
            reminderIntent(reminder.id).apply {
                putExtra(ProgramReminderReceiver.EXTRA_SOURCE_KEY, reminder.sourceKey)
                putExtra(ProgramReminderReceiver.EXTRA_CHANNEL_NAME, reminder.channelName)
                putExtra(ProgramReminderReceiver.EXTRA_PROGRAM_TITLE, reminder.programTitle)
                putExtra(
                    ProgramReminderReceiver.EXTRA_START_TIME_MILLIS,
                    reminder.startTimeMillis,
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun pendingIntentPlaceholder(reminderId: String): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            reminderId.hashCode(),
            reminderIntent(reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun reminderIntent(reminderId: String): Intent =
        Intent(appContext, ProgramReminderReceiver::class.java)
            .setAction(ProgramReminderReceiver.ACTION_FIRE_REMINDER)
            .putExtra(ProgramReminderReceiver.EXTRA_REMINDER_ID, reminderId)
}
