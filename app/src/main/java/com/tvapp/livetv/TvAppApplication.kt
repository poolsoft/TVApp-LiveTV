package com.tvapp.livetv

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tvapp.livetv.diagnostics.CrashReportStore
import com.tvapp.livetv.settings.AppLanguage
import com.tvapp.livetv.settings.AppLanguageStore
import com.tvapp.livetv.tifinput.IptvInputSyncScheduler

class TvAppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val language = AppLanguageStore(this).load()
        if (language != AppLanguage.SYSTEM) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language.languageTag),
            )
        }
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        val reportStore = CrashReportStore(this)
        reportStore.recordDebug("APPLICATION_START | process=${android.os.Process.myPid()}")
        IptvInputSyncScheduler.schedulePeriodic(this)
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (thread.name == "FinalizerWatchdogDaemon" && error is java.util.concurrent.TimeoutException) {
                runCatching {
                    reportStore.recordDebug("FINALIZER_WATCHDOG_TIMEOUT_SUPPRESSED | ${error.message}")
                }
                return@setDefaultUncaughtExceptionHandler
            }
            runCatching { reportStore.recordCrash(thread, error) }
            previousHandler?.uncaughtException(thread, error)
        }
    }
}
