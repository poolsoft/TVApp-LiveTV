package com.tvapp.livetv.main

import android.content.Context
import com.tvapp.livetv.data.XmlTvRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Faz 5: MainActivity'deki EPG periyodik yenileme ve arkaplan auto-refresh orkestrasyonu.
 *
 * Davranis degisikligi yok: zamanlayici ve XMLTV repo sorgulari bu Host bunyesindedir.
 * Job sahipligi buradadir; Activity kapanirken veya duraklatilirken yalnizca [cancelAll]
 * veya [stopEpgRefresh] cagirir.
 */
class EpgHost(
    private val scope: CoroutineScope,
    private val env: EpgHostEnv,
) {

    companion object {
        const val EPG_REFRESH_INTERVAL_MS = 60_000L
        const val EPG_AUTO_REFRESH_INITIAL_DELAY_MS = 25_000L
    }

    private var epgRefreshJob: Job? = null
    private var epgAutoRefreshJob: Job? = null

    /** Periyodik EPG yenileme dongusunu baslatir. */
    fun startEpgRefresh() {
        epgRefreshJob?.cancel()
        epgRefreshJob = scope.launch {
            while (true) {
                delay(EPG_REFRESH_INTERVAL_MS)
                env.onRefreshTick()
            }
        }
    }

    /** Periyodik yenilemeyi durdurur. */
    fun stopEpgRefresh() {
        epgRefreshJob?.cancel()
        epgRefreshJob = null
    }

    /** Uygulama acildiginda gereken XMLTV otomatik yenilemesini zamanlar. */
    fun checkAndScheduleAutoRefresh() {
        if (epgAutoRefreshJob?.isActive == true) return
        epgAutoRefreshJob = scope.launch(Dispatchers.IO) {
            delay(EPG_AUTO_REFRESH_INITIAL_DELAY_MS)
            val context = env.getContext() ?: return@launch
            val xmlTvRepo = XmlTvRepository(context)
            if (xmlTvRepo.shouldAutoRefresh()) {
                env.recordDebug("EPG_AUTO_REFRESH_START | source=xmltv")
                val activeKeys = xmlTvRepo.activeChannelKeys()
                runCatching {
                    val count = xmlTvRepo.refreshSavedUrls(activeKeys)
                    env.recordDebug("EPG_AUTO_REFRESH_SUCCESS | count=$count")
                    withContext(Dispatchers.Main) {
                        env.onAutoRefreshComplete()
                    }
                }.onFailure { error ->
                    env.recordDebug(
                        "EPG_AUTO_REFRESH_FAILURE | ${error.javaClass.simpleName}: ${error.message}",
                    )
                }
            }
        }
    }

    /** Kapanis sirasinda tum gorevleri sonlandirir. */
    fun cancelAll() {
        epgRefreshJob?.cancel()
        epgRefreshJob = null
        epgAutoRefreshJob?.cancel()
        epgAutoRefreshJob = null
    }
}

interface EpgHostEnv {
    fun getContext(): Context?
    suspend fun onRefreshTick()
    fun onAutoRefreshComplete()
    fun recordDebug(message: String)
}
