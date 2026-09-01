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
        IptvInputSyncScheduler.scheduleImmediate(this)
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { reportStore.recordCrash(thread, error) }
            previousHandler?.uncaughtException(thread, error)
        }
    }
}
