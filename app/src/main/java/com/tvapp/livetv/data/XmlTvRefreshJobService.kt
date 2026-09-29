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
                val outcome = runCatching {
                    val repository = XmlTvRepository(this@XmlTvRefreshJobService)
                    when (params.jobId) {
                        XmlTvRepository.XTREAM_REFRESH_JOB_ID ->
                            repository.refreshXtreamShortEpg(
                                force = params.extras.getBoolean(XmlTvRepository.EXTRA_FORCE_REFRESH),
                            )
                        XmlTvRepository.NIGHTLY_REFRESH_JOB_ID -> nightlyRefresh(repository)
                        else -> {
                            repository.refreshSavedUrls()
                            repository.refreshXtreamShortEpg(force = true)
                        }
                    }
                }
                outcome
                    .onSuccess { imported ->
                        if (params.jobId == XmlTvRepository.NIGHTLY_REFRESH_JOB_ID) {
                            XmlTvRepository.recordNightlyRefreshDebug(
                                "NIGHTLY_REFRESH_SUCCESS | programs=$imported",
                            )
                        }
                    }
                    .onFailure { error ->
                        // Sessiz gece işinde arayüz yok; kalıcı operasyonel hata
                        // mevcut debug log tesisine yazılır (hassas veri içermez).
                        XmlTvRepository.recordNightlyRefreshDebug(
                            "NIGHTLY_REFRESH_FAILURE | ${error.javaClass.name}: ${error.message}",
                        )
                    }
                outcome.isFailure
            }
            refreshJobs.remove(params.jobId)
            jobFinished(params, needsRetry)
        }
        return true
    }

    /** Gece penceresinde EPG'yi taze tutar: tüm etkin URL kaynakları, ardından
     *  Xtream kısa EPG ve süresi geçmiş program temizliği. Her kaynak kendi
     *  kayıt hatası güncellemesini alır; bir kaynak başarısız olsa bile diğerleri
     *  yenilenir. */
    private suspend fun nightlyRefresh(repository: XmlTvRepository): Int {
        val sourceCount = repository.refreshSavedUrls()
        repository.refreshXtreamShortEpg(force = true)
        repository.purgeExpiredPrograms()
        return sourceCount
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
