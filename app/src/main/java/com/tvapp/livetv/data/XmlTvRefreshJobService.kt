package com.tvapp.livetv.data

import android.app.job.JobParameters
import android.app.job.JobService

class XmlTvRefreshJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            val needsRetry = runCatching {
                XmlTvRepository(this).refreshSavedUrl()
            }.isFailure
            jobFinished(params, needsRetry)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
