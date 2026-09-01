package com.tvapp.livetv.tifinput

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context

object IptvInputSyncScheduler {
    private const val PERIODIC_JOB_ID = 42001
    private const val IMMEDIATE_JOB_ID = 42002
    private const val PERIOD_MILLIS = 12L * 60L * 60L * 1_000L

    fun schedulePeriodic(context: Context) {
        val info = JobInfo.Builder(PERIODIC_JOB_ID, component(context))
            .setPersisted(true)
            .setPeriodic(PERIOD_MILLIS)
            .build()
        context.getSystemService(JobScheduler::class.java).schedule(info)
    }

    fun scheduleImmediate(context: Context) {
        val info = JobInfo.Builder(IMMEDIATE_JOB_ID, component(context))
            .setMinimumLatency(500L)
            .setOverrideDeadline(5_000L)
            .build()
        context.getSystemService(JobScheduler::class.java).schedule(info)
    }

    private fun component(context: Context) =
        ComponentName(context, IptvInputSyncJobService::class.java)
}
