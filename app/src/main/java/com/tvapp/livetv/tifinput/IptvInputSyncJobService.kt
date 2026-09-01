package com.tvapp.livetv.tifinput

import android.app.job.JobParameters
import android.app.job.JobService
import java.util.concurrent.Executors

class IptvInputSyncJobService : JobService() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onStartJob(params: JobParameters): Boolean {
        executor.execute {
            runCatching {
                IptvInputResolver.findInputId(this)?.let {
                    IptvInputChannelSyncRepository(this).sync(it)
                }
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
