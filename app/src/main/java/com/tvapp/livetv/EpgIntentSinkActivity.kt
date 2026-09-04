package com.tvapp.livetv

import android.app.Activity
import android.os.Bundle
import com.tvapp.livetv.diagnostics.CrashReportStore

/** Absorbs vendor TIF channel-view intents without starting or retuning MainActivity. */
class EpgIntentSinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReportStore(this).recordDebug(
            "EPG_VIEW_INTENT | action=${intent?.action}, type=${intent?.type}, data=${intent?.data}",
        )
        finish()
        overridePendingTransition(0, 0)
    }
}
