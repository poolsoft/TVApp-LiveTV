package com.tvapp.livetv.main

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgHostTest {

    private fun scope() = CoroutineScope(Dispatchers.Unconfined + Job())

    @Test
    fun stopEpgRefreshCancelsRunningJob() {
        var refreshCount = 0
        val env = object : EpgHostEnv {
            override fun getContext(): Context? = null
            override suspend fun onRefreshTick() { refreshCount++ }
            override fun onAutoRefreshComplete() {}
            override fun recordDebug(message: String) {}
        }
        val host = EpgHost(scope(), env)
        host.startEpgRefresh()
        host.stopEpgRefresh()
        assertEquals(0, refreshCount)
    }

    @Test
    fun cancelAllResetsJobs() {
        val messages = mutableListOf<String>()
        val env = object : EpgHostEnv {
            override fun getContext(): Context? = null
            override suspend fun onRefreshTick() {}
            override fun onAutoRefreshComplete() {}
            override fun recordDebug(message: String) { messages += message }
        }
        val host = EpgHost(scope(), env)
        host.startEpgRefresh()
        host.cancelAll()
        assertTrue(messages.isEmpty())
    }
}
