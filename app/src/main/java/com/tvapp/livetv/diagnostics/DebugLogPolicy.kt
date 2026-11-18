package com.tvapp.livetv.diagnostics

/** Bir gunluk satirinin hangi yolla yazildigini ayiran siniflandirma (DEBUGLOG-001). */
internal enum class DiagnosticEntryKind {
    /** Siradag olay/izleme satiri: ayiklama gunlugu kapaliysa yazilmaz. */
    EVENT,

    /** Istisna veya cokme kaydi: tercih ne olursa olsun yazilir. */
    EXCEPTION,
}

/**
 * Ayiklama gunlugunun yazim kurali (DEBUGLOG-001).
 *
 * Kullanici ayarlardan ayiklama gunlugunu kapatabilir; ancak uygulama hicbir zaman korumasiz
 * kalmaz. Kullanici gereksinimi dogrudan soyle: "acilistaki exceptionlar icin her zaman
 * yakalamasi gerekli". Bu yuzden istisna yolu tercihten bagimsizdir ve bu kural tek noktada
 * toplanir ki bir daha yanlis bir gatede istisna kaybi yasamasin (TASKS.md CRASH-001..004
 * dogrulama zinciri bu kayitlara dayanir).
 */
internal object DebugLogPolicy {

    fun writesDebugLine(debugLogEnabled: Boolean, kind: DiagnosticEntryKind): Boolean =
        kind == DiagnosticEntryKind.EXCEPTION || debugLogEnabled
}
