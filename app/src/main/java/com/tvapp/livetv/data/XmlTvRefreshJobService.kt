package com.tvapp.livetv.data

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.runBlocking

class XmlTvRefreshJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            val needsRetry = runCatching {
                val repository = XmlTvRepository(this)
                repository.refreshSavedUrl()
                runBlocking { repository.refreshXtreamShortEpg(force = true) }
            }.isFailure
            jobFinished(params, needsRetry)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
