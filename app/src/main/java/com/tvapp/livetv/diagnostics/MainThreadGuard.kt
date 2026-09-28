package com.tvapp.livetv.diagnostics

import android.os.StrictMode
import com.tvapp.livetv.BuildConfig
import java.util.concurrent.Executor

/** Yalnızca debug derlemelerinde ana iş parçacığı ihlallerini yakalar ve mevcut
 *  debug log tesisine yazar; release derlemelerinde hiçbir etki yapmaz. Böylece
 *  cihazdaki yavaşlık şikâyeti Download/TVApp/TVApp-debug-*.log içinde somut
 *  MAIN_THREAD_VIOLATION kayıtlarıyla (disk G/Ç, ağ vb.) doğrulanabilir. */
object MainThreadGuard {
    @Volatile
    private var store: CrashReportStore? = null

    fun install(store: CrashReportStore) {
        if (!BuildConfig.DEBUG) return
        this.store = store
        val listener = StrictMode.OnThreadViolationListener { violation ->
            record(violation)
        }
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyListener(Executor { it.run() }, listener)
                .build(),
        )
    }

    private fun record(violation: Throwable) {
        // recordDebug dosya yazımını kendi CrashReportLogWriter iş parçacığına devreder;
        // ihlalin tespit edildiği ana iş parçacığı ek G/Ç yapmaz.
        store?.recordDebug(
            "MAIN_THREAD_VIOLATION | ${violation.javaClass.name}\n" +
                violation.stackTrace.take(24).joinToString("\n") { "  at $it" },
        )
    }
}
