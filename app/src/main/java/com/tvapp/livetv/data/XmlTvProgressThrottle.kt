package com.tvapp.livetv.data

import android.os.SystemClock

/** Büyük XMLTV içe aktarmalarında UI progress geri çağrılarını sınırlar.
 *
 *  100k+ programlı katalogta her program için bir geri çağrı, iş parçacığı
 *  üzerinde on binlerce post üretip ayrıştırmayı yavaşlatır; sayaçsal olarak
 *  anlamlı değişimler en fazla [XMLTV_PROGRESS_MIN_INTERVAL_MS] aralıklarla iletilir.
 *  Aşama değişimleri (okunuyor → kaydediliyor) zamanlayıcıya takılmadan iletilir. */
internal class XmlTvProgressThrottle(
    private val onProgress: ((XmlTvImportProgress) -> Unit)?,
    private val minIntervalMillis: Long = XMLTV_PROGRESS_MIN_INTERVAL_MS,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    var count = 0
        private set

    private var lastReportedCount = -1
    private var lastReportedPhase: XmlTvImportPhase? = null
    private var lastReportedAtMillis = Long.MIN_VALUE

    fun bump() {
        count++
    }

    fun report(phase: XmlTvImportPhase, force: Boolean = false) {
        val callback = onProgress ?: return
        val now = clock()
        val countChanged = count != lastReportedCount
        val phaseChanged = phase != lastReportedPhase
        if (!force && !countChanged && !phaseChanged) return
        if (!force && !phaseChanged && countChanged &&
            now - lastReportedAtMillis < minIntervalMillis
        ) {
            return
        }
        lastReportedCount = count
        lastReportedPhase = phase
        lastReportedAtMillis = now
        callback(XmlTvImportProgress(phase, count))
    }
}

internal const val XMLTV_PROGRESS_MIN_INTERVAL_MS = 120L
