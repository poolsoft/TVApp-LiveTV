package com.tvapp.livetv.data

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class XmlTvRefreshJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val refreshJobs = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        refreshJobs[params.jobId] = scope.launch {
            val needsRetry = refreshMutex.withLock {
                runCatching {
                    val repository = XmlTvRepository(this@XmlTvRefreshJobService)
                    if (params.jobId == XmlTvRepository.XTREAM_REFRESH_JOB_ID) {
                        repository.refreshXtreamShortEpg(
                            force = params.extras.getBoolean(XmlTvRepository.EXTRA_FORCE_REFRESH),
                        )
                    } else {
                        repository.refreshSavedUrls()
                        repository.refreshXtreamShortEpg(force = true)
                    }
                }.isFailure
            }
            refreshJobs.remove(params.jobId)
            jobFinished(params, needsRetry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        refreshJobs.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
