package com.tvapp.livetv

import android.app.Application
import com.tvapp.livetv.diagnostics.CrashReportStore

class TvAppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        val reportStore = CrashReportStore(this)
        reportStore.recordDebug("APPLICATION_START | process=${android.os.Process.myPid()}")
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { reportStore.recordCrash(thread, error) }
            previousHandler?.uncaughtException(thread, error)
        }
    }
}
